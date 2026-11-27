import { Container } from "@cloudflare/containers";
import { Hono } from "hono";
import { serve } from "./edge-cache.js";

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

// Public pages come from the edge cache (src/edge-cache.js); everything else, and every
// cache miss, is forwarded to the container (singleton pattern). A deployed /static/* never
// gets here: Workers Static Assets serves it first ("assets" in wrangler.jsonc).
app.all("*", (c) =>
  serve(c.req.raw, {
    cache: caches.default,
    version: c.env.CF_VERSION_METADATA?.id ?? "dev",
    waitUntil: (promise) => c.executionCtx.waitUntil(promise),
    origin: async (request) => {
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

      return await container.fetch(request);
    },
  }),
);

export default app;
