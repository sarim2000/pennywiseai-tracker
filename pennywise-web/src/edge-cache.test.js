// Run: npm test  (plain node --test, no dependencies)
import test from "node:test";
import assert from "node:assert/strict";
import { cacheKey, storable, serve, FRESH_MS } from "./edge-cache.js";

const BASE = "https://pennywise.zynth.dev";
const key = (path, method = "GET", v = "v1") => cacheKey(new Request(BASE + path, { method }), v);

test("public pages are cached", () => {
  for (const p of ["/", "/banks", "/banks/hdfc-bank", "/banks/country/india", "/guide", "/guide/budgets",
    "/pro", "/terms", "/tools/parse", "/sitemap.xml", "/robots.txt"]) {
    assert.ok(key(p), p);
    assert.ok(key(p, "HEAD"), `HEAD ${p}`);
  }
});

test("live, API, POST and other methods bypass", () => {
  for (const p of ["/feedback", "/roadmap", "/api/feedback", "/api/report", "/htmx/parse", "/htmx/feedback-list",
    "/htmx/vote/1", "/static/owl.svg", "/bankster", "/proton", "/guidebook"]) {
    assert.equal(key(p), null, p);
  }
  for (const m of ["POST", "PUT", "DELETE", "OPTIONS"]) assert.equal(key("/tools/parse", m), null, m);
});

test("cache key keeps only what changes the page", () => {
  assert.equal(key("/?utm_source=ig&gclid=x&message=hi&sender=AD-HDFC"), key("/"));
  assert.equal(key("/tools/parse?sender=X&message=Y"), key("/tools/parse"));
  assert.equal(key("/banks?app=1"), key("/banks"));
  assert.equal(key("/guide/budgets?app=1&utm_source=x"), key("/guide/budgets?app=1"));
  assert.notEqual(key("/guide/budgets?app=1"), key("/guide/budgets"));
  assert.equal(key("/guide?app=0"), key("/guide"));
  assert.notEqual(key("/", "GET", "v2"), key("/"), "a deploy starts fresh");
  assert.equal(key("/", "HEAD"), key("/"), "HEAD reads the GET copy");
});

test("only a plain 200 is stored", () => {
  assert.ok(storable(new Response("ok")));
  for (const status of [301, 302, 404, 500, 503]) assert.ok(!storable(new Response(null, { status })), status);
  assert.ok(!storable(new Response("ok", { headers: { "set-cookie": "a=b" } })));
  assert.ok(!storable(new Response("ok", { headers: { "cache-control": "no-store" } })));
  assert.ok(!storable(new Response("ok", { headers: { "cache-control": "private, max-age=0" } })));
});

function harness(originStatus = 200) {
  const store = new Map();
  const calls = [];
  const pending = [];
  const deps = {
    version: "v1",
    cache: {
      match: async (k) => store.get(k)?.clone(),
      put: async (k, res) => { store.set(k, new Response(await res.text(), res)); },
    },
    origin: async (req) => { calls.push(`${req.method} ${new URL(req.url).pathname}`); return new Response(`page ${calls.length}`, { status: originStatus }); },
    waitUntil: (p) => pending.push(p),
  };
  const run = async (path, method = "GET", now = 0) => {
    const res = await serve(new Request(BASE + path, { method }), { ...deps, now });
    await Promise.all(pending.splice(0));
    return res;
  };
  return { run, calls, store };
}

test("miss, hit, stale refresh", async () => {
  const { run, calls } = harness();
  let res = await run("/banks");
  assert.equal(res.headers.get("x-edge-cache"), "MISS");
  assert.equal(res.headers.get("cache-control"), "public, max-age=0, must-revalidate");
  assert.equal(await res.text(), "page 1");

  res = await run("/banks?utm_source=x", "GET", FRESH_MS);
  assert.equal(res.headers.get("x-edge-cache"), "HIT");
  assert.equal(res.headers.get("x-stored-at"), null);
  assert.equal(await res.text(), "page 1");
  assert.equal(calls.length, 1, "a hit never wakes the container");

  res = await run("/banks", "GET", FRESH_MS + 1);
  assert.equal(res.headers.get("x-edge-cache"), "STALE");
  assert.equal(await res.text(), "page 1", "stale copy answers at once");
  assert.equal(calls.length, 2, "…and is refreshed in the background");
  assert.equal(await (await run("/banks", "GET", FRESH_MS + 2)).text(), "page 2");
});

test("HEAD is served from the GET copy", async () => {
  const { run, calls } = harness();
  const miss = await run("/pro", "HEAD");
  assert.equal(calls[0], "GET /pro");
  assert.equal(miss.body, null);
  const hit = await run("/pro", "HEAD");
  assert.equal(hit.headers.get("x-edge-cache"), "HIT");
  assert.equal(hit.body, null);
});

test("bypassed and non-200 responses are passed through and never stored", async () => {
  const { run, calls, store } = harness();
  const post = await run("/htmx/parse", "POST");
  assert.equal(post.headers.get("x-edge-cache"), null);
  await run("/feedback");
  await run("/feedback");
  assert.deepEqual(calls, ["POST /htmx/parse", "GET /feedback", "GET /feedback"]);
  assert.equal(store.size, 0);

  const missing = harness(404);
  await missing.run("/banks/nope");
  await missing.run("/banks/nope");
  assert.equal(missing.calls.length, 2);
  assert.equal(missing.store.size, 0);
});
