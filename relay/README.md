# PennyWise AI relay

Cloudflare Worker that lets Claude, ChatGPT and other MCP apps ask a user's phone about their PennyWise data (ADR 0002). It passes tool calls to the phone over the phone's WebSocket and stores or logs no money data.

- `npm test` — end-to-end tests with a fake phone (OAuth sign-in, pairing, tool calls, revoke).
- Deploy: `npx wrangler kv namespace create OAUTH_KV`, put the id in `wrangler.jsonc`, then `npm run deploy`.

Phone API (all but registration need `Authorization: Device <deviceId>.<secret>`):

| Route | Purpose |
|---|---|
| `POST /device` | Register a phone → `{deviceId, secret}` |
| `POST /device/pair` | New 6-digit pairing code, valid 5 minutes |
| `GET /device/connect` | WebSocket for an AI session. Receives `{type:"call",id,name,arguments}`, answers `{type:"result",id,result}`; `ping` → `pong` |
| `GET /device/connections` · `DELETE /device/connections/:id` | List or revoke AI connections |
| `DELETE /device` | Reset: revoke every connection and forget the phone |
