// Run: npm test (node --test, no dependencies).
// Node has no HTMLRewriter, so a stub stands in: it runs the <head> handler
// and puts what was appended just before </head>. Real rewriting was checked
// once under `wrangler dev`; these tests guard the gating and the escaping.
import { test } from "node:test";
import assert from "node:assert/strict";
import { withBeacon } from "./beacon.js";

globalThis.HTMLRewriter = class {
  on(selector, handler) {
    assert.equal(selector, "head");
    this.handler = handler;
    return this;
  }
  transform(response) {
    let added = "";
    this.handler.element({
      append: (content, opts) => {
        assert.deepEqual(opts, { html: true });
        added += content;
      },
    });
    const body = response.text().then((html) => html.replace("</head>", `${added}</head>`));
    return { text: () => body };
  }
};

const PAGE = "<html><head><title>t</title></head><body></body></html>";
const res = (type, body = PAGE) => new Response(body, { headers: { "content-type": type } });

test("appends the beacon to <head> of HTML", async () => {
  const html = await withBeacon(res("text/html; charset=UTF-8"), "abc123").text();
  assert.equal(
    html,
    `<html><head><title>t</title><script defer src="https://static.cloudflareinsights.com/beacon.min.js" data-cf-beacon='{"token":"abc123"}'></script></head><body></body></html>`,
  );
});

test("matches the content type case-insensitively", async () => {
  assert.match(await withBeacon(res("TEXT/HTML"), "abc123").text(), /beacon\.min\.js/);
});

test("skips injection without a token", () => {
  for (const token of ["", undefined]) {
    const r = res("text/html");
    assert.equal(withBeacon(r, token), r);
  }
});

test("leaves non-HTML responses untouched", () => {
  for (const type of ["application/json", "text/css", "application/javascript"]) {
    const r = res(type, "{}");
    assert.equal(withBeacon(r, "abc123"), r);
  }
  const noType = new Response("x");
  noType.headers.delete("content-type");
  assert.equal(withBeacon(noType, "abc123"), noType);
});

test("escapes the token for the single-quoted attribute", async () => {
  const html = await withBeacon(res("text/html"), `a'b"c&d`).text();
  assert.match(html, /data-cf-beacon='\{"token":"a&#39;b\\"c&amp;d"\}'/);
});
