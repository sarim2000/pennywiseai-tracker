# Privacy Policy

**Last Updated: November 2026**

## Our Commitment to Privacy

PennyWise AI is built with privacy as the core principle. We believe your financial data should remain yours alone.

## On-Device Processing

**Transaction processing and AI run locally on your device.** AI features use an on-device LLM (Qwen 2.5, via Google AI Edge LiteRT-LM). The only data that ever leaves your device goes through the explicit outbound requests listed under Internet Permission below:

- ✅ **On-device AI** - Parsing and AI processing run locally; your transaction database never uploads anywhere
- ✅ **No data collection** - No analytics, telemetry, or accounts; the exceptions are the parse-report flow and share links you choose to create (see Internet Permission)
- ✅ **No tracking** - No analytics, no telemetry, no user tracking
- ✅ **No ads** - No advertising networks or tracking pixels
- ✅ **Offline AI** - Once downloaded, AI works completely offline

## Data Storage

### What We Store (Locally Only)
- Transaction details extracted from SMS (amount, merchant, date, category)
- Your custom categories and notes
- App preferences and settings

### Where It's Stored
- All data is stored in a local SQLite database on your device
- Database is protected by Android's app sandboxing
- Data is only accessible to PennyWise AI app

### Data Deletion
- Uninstalling the app completely removes all data
- You can delete individual transactions at any time
- Export your data before uninstalling if you want to keep records

## Permissions

### SMS Permissions (Read-Only)
- **`READ_SMS` / `RECEIVE_SMS`**: to read bank transaction SMS as they arrive
- **Scope**: Read-only access, we cannot send or modify messages
- **Processing**: SMS parsing happens entirely on-device
- **Storage**: Only extracted transaction fields are stored, not full messages

### Notifications (`POST_NOTIFICATIONS`)
- Subscription due-date reminders and budget alerts

### Biometrics (`USE_BIOMETRIC`)
- Optional app lock via fingerprint or face unlock

### Contacts (`READ_CONTACTS`, off by default)
- PennyWise ships this permission for one opt-in feature: contact lookup, which can resolve a UPI VPA like `merchant@ybl` into a name from your address book so merchant lists read naturally
- The lookup runs on-device and the feature stays dormant until you enable it in Settings

### Microphone (`RECORD_AUDIO`, asked on your first mic tap)
- Used only while you dictate a chat message. The words fill the message box; nothing is sent until you tap Send
- PennyWise never records or stores audio. Speech is turned into text by your phone's own speech recognition service
- By default that service runs **on your phone**. Languages it can recognize offline can be downloaded from the voice language menu (the download comes from your phone's speech service, not from PennyWise)
- A phone language that can't be recognized offline (for example Arabic on Google's recognizer today) is offered as **online** voice, marked with a cloud. Picking one asks first and names the service. Once you agree, voice in any such online language goes to that service over the internet; languages recognized on your phone stay offline. Switching your phone's default speech service asks again

### Internet Permission
- **Model Download**: One-time download of the ~1.5GB Qwen 2.5 model from a public Cloudflare R2 bucket, verified against a SHA-256 checksum before use
- **Exchange Rates**: Multi-currency features fetch current rates from open.er-api.com; only the currency code is sent
- **Parse Reports**: The "report a parsing problem" action opens pennywise.zynth.dev with the SMS text and sender ID pre-filled. The link also carries an encrypted device identifier — your Android device ID plus a timestamp, RSA-encrypted on-device before it leaves. Opening the page sends these details to the server right away to generate a parsed preview; submitting the report itself remains a separate user action
- **AI Access (optional, off by default)**: Only while you have started an AI session, answers to a connected AI app's questions travel from your phone through PennyWise's relay (see "Connected AI Apps" below)
- **Share links (optional)**: Only when you tap Share as link and then Upload and share, the card you just previewed goes to pennywise.zynth.dev (see "Share Cards and Links" below)
- **Online voice (optional)**: Only for voice languages marked online, and only after you agreed for that speech service, your speech goes to your phone's default speech service (see Microphone above). PennyWise itself receives nothing
- **App Updates**: Google Play Store variant uses Play Services for app updates (F-Droid variant does not)
- **After Model Download**: AI works completely offline, no internet required for core features

No location or camera permission is requested.

## Third-Party Services

PennyWise AI does **NOT** use:
- ❌ Cloud storage, backup, or sync of your transaction data
- ❌ Analytics services (Google Analytics, Firebase, etc.)
- ❌ Crash reporting services
- ❌ Advertising networks
- ❌ Social media SDKs
- ❌ Payment processors

**Note**: The Google Play Store variant includes Play Services for app updates only. The F-Droid variant has no Google services.

## AI Features

### On-Device AI Assistant
- Uses the Qwen 2.5 model (1.5GB download) via Google AI Edge LiteRT-LM
- Model runs entirely on your device; inference needs no network
- After initial download, no internet connection required
- Chat history is saved locally in the app's database so conversations survive restarts; it's never transmitted
- AI insights are generated locally from your local transaction data
- Model file stored in app's private storage

### Connected AI Apps (optional, Pro)
You can connect Claude, ChatGPT or another app that supports MCP so it can answer questions about your spending. It is off until you set it up, and it only works while you have started a timed AI session on your phone.

- **Your data stays on your phone.** The AI app sends questions to PennyWise's relay (a Cloudflare Worker at ai.pennywise.zynth.dev), which passes them to your phone over a connection your phone opened. Your phone answers from its local database and the answer goes back the same way. The relay never stores or logs questions or answers; its code is in this repository under `relay/`.
- **What the relay stores**: a random ID for your phone, a hash of its secret, and your connected AI apps (their names and hashed sign-in tokens). Nothing about your money.
- **What an AI app can read**: transactions (date, merchant or payee name, category, type, amount and currency, bank with the last 4 digits of the account), account balances and credit card dues, and active subscriptions. It can search your whole history, across all profiles, up to 200 transactions per request. Payee names can include people's names for transfers.
- **What it never gets**: SMS text, full account numbers, UPI or reference numbers, notes, receipts, budgets, goals, loans or your settings. It cannot add, change or delete anything.
- **The AI provider** (for example Anthropic or OpenAI) receives the answers it reads, under its own privacy policy.
- **Control**: stop the session any time from the notification, disconnect one app or all of them in Settings → Connect Claude / ChatGPT. Disconnecting all also deletes your phone's ID from the relay.

## Share Cards and Links

The Share screen makes a card from your figures. You choose exactly what it shows (amounts and percentages are separate switches) and see it before anything happens.

- **Share image** draws the picture on your phone and hands it to the app you pick. Nothing is uploaded.
- **Share as link** uploads a copy, and only after you tap Upload and share on a screen that shows the image being uploaded. The copy is the card's text exactly as previewed (already formatted on your phone), plus that 1200×630 image for chat link previews. Nothing else from the app goes with it: no transactions, account details or settings.
- **Who can see it**: anyone who has the link. Links are unlisted: the page asks search engines not to index it, and there is no public list of shares.
- **How long it stays**: 7, 30 or 90 days, or until you delete it, as you chose. When it expires or you delete it, the server erases the card's text and image. To limit how many links one connection can make, the server stores a salted hash of the uploading IP address with each link, never the address itself.
- **Deleting**: Share → Shared links lists the links this phone made, with Delete. The delete keys stay on this phone, outside PennyWise backups, so after clearing the app's data or uninstalling it those links stay up until they expire, and a link set to never expire stays up for good. Anyone you sent the link to may have saved the image already.

## Data Export

When you export your data:
- CSV/PDF files are created locally on your device
- You control where to share or save them
- No automatic uploads or backups

## Open Source Transparency

PennyWise AI is fully open source:
- Review our code at [GitHub](https://github.com/sarim2000/pennywiseai-tracker)
- Verify our privacy claims yourself
- Contribute to make it even better

## Children's Privacy

PennyWise AI is not directed at children under 13. We do not knowingly collect information from children.

## Changes to Privacy Policy

Any changes to this privacy policy will be:
- Updated in the app repository
- Reflected in the "Last Updated" date
- Communicated through release notes

## Contact

For privacy concerns or questions:
- Open an issue on [GitHub](https://github.com/sarim2000/pennywiseai-tracker/issues)
- Join our [Discord community](https://discord.gg/H3xWeMWjKQ)

## Summary

**Your financial data lives on your phone.** Transactions, balances, budgets, and chat history all sit in a local database inside the app sandbox, and uninstalling removes everything. PennyWise has no analytics or cloud-sync backend; the outbound requests listed under Internet Permission are the complete list.

---

*PennyWise AI - Privacy-first expense tracking with on-device AI*