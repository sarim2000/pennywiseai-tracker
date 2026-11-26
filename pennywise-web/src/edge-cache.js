// Edge cache for the public pages. They only change on deploy, so a copy kept at the
// visitor's Cloudflare data center answers in milliseconds instead of a round trip to
// the container (and hides the container's cold start once a page has been seen).
//
// Pure module (no Workers imports) so `node --test` can run edge-cache.test.js.

// Public pages that render the same for everyone. /feedback, /roadmap (live DB, set a
// cookie), /api/*, /htmx/* and every POST never match, so they always reach the container.
const PAGES = /^\/(banks(\/.*)?|guide(\/.*)?|pro|terms|tools\/parse|sitemap\.xml|robots\.txt)?$/;

export const FRESH_MS = 5 * 60 * 1000; // older copies are still served, then refreshed in the background
const KEEP_SECONDS = 24 * 60 * 60; // how long a data center keeps a copy at all
const BROWSER = "public, max-age=0, must-revalidate";

/** The cache key for a cacheable request, or null to send it straight to the container. */
export function cacheKey(request, version) {
  if (request.method !== "GET" && request.method !== "HEAD") return null;
  const url = new URL(request.url);
  if (!PAGES.test(url.pathname)) return null;
  const key = new URL(url.pathname, url.origin);
  // The server ignores every query param on these pages except the guide's ?app=1 (the
  // in-app view), so utm/gclid/?message=… all share one copy and never land in a key.
  // A new param the server reads on these routes must be added here.
  if (url.pathname.startsWith("/guide") && url.searchParams.get("app") === "1") key.searchParams.set("app", "1");
  // Each deploy is a new Worker version, so it starts on a fresh set of copies.
  key.searchParams.set("v", version);
  return key.toString();
}

/** Only a plain 200 that sets no cookie and doesn't opt out may be stored. */
export function storable(response) {
  return response.status === 200 &&
    !response.headers.has("set-cookie") &&
    !/no-store|private/i.test(response.headers.get("cache-control") ?? "");
}

// Keys being refreshed in this isolate, so a burst of stale hits wakes the container once.
const refreshing = new Set();

export async function serve(request, { cache, origin, version, waitUntil, now = Date.now() }) {
  const key = cacheKey(request, version);
  if (!key) return origin(request);

  const hit = await cache.match(key);
  if (hit) {
    const stale = now - Number(hit.headers.get("x-stored-at")) > FRESH_MS;
    if (stale && !refreshing.has(key)) {
      refreshing.add(key);
      const refresh = origin(new Request(key)).then((res) => storable(res) && store(cache, key, res, now));
      waitUntil(refresh.finally(() => refreshing.delete(key)));
    }
    return toBrowser(hit, request, stale ? "STALE" : "HIT");
  }

  // The container always gets the key URL itself (a plain GET, no client headers), here and
  // in the refresh above, so a stored copy is exactly what its key names: a query the server
  // reads differently from URLSearchParams (`?=app=0&app=1`) can't seed the wrong copy, and
  // HEAD gets the GET page. The server ignores the extra `v` param.
  const res = await origin(new Request(key));
  if (!storable(res)) return res;
  waitUntil(store(cache, key, res.clone(), now));
  return toBrowser(res, request, "MISS");
}

function store(cache, key, res, now) {
  const copy = new Response(res.body, res);
  copy.headers.set("cache-control", `public, max-age=${KEEP_SECONDS}`); // read by cache.put only
  copy.headers.set("x-stored-at", String(now));
  return cache.put(key, copy);
}

function toBrowser(res, request, state) {
  const out = new Response(request.method === "HEAD" ? null : res.body, res);
  out.headers.set("cache-control", BROWSER);
  out.headers.set("x-edge-cache", state);
  out.headers.delete("x-stored-at");
  return out;
}
