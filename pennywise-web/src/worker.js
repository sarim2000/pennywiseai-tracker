import { Container } from "@cloudflare/containers";
import { Hono } from "hono";

export class PennyWiseContainer extends Container {
  // Port the container listens on (default: 8080)
  defaultPort = 8080;
  // Time before container sleeps due to inactivity (default: 30s)
  sleepAfter = "15m";

  // Optional lifecycle hooks
  onStart() {
    console.log("PennyWise container successfully started");
  }

  onStop() {
    console.log("PennyWise container successfully shut down");
  }

  onError(error) {
    console.log("PennyWise container error:", error);
  }
}

// Create Hono app
const app = new Hono();

// Start (or reuse) the container and hand it the request (singleton pattern).
async function forwardToContainer(c) {
  // Get container instance by name
  const container = c.env.PENNYWISE_CONTAINER.getByName("main");

  // Start container with environment variables (secrets from Worker Secrets)
  await container.startAndWaitForPorts({
    startOptions: {
      envVars: {
        KTOR_ENV: "production",
        DATABASE_URL: c.env.DATABASE_URL || "",
        DATABASE_USER: c.env.DATABASE_USER || "postgres",
        DATABASE_PASSWORD: c.env.DATABASE_PASSWORD || "",
      },
    },
  });

  return await container.fetch(c.req.raw);
}

// --- Shared links (/s/{id}) -------------------------------------------------
// WhatsApp, iMessage and friends fetch the page and its og.png the moment a link
// is pasted and give up after a few seconds, so a cold container would cost the
// preview. Successful responses stay in the edge cache for as long as the
// origin's Cache-Control max-age allows: five minutes for both, cut to the time
// the share has left, so a cached copy never outlives the share.
const SHARE_PATH = /^\/s\/([A-Za-z0-9_-]{22})(\/og\.png)?$/;

function shareCacheKeys(origin, id) {
  return [new Request(`${origin}/s/${id}`), new Request(`${origin}/s/${id}/og.png`)];
}

// Best-effort: the Cache API is per data centre, so this clears only the colo that
// served the DELETE/report. Elsewhere a copy lives until its max-age runs out.
function purgeShare(c, id) {
  const origin = new URL(c.req.url).origin;
  c.executionCtx.waitUntil(
    Promise.all(shareCacheKeys(origin, id).map((key) => caches.default.delete(key))).catch(() => {}),
  );
}

app.get("/s/*", async (c) => {
  const url = new URL(c.req.url);
  if (!SHARE_PATH.test(url.pathname)) return forwardToContainer(c);
  // Keyed on the path alone so a query string can't be used to bypass the cache.
  const key = new Request(url.origin + url.pathname);
  const cached = await caches.default.match(key);
  if (cached) return cached;
  const response = await forwardToContainer(c);
  if (response.status === 200) {
    c.executionCtx.waitUntil(caches.default.put(key, response.clone()).catch(() => {}));
  }
  return response;
});

app.delete("/api/shares/:id", async (c) => {
  const response = await forwardToContainer(c);
  if (response.status === 204) purgeShare(c, c.req.param("id"));
  return response;
});

// A report can be the one that hides the share, so drop the cached copies too.
app.post("/s/:id/report", async (c) => {
  const response = await forwardToContainer(c);
  if (response.status === 204) purgeShare(c, c.req.param("id"));
  return response;
});

// Forward everything else to the container
app.all("*", forwardToContainer);

export default app;
