# Privacy Policy

**Last Updated: August 2026**

## Our Commitment to Privacy

PennyWise AI is built with privacy as the core principle. We believe your financial data should remain yours alone.

## On-Device Processing

**Transaction processing and AI run locally on your device.** AI features use an on-device LLM (Qwen 2.5, via Google AI Edge LiteRT-LM). The only data that ever leaves your device goes through the explicit outbound requests listed under Internet Permission below:

- ✅ **On-device AI** - Parsing and AI processing run locally; your transaction database never uploads anywhere
- ✅ **No data collection** - No analytics, telemetry, or accounts; the one exception is the parse-report flow (see Internet Permission)
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

### Internet Permission
- **Model Download**: One-time download of the ~1.5GB Qwen 2.5 model from a public Cloudflare R2 bucket, verified against a SHA-256 checksum before use
- **Exchange Rates**: Multi-currency features fetch current rates from open.er-api.com; only the currency code is sent
- **Parse Reports**: The "report a parsing problem" action opens pennywise.zynth.dev with the SMS text and sender ID pre-filled. The link also carries an encrypted device identifier — your Android device ID plus a timestamp, RSA-encrypted on-device before it leaves. Opening the page sends these details to the server right away to generate a parsed preview; submitting the report itself remains a separate user action
- **App Updates**: Google Play Store variant uses Play Services for app updates (F-Droid variant does not)
- **After Model Download**: AI works completely offline, no internet required for core features

No location, camera, or microphone permission is requested.

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