# PennyWise AI relay

Cloudflare Worker that lets Claude, ChatGPT and other MCP apps — and the big-screen dashboard at `/dashboard/` — ask a user's phone about their PennyWise data (ADR 0002). It passes tool calls to the phone over the phone's WebSocket and stores or logs no money data. What it does store: a random ID per phone, a hash of the phone's secret, and OAuth grants (hashed tokens, the AI app's name).

- `npm test` — builds the dashboard, then runs end-to-end tests with a fake phone (sign-in, pairing, tool calls, revoke, dashboard headers).
- `npm run deploy` — builds the dashboard and deploys the Worker.
- Local run: the `relay-local` launch config (`wrangler dev` on :8787), then `node scripts/fake-phone.mjs http://localhost:8787` for sample data and a pairing code.

## Self-hosting

Anyone can run their own relay so nothing passes through PennyWise's. You need a Cloudflare account (the free plan is enough) and Node 22.12+.

1. `cd relay && npm ci && npm --prefix dashboard ci`
2. `npx wrangler login`
3. `npx wrangler kv namespace create OAUTH_KV` and put the printed id in `wrangler.jsonc` under `kv_namespaces`.
4. In `wrangler.jsonc`, replace the `routes` entry with your own domain (or delete it to use `*.workers.dev`), and set `vars.BASE_URL` to that same `https://` address.
5. The rate-limit `namespace_id`s must be unique in your account; change them if they clash.
6. `npm run deploy`

Then in PennyWise: Settings → Connect Claude / ChatGPT → **Use your own relay**, enter your address, and connect your AI apps again. They use `https://<your-relay>/mcp`; the dashboard is at `https://<your-relay>/dashboard/`.

## Phone API

All but registration need `Authorization: Device <deviceId>.<secret>`.

| Route | Purpose |
|---|---|
| `POST /device` | Register a phone → `{deviceId, secret}` |
| `POST /device/pair` | New 6-digit pairing code, valid 5 minutes |
| `GET /device/connect` | WebSocket for an AI session. Receives `{type:"call",id,name,arguments}`, answers `{type:"result",id,result}`; `ping` → `pong` |
| `GET /device/connections` · `DELETE /device/connections/:id` | List or revoke AI connections |
| `DELETE /device` | Reset: revoke every connection and forget the phone |
