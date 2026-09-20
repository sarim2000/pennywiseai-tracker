/**
 * PennyWise "Move Pro to this device" endpoint.
 *
 * Dodo's activate/validate/deactivate endpoints are public, but deactivating
 * needs the *instance id*, which a new phone doesn't have. This Worker holds
 * the Dodo API key, looks the key up, checks that the caller knows the
 * purchaser's email (so a leaked key string alone can't evict the owner),
 * and frees every activation so the app can activate again.
 *
 * Deploy:  wrangler deploy   (after `wrangler secret put DODO_API_KEY`)
 * Env:     DODO_API_KEY (secret), DODO_BASE (optional, default live)
 * Request: POST { "license_key": "…", "email": "…" }
 * Reply:   200 { "deactivated": n } | 403 (email mismatch) | 404 (unknown key)
 */
export default {
  async fetch(request, env) {
    if (request.method !== "POST") return new Response("POST only", { status: 405 });
    let key, email;
    try {
      const body = await request.json();
      key = body.license_key?.trim();
      email = body.email?.trim().toLowerCase();
    } catch { /* fallthrough */ }
    if (!key || !email) return json({ error: "license_key and email required" }, 400);

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
    // Same reply for unknown key and wrong email: don't confirm which keys exist.
    if (!record) return json({ error: "not found" }, 404);

    const cust = await fetch(`${base}/customers/${record.customer_id}`, { headers: auth });
    if (!cust.ok) return json({ error: `dodo ${cust.status}` }, 502);
    const ownerEmail = ((await cust.json()).email || "").toLowerCase();
    if (!ownerEmail || ownerEmail !== email) return json({ error: "not found" }, 404);

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
