---
status: accepted
---

# AI access through a Cloudflare Worker relay that stores nothing

Supersedes ADR-0001. Claude and ChatGPT reach the phone through a Cloudflare Worker and Durable Object on `pennywise.zynth.dev`: the phone keeps an outbound WebSocket open during a user-started AI session, the Worker passes each tool call to it and returns the answer, and the Worker never stores or logs either. We dropped ADR-0001's relay that cannot read the traffic because no Cloudflare service on our plan can forward TLS without terminating it (Spectrum TCP is Enterprise-only) and we chose not to pay for and run a separate server; the AI provider sees the answers in either design, so the extra guarantee was small next to that cost.

## Consequences

- The privacy claim is "we never store or log your data", not "we cannot see it". The relay code is open source so anyone can check.
- OAuth sign-in runs on the Worker. It stores AI connections (hashed tokens mapped to a phone) and nothing about the user's money; sign-in and token refresh work even when the phone is offline.
- When no AI session is running, the AI app gets a plain-language tool result telling the user to start one, instead of a connection error.
- The phone no longer needs a TLS server, a certificate or ACME. It keeps the MCP tools, the outbound line and the AI session timer.
