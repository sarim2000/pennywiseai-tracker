// Cloudflare Web Analytics: cookieless page views, no personal data.
// Injected here so every Kotlin-rendered page gets it without touching the views.

/** Returns `response` with the beacon appended to <head>, or unchanged when
 *  there's no token or the body isn't HTML. */
export function withBeacon(response, token) {
  if (!token) return response;
  const type = response.headers.get("content-type") || "";
  if (!type.toLowerCase().startsWith("text/html")) return response;

  // JSON-encode, then escape for a single-quoted HTML attribute.
  const config = JSON.stringify({ token })
    .replace(/&/g, "&amp;")
    .replace(/'/g, "&#39;");
  const tag = `<script defer src="https://static.cloudflareinsights.com/beacon.min.js" data-cf-beacon='${config}'></script>`;

  return new HTMLRewriter()
    .on("head", { element: (head) => head.append(tag, { html: true }) })
    .transform(response);
}
