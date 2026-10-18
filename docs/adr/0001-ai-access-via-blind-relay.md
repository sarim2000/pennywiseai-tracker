---
status: accepted
---

# AI access through a relay that cannot read the traffic

Claude and ChatGPT (web and mobile) can only reach MCP servers at a public HTTPS address, and PennyWise keeps all data on the phone with no backend. We decided the phone itself runs the MCP and OAuth server, holds its own TLS certificate for a per-install phone address, and keeps an outbound line open to a relay that routes by hostname and forwards encrypted bytes it cannot decrypt. Access exists only during a user-started, timed AI session. We chose this over storing a snapshot in our cloud, a plain HTTP relay that sees questions and answers in transit, and a desktop-only local server, because it is the only option that works from the Claude and ChatGPT phone apps while keeping the promise that we never see the user's data.

## Consequences

- The relay cannot show a friendly message when no AI session is running; AI apps see a plain connection error.
- First sign-in and token refresh need an AI session, because the OAuth server is on the phone.
- The relay is a small TCP/SNI proxy on its own server (not a Cloudflare Worker), on a dedicated domain listed on the Public Suffix List so per-phone certificates stay within Let's Encrypt limits.
- Because we control that domain's DNS we could in principle obtain a certificate for a user's address; this is disclosed, and the domain's Certificate Transparency entries are published for anyone to check.
- AI connections, the certificate key and the device credential are never included in backups; a restored phone pairs again.
- Android only: iOS cannot keep the outbound line open in the background.
