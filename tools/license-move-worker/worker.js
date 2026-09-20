/**
 * PennyWise "Move Pro to this device" endpoint.
 *
 * Dodo's activate/validate/deactivate endpoints are public, but deactivating
 * needs the *instance id*, which a new phone doesn't have. This Worker holds
 * the Dodo API key, looks the key up, and frees every activation on it so the
 * app can activate again. Anyone who knows a key string can call this — that
 * only lets a key hop between devices, never run on two at once.
 *
 * Deploy:  wrangler deploy   (after `wrangler secret put DODO_API_KEY`)
 * Env:     DODO_API_KEY (secret), DODO_BASE (optional, default live)
 * Request: POST { "license_key": "…" }  →  200 { "deactivated": n } | 404
 */
export default {
  async fetch(request, env) {
    if (request.method !== "POST") return new Response("POST only", { status: 405 });
    let key;
    try { key = (await request.json()).license_key?.trim(); } catch { /* fallthrough */ }
    if (!key) return json({ error: "license_key required" }, 400);

    const base = env.DODO_BASE || "https://live.dodopayments.com";
    const auth = { Authorization: `Bearer ${env.DODO_API_KEY}` };

    // ponytail: linear scan of all keys — fine at indie volume, switch to
    // GET /customers/{id}/grants?integration_type=license_key when it isn't.
    let record = null;
    for (let page = 0; page < 50 && !record; page++) {
      const res = await fetch(`${base}/license_keys?page_size=100&page_number=${page}`, { headers: auth });
      if (!res.ok) return json({ error: `dodo ${res.status}` }, 502);
      const items = (await res.json()).items || [];
      record = items.find((k) => k.key === key) || null;
      if (items.length < 100) break;
    }
    if (!record) return json({ error: "not found" }, 404);

    const inst = await fetch(`${base}/license_key_instances?license_key_id=${record.id}&page_size=100`, { headers: auth });
    if (!inst.ok) return json({ error: `dodo ${inst.status}` }, 502);
    const instances = (await inst.json()).items || [];

    let deactivated = 0;
    for (const i of instances) {
      const r = await fetch(`${base}/licenses/deactivate`, {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ license_key: key, license_key_instance_id: i.id }),
      });
      if (r.ok) deactivated++;
    }
    return json({ deactivated });
  },
};

const json = (body, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });
