# AI access: Claude, ChatGPT and the dashboard

PennyWise Pro can let AI apps — and a big-screen dashboard on a computer — read your data while you allow it. Your data stays on your phone: during an **AI session** the app's questions travel through PennyWise's relay to your phone, your phone answers from its own database, and the answer goes back the same way. The relay never stores or logs them. Design: [ADR 0002](adr/0002-ai-access-via-worker-relay.md).

## Connect Claude or ChatGPT (once)

1. On your phone: **Settings → Connect Claude / ChatGPT**.
2. In Claude (claude.ai → Settings → Connectors → Add custom connector) or ChatGPT (Settings → Connectors), add the URL shown in the app: `https://ai.pennywise.zynth.dev/mcp`.
3. When it asks you to sign in, tap **Get pairing code** in PennyWise and enter the 6-digit code. Codes last 5 minutes and work once.

The connection then shows under **Connected apps**, where you can disconnect it any time. Connectors added on claude.ai also work in the Claude phone and desktop apps.

## Ask questions

1. Start an **AI session** in the app (15, 30 or 60 minutes), or tap the **PennyWise AI** tile in Quick Settings. A notification shows while it's on, with **+15 min** and **Stop**.
2. Ask the AI app anything, for example “What did I spend on food last month?” or “Which subscriptions renew this week?”.

Outside a session the AI app gets a message telling you to start one. Nothing can reach your data then.

## Dashboard on a computer

Open `https://ai.pennywise.zynth.dev/dashboard/` on a computer, click **Connect to my phone**, and enter a pairing code. It shows your spending over time, a category-by-month view, comparisons with the previous period, spending by account and weekday, your full transaction history with filters and CSV download, and your accounts and subscriptions. It needs an AI session on, like any AI app, and appears as “PennyWise dashboard” in your connected apps.

## What AI apps can and can't see

- **Can read:** transactions (date, merchant or payee, category, type, amount and currency, bank and last 4 digits of the account), account balances and card dues, and active subscriptions — across your whole history and all profiles.
- **Never get:** SMS text, full account numbers, UPI or reference numbers, notes, receipts, budgets, goals, loans or settings.
- **Can't change anything.** Every tool is read-only.
- Amounts always come with their currency; totals are never added across currencies.
- The AI app's provider (for example Anthropic or OpenAI) sees the answers it receives, under its own privacy policy.

## Use your own relay

For full control, run the open-source relay on your own Cloudflare account ([relay/README.md](../relay/README.md#self-hosting)), then in the app open **Use your own relay** and enter its `https://` address. Switching relays disconnects every AI app; connect them again with the new address.
