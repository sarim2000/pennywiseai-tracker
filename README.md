<a name="top"></a>
[![PennyWise AI Banner](banner.png)](https://github.com/sarim2000/pennywiseai-tracker)
[![GitHub stars](https://img.shields.io/github/stars/sarim2000/pennywiseai-tracker?style=social)](https://github.com/sarim2000/pennywiseai-tracker)
[![License](https://img.shields.io/badge/license-AGPL%20v3-blue)](LICENSE)
[![Android](https://img.shields.io/badge/Android-12+-3DDC84)](https://developer.android.com/about/versions/12)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF)](https://kotlinlang.org/)
[![Privacy](https://img.shields.io/badge/AI-100%25_On--Device-FF6B6B)](https://developers.google.com/mediapipe)
[![Downloads](https://img.shields.io/badge/Downloads-100+-green)](https://play.google.com/store/apps/details?id=com.pennywiseai.tracker)
[![F-Droid](https://img.shields.io/f-droid/v/com.pennywiseai.tracker?color=1976d2)](https://f-droid.org/packages/com.pennywiseai.tracker/)
[![GitHub release](https://img.shields.io/github/v/release/sarim2000/pennywiseai-tracker)](https://github.com/sarim2000/pennywiseai-tracker/releases)
[![GitHub last commit](https://img.shields.io/github/last-commit/sarim2000/pennywiseai-tracker)](https://github.com/sarim2000/pennywiseai-tracker/commits)
[![Discord](https://img.shields.io/badge/Discord-Join_Community-5865F2)](https://discord.gg/H3xWeMWjKQ)

## PennyWise AI — Free & Open‑Source, private SMS‑powered expense tracker

Turn bank SMS into a clean, searchable money timeline with on-device AI assistance. 100% private, no cloud processing.


⭐ **Star us on GitHub — join 300+ supporters!**

[![Share](https://img.shields.io/badge/share-000000?logo=x&logoColor=white)](https://x.com/intent/tweet?text=Check%20out%20PennyWise%20AI%20-%20Privacy-first%20expense%20tracker%20with%20on-device%20AI:%20https://github.com/sarim2000/pennywiseai-tracker%20%23Android%20%23PrivacyFirst%20%23OnDeviceAI)
[![Share](https://img.shields.io/badge/share-0A66C2?logo=linkedin&logoColor=white)](https://www.linkedin.com/sharing/share-offsite/?url=https://github.com/sarim2000/pennywiseai-tracker)
[![Share](https://img.shields.io/badge/share-FF4500?logo=reddit&logoColor=white)](https://www.reddit.com/submit?title=PennyWise%20AI%20-%20Privacy-first%20expense%20tracker&url=https://github.com/sarim2000/pennywiseai-tracker)
[![Share](https://img.shields.io/badge/share-0088CC?logo=telegram&logoColor=white)](https://t.me/share/url?url=https://github.com/sarim2000/pennywiseai-tracker&text=Check%20out%20PennyWise%20AI)

## Overview

Your bank already texts you every transaction — PennyWise turns those SMS into a private, zero-setup expense tracker with on-device AI. No accounts, no cloud, no effort.

<a href="https://play.google.com/store/apps/details?id=com.pennywiseai.tracker">
  <img src="https://img.shields.io/badge/GET_IT_ON-Google_Play-00875F?style=for-the-badge&logo=google-play&logoColor=white" alt="Get it on Google Play" />
</a>
<a href="https://f-droid.org/packages/com.pennywiseai.tracker">
  <img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" alt="Get it on F-Droid" height="80">
</a>

### How it works

1. **Grant SMS permission** (read-only) — no account creation, no inbox changes, no messages sent.
2. **PennyWise parses your transactions instantly** — extracts amount, merchant, category, and date. New SMS are detected in real-time.
3. **Get insights** — analytics, budgets, subscriptions, and an on-device AI assistant you can ask anything.

## Why PennyWise

**Key Differentiators**

- **🔒 100% On-Device & Private** — All AI processing runs locally, no cloud, no servers, no tracking
- **⚡ Zero Setup** — Just grant SMS permission, no accounts to create, works instantly
- **🆓 Free & Open Source** — AGPL v3 licensed, no ads, no trackers; an optional **PennyWise Pro** upgrade (unlimited rules, PDF statement imports, CSV export, account merge) supports the solo developer

**Core Features**

- **🤖 Smart SMS Parsing** — <!-- BANKS_SUMMARY -->140 banks across 23 countries<!-- /BANKS_SUMMARY --> with real-time detection of incoming SMS
- **🌍 Multi-Currency** — Native support for INR, USD, AED, THB, NPR, ETB, and more with exchange rate management
- **💬 On-Device AI Assistant** — Ask "What did I spend on food?" — powered by Qwen 2.5, runs entirely on your phone
- **🏷️ Auto-Categorization & Smart Rules** — Pattern-based rules that auto-categorize transactions
- **💰 Budget Tracking** — Multiple budget groups (Limit/Target/Expected types), daily allowance, category-level budgets
- **📊 Analytics & Charts** — Bar charts, line trends, heatmaps, category breakdowns, top merchants, custom date ranges
- **🏦 Multi-Account & Balance Tracking** — Track multiple bank accounts with live balance from SMS and balance history
- **🔄 Subscription Detection** — Automatic recurring payment detection with due date alerts
- **🎨 Custom Categories** — Create your own with custom colors and icons

**More Features**

- **🔐 Biometric App Lock** — Fingerprint/face unlock with configurable timeout
- **📱 Home Screen Widgets** — Budget progress and recent transactions at a glance
- **📤 Data Export & Backup** — CSV export for taxes, full backup/restore (.pennywisebackup)
- **🎭 Material You Theming** — Dynamic colors, multiple cover styles (Aurora, Gradient, Wave), light/dark/system themes
- **✏️ Manual Transactions** — Add and edit transactions manually
- **🔍 Search & Filters** — Filter by category, merchant, period, currency, transaction type
- **🎯 Guided Onboarding** — Spotlight tutorial for first-time users

### Smart Rules Engine

Rules let you auto-categorize, modify, or block transactions based on configurable **conditions**. A rule consists of one or more "When" conditions and a "Then" action.

**Available condition fields:**

| Field | Description | Example value | Supported operators |
|---|---|---|---|
| `AMOUNT` | Transaction amount | `200` | `<`, `>`, `=` |
| `TYPE` | Income/Expense/Transfer | `INCOME` | `contains`, `equals`, `starts with` |
| `CATEGORY` | Assigned category | `Food & Dining` | `contains`, `equals`, `starts with` |
| `MERCHANT` | Merchant/vendor name | `Amazon` | `contains`, `equals`, `starts with` |
| `NARRATION` | Transaction description | `Monthly rent` | `contains`, `equals`, `starts with` |
| `SMS_TEXT` | Raw SMS body | `OTP` | `contains`, `equals`, `starts with` |
| `BANK_NAME` | Bank name from SMS | `HDFC Bank` | `contains`, `equals`, `starts with` |
| `TIME` / `HOUR` | Transaction time | `09:00` / `9` | `before`, `after`, `at or after`, `at or before` |
| `DAY_OF_WEEK` | Day (1=Mon .. 7=Sun) | `1` | `is`, `is any of`, `is not` |
| `DAY_OF_MONTH` | Day of month (1-31) | `15` | `is`, `is any of`, `before`, `after` |
| `DATE` | Transaction date | `2026-03-21` | `is`, `before`, `after` |
| **`ACCOUNT`** | **Bank account composite** | Select from picker | `is`, `is not` |

**Account condition (ACCOUNT)** — Restricts the rule to transactions from a specific bank account. The value is stored as a `BankName||Last4` composite key (e.g. `HDFC Bank||1234`). When selected, the UI shows an account picker populated from your tracked accounts. Only `EQUALS` and `NOT_EQUALS` operators are available.

Conditions can be combined with **AND** / **OR** logic, and multiple conditions per rule are supported.

**Available actions:**
- **Set** — Override a field (category, merchant, type, description)
- **Append / Prepend** — Add text to a field (merchant, description)
- **Clear** — Remove a field value
- **Block** — Prevent the transaction from being saved

**JSON representation** (stored locally, not exposed via API):
```json
{
  "conditions": [
    {"field": "ACCOUNT", "operator": "EQUALS", "value": "HDFC Bank||1234"},
    {"field": "AMOUNT", "operator": "GREATER_THAN", "value": "500"}
  ],
  "actions": [
    {"field": "CATEGORY", "actionType": "SET", "value": "Food & Dining"}
  ]
}
```

## Supported Banks & Countries

<!-- SUPPORTED_BANKS:START (generated by scripts/update-supported-banks.sh — do not edit by hand) -->
Supporting **140 banks & services** across **23 countries** with **multi-currency** capabilities:

### 🇮🇳 India — INR ₹ (53)
**AU Small Finance Bank**, **Airtel Payments Bank**, **Amazon Pay**, **American Express**, **Axis Bank**, **Bandhan Bank**, **Bank of Baroda**, **Bank of India**, **CRED**, **Canara Bank**, **Cashfree**, **Central Bank of India**, **City Union Bank**, **DBS Bank**, **Department of Post**, **Dhanlaxmi Bank**, **Equitas Small Finance Bank**, **Federal Bank**, **Greater Bank**, **HDFC Bank**, **HDFC Mutual Fund**, **HSBC Bank**, **ICICI Bank**, **IDBI Bank**, **IDFC First Bank**, **India Post Payments Bank**, **Indian Bank**, **Indian Overseas Bank**, **IndusInd Bank**, **JK Bank**, **Jana Small Finance Bank**, **Jio Payments Bank**, **JioPay**, **Jupiter**, **Karnataka Bank**, **Kerala Bank**, **Kerala Gramin Bank**, **Kotak Bank**, **LazyPay**, **NSDL Payments Bank**, **Navi Mutual Fund**, **OneCard**, **Punjab & Sind Bank**, **Punjab National Bank**, **Saraswat Co-operative Bank**, **Slice**, **South Indian Bank**, **Standard Chartered Bank**, **State Bank of India**, **UCO Bank**, **Union Bank of India**, **Utkarsh Bank**, **Yes Bank**

### 🇳🇵 Nepal — NPR ₨ (13)
**Citizens Bank**, **Everest Bank**, **Laxmi Sunrise Bank**, **Lumbini Bikash Bank**, **Machchhapuchchhre Bank**, **Manjushree Finance**, **NMB Bank**, **Nabil Bank**, **Nepal Bank Limited**, **Nepal SBI Bank**, **Prime Commercial Bank**, **Siddhartha Bank**, **Standard Chartered Bank Nepal**

### 🇹🇭 Thailand — THB ฿ (11)
**BAAC**, **Bangkok Bank**, **CIMB Thai**, **Government Savings Bank**, **KTC**, **Kasikorn Bank**, **Krungsri**, **Krungthai Bank**, **Siam Commercial Bank**, **TTB**, **UOB Thailand**

### 🇺🇸 United States — USD $ (10)
**ALECU**, **AdelFi**, **Altana Federal Credit Union**, **Charles Schwab**, **Chase**, **Citi Bank**, **Discover Card**, **Huntington Bank**, **Navy Federal Credit Union**, **Old Hickory Credit Union**

### 🇹🇿 Tanzania — TZS TSh (7)
**CRDB Bank**, **Diamond Trust Bank**, **M-Pesa Tanzania**, **Mixx by Yas**, **NMB Bank Tanzania**, **Selcom Pesa**, **Tigo Pesa**

### 🇸🇦 Saudi Arabia — SAR ﷼ (6)
**Al Rajhi Bank**, **Alinma Bank**, **D360 Bank**, **SABB**, **STC Bank**, **Saudi National Bank**

### 🇦🇪 UAE — AED د.إ (6)
**Abu Dhabi Commercial Bank**, **Emirates Islamic**, **Emirates NBD**, **First Abu Dhabi Bank**, **Liv Bank**, **Mashreq Bank**

### 🇮🇷 Iran — IRR ﷼ (5)
**Bankino**, **Mellat Bank**, **Melli Bank**, **Parsian Bank**, **blu Bank**

### 🇳🇬 Nigeria — NGN ₦ (5)
**Access Bank**, **Jaiz Bank**, **Keystone Bank**, **Opay**, **Zenith Bank**

### 🇪🇹 Ethiopia — ETB ብር (4)
**Commercial Bank of Ethiopia**, **Dashen Bank**, **Telebirr**, **Zemen Bank**

### 🇲🇿 Mozambique — MZN MT (4)
**M-Pesa Mozambique**, **Millennium BIM**, **Standard Bank Mozambique**, **eMola**

### 🇱🇰 Sri Lanka — LKR Rs (3)
**National Savings Bank**, **Nations Trust Bank**, **Sampath Bank**

### 🇪🇬 Egypt — EGP E£ (2)
**Arab Bank**, **CIB Egypt**

### 🇪🇺 Eurozone — EUR € (2)
**BPCE**, **Sparkasse Rhein-Maas**

### 🇧🇩 Bangladesh — BDT ৳ (1)
**bKash**

### 🇧🇾 Belarus — BYN Br (1)
**Priorbank**

### 🇨🇴 Colombia — COP $ (1)
**Bancolombia**

### 🇨🇿 Czech Republic — CZK Kč (1)
**mBank CZ**

### 🇰🇪 Kenya — KES Ksh (1)
**M-PESA**

### 🇴🇲 Oman — OMR ر.ع. (1)
**Bank Muscat**

### 🇵🇰 Pakistan — PKR ₨ (1)
**Faysal Bank**

### 🇷🇺 Russia — RUB ₽ (1)
**T-Bank**

### 🇹🇷 Turkey — TRY ₺ (1)
**Enpara**
<!-- SUPPORTED_BANKS:END -->

More banks being added regularly! [Request your bank →](https://github.com/sarim2000/pennywiseai-tracker/issues/new?template=bank_support_request.md)

## Privacy First

All AI processing happens on your device using MediaPipe's Qwen 2.5 model — no cloud, no API calls, no data leaving your phone. There are no accounts to create, no sign-ups, no servers collecting your data. Your SMS and financial information stay entirely on your device. The entire codebase is open source (AGPL v3) so anyone can verify exactly what the app does.

## Screenshots

<p align="center">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/1.png" width="150"/>
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/2.png" width="150"/>
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/3.png" width="150"/>
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/4.png" width="150"/>
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/5.png" width="150"/>
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/6.png" width="150"/>
</p>

<details>
<summary>In-app screenshots (light &amp; dark themes)</summary>

<br/>

**Light Theme**

<table>
<tr>
<td><img src="screenshots/home-light.png" width="160"/></td>
<td><img src="screenshots/analytics-light.png" width="160"/></td>
<td><img src="screenshots/analytics-scroll-light.png" width="160"/></td>
<td><img src="screenshots/transaction-detail-light.png" width="160"/></td>
<td><img src="screenshots/chat-light.png" width="160"/></td>
</tr>
<tr>
<td align="center">Home</td>
<td align="center">Analytics</td>
<td align="center">Categories</td>
<td align="center">Details</td>
<td align="center">AI Chat</td>
</tr>
</table>

**Dark Theme**

<table>
<tr>
<td><img src="screenshots/home-dark.png" width="160"/></td>
<td><img src="screenshots/analytics-dark.png" width="160"/></td>
<td><img src="screenshots/account-detail-dark.png" width="160"/></td>
<td><img src="screenshots/accounts-dark.png" width="160"/></td>
<td><img src="screenshots/chat-dark.png" width="160"/></td>
</tr>
<tr>
<td align="center">Home</td>
<td align="center">Analytics</td>
<td align="center">Account Detail</td>
<td align="center">Accounts</td>
<td align="center">AI Chat</td>
</tr>
</table>

</details>

## Quick Start

```bash
# Clone repository
git clone https://github.com/sarim2000/pennywiseai-tracker.git
cd pennywiseai-tracker

# Build APK
./gradlew assembleDebug

# Install
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Requirements

- Android 12+ (API 31)
- Android Studio Ladybug or newer
- JDK 11

## Tech Stack

<p align="center">
  <img src="https://skillicons.dev/icons?i=kotlin,androidstudio,materialui" /><br>
  <img src="https://skillicons.dev/icons?i=hilt,room,coroutines" />
</p>

**Architecture**: MVVM • Jetpack Compose • Room • Coroutines • Hilt • MediaPipe AI • Material Design 3

## Community & Support

- **Discord**: Join the community, share feedback, and get help — [Join Discord](https://discord.gg/H3xWeMWjKQ)
- **Issues**: Report bugs or request features — [Open an issue](https://github.com/sarim2000/pennywiseai-tracker/issues)

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

Please read our [Code of Conduct](CODE_OF_CONDUCT.md) before participating.

```bash
./gradlew test          # Run tests
./gradlew lint   # Check style
```

## Security

Please review our [Security Policy](SECURITY.md) for how to report vulnerabilities.

## Contributors ✨

Thanks goes to these wonderful people ([emoji key](https://allcontributors.org/docs/en/emoji-key)):

<!-- ALL-CONTRIBUTORS-LIST:START - Do not remove or modify this section -->
<!-- prettier-ignore-start -->
<!-- markdownlint-disable -->
<table>
  <tbody>
    <tr>
      <td align="center" valign="top" width="14.28%"><a href="https://github.com/Lucifer1590"><img src="https://avatars.githubusercontent.com/Lucifer1590?v=4&s=100" width="100px;" alt="Lucifer1590"/><br /><sub><b>Lucifer1590</b></sub></a><br /><a href="#community-Lucifer1590" title="Community Management">👥</a> <a href="https://github.com/sarim2000/pennywiseai-tracker/issues?q=author%3ALucifer1590" title="Bug reports">🐛</a> <a href="#userTesting-Lucifer1590" title="User Testing">📓</a></td>
      <td align="center" valign="top" width="14.28%"><a href="https://github.com/akshaynexus"><img src="https://avatars.githubusercontent.com/akshaynexus?v=4&s=100" width="100px;" alt="akshaynexus"/><br /><sub><b>akshaynexus</b></sub></a><br /><a href="https://github.com/sarim2000/pennywiseai-tracker/commits?author=akshaynexus" title="Code">💻</a></td>
      <td align="center" valign="top" width="14.28%"><a href="https://github.com/WINDY-WINDWARD"><img src="https://avatars.githubusercontent.com/WINDY-WINDWARD?v=4&s=100" width="100px;" alt="WINDY-WINDWARD"/><br /><sub><b>WINDY-WINDWARD</b></sub></a><br /><a href="https://github.com/sarim2000/pennywiseai-tracker/commits?author=WINDY-WINDWARD" title="Code">💻</a></td>
      <td align="center" valign="top" width="14.28%"><a href="https://github.com/brukberhane"><img src="https://avatars.githubusercontent.com/brukberhane?v=4&s=100" width="100px;" alt="brukberhane"/><br /><sub><b>brukberhane</b></sub></a><br /><a href="https://github.com/sarim2000/pennywiseai-tracker/commits?author=brukberhane" title="Code">💻</a></td>
      <td align="center" valign="top" width="14.28%"><a href="https://github.com/KaushalJyanni"><img src="https://avatars.githubusercontent.com/KaushalJyanni?v=4&s=100" width="100px;" alt="KaushalJyanni"/><br /><sub><b>KaushalJyanni</b></sub></a><br /><a href="https://github.com/sarim2000/pennywiseai-tracker/commits?author=KaushalJyanni" title="Code">💻</a></td>
      <td align="center" valign="top" width="14.28%"><a href="https://github.com/cmazwar"><img src="https://avatars.githubusercontent.com/cmazwar?v=4&s=100" width="100px;" alt="cmazwar"/><br /><sub><b>cmazwar</b></sub></a><br /><a href="https://github.com/sarim2000/pennywiseai-tracker/commits?author=cmazwar" title="Code">💻</a></td>
      <td align="center" valign="top" width="14.28%"><a href="https://github.com/AyonPal"><img src="https://avatars.githubusercontent.com/AyonPal?v=4&s=100" width="100px;" alt="AyonPal"/><br /><sub><b>AyonPal</b></sub></a><br /><a href="https://github.com/sarim2000/pennywiseai-tracker/commits?author=AyonPal" title="Code">💻</a></td>
    </tr>
    <tr>
      <td align="center" valign="top" width="14.28%"><a href="https://github.com/rafi-ghanbari"><img src="https://avatars.githubusercontent.com/rafi-ghanbari?v=4&s=100" width="100px;" alt="rafi-ghanbari"/><br /><sub><b>rafi-ghanbari</b></sub></a><br /><a href="https://github.com/sarim2000/pennywiseai-tracker/commits?author=rafi-ghanbari" title="Code">💻</a></td>
      <td align="center" valign="top" width="14.28%"><a href="https://github.com/Chevindu"><img src="https://avatars.githubusercontent.com/Chevindu?v=4&s=100" width="100px;" alt="Chevindu"/><br /><sub><b>Chevindu</b></sub></a><br /><a href="https://github.com/sarim2000/pennywiseai-tracker/commits?author=Chevindu" title="Code">💻</a></td>
      <td align="center" valign="top" width="14.28%"><a href="https://github.com/arunpdl"><img src="https://avatars.githubusercontent.com/arunpdl?v=4&s=100" width="100px;" alt="arunpdl"/><br /><sub><b>arunpdl</b></sub></a><br /><a href="https://github.com/sarim2000/pennywiseai-tracker/commits?author=arunpdl" title="Code">💻</a></td>
      <td align="center" valign="top" width="14.28%"><a href="https://github.com/hegdenischay"><img src="https://avatars.githubusercontent.com/hegdenischay?v=4&s=100" width="100px;" alt="hegdenischay"/><br /><sub><b>hegdenischay</b></sub></a><br /><a href="https://github.com/sarim2000/pennywiseai-tracker/commits?author=hegdenischay" title="Code">💻</a></td>
      <td align="center" valign="top" width="14.28%"><a href="https://github.com/shreyes-shalgar"><img src="https://avatars.githubusercontent.com/shreyes-shalgar?v=4&s=100" width="100px;" alt="shreyes-shalgar"/><br /><sub><b>shreyes-shalgar</b></sub></a><br /><a href="https://github.com/sarim2000/pennywiseai-tracker/issues?q=author%3Ashreyes-shalgar" title="Bug reports">🐛</a></td>
      <td align="center" valign="top" width="14.28%"><a href="https://github.com/test2a"><img src="https://avatars.githubusercontent.com/test2a?v=4&s=100" width="100px;" alt="test2a"/><br /><sub><b>test2a</b></sub></a><br /><a href="https://github.com/sarim2000/pennywiseai-tracker/issues?q=author%3Atest2a" title="Bug reports">🐛</a></td>
      <td align="center" valign="top" width="14.28%"><a href="https://github.com/AlphaXyph"><img src="https://avatars.githubusercontent.com/AlphaXyph?v=4&s=100" width="100px;" alt="AlphaXyph"/><br /><sub><b>AlphaXyph</b></sub></a><br /><a href="https://github.com/sarim2000/pennywiseai-tracker/issues?q=author%3AAlphaXyph" title="Bug reports">🐛</a></td>
    </tr>
    <tr>
      <td align="center" valign="top" width="14.28%"><a href="https://github.com/kamranbahrami261-beep"><img src="https://avatars.githubusercontent.com/kamranbahrami261-beep?v=4&s=100" width="100px;" alt="kamranbahrami261-beep"/><br /><sub><b>kamranbahrami261-beep</b></sub></a><br /><a href="https://github.com/sarim2000/pennywiseai-tracker/issues?q=author%3Akamranbahrami261-beep" title="Bug reports">🐛</a></td>
      <td align="center" valign="top" width="14.28%"><a href="https://github.com/jerryn70"><img src="https://avatars.githubusercontent.com/jerryn70?v=4&s=100" width="100px;" alt="jerryn70"/><br /><sub><b>jerryn70</b></sub></a><br /><a href="https://github.com/sarim2000/pennywiseai-tracker/issues?q=author%3Ajerryn70" title="Bug reports">🐛</a></td>
      <td align="center" valign="top" width="14.28%"><a href="https://github.com/albert-godinho"><img src="https://avatars.githubusercontent.com/albert-godinho?v=4&s=100" width="100px;" alt="albert-godinho"/><br /><sub><b>albert-godinho</b></sub></a><br /><a href="https://github.com/sarim2000/pennywiseai-tracker/issues?q=author%3Aalbert-godinho" title="Bug reports">🐛</a></td>
      <td align="center" valign="top" width="14.28%"><a href="https://github.com/Mikcharls311"><img src="https://avatars.githubusercontent.com/Mikcharls311?v=4&s=100" width="100px;" alt="Mikcharls311"/><br /><sub><b>Mikcharls311</b></sub></a><br /><a href="https://github.com/sarim2000/pennywiseai-tracker/issues?q=author%3AMikcharls311" title="Bug reports">🐛</a></td>
      <td align="center" valign="top" width="14.28%"><a href="https://github.com/rhsjudb"><img src="https://avatars.githubusercontent.com/rhsjudb?v=4&s=100" width="100px;" alt="rhsjudb"/><br /><sub><b>rhsjudb</b></sub></a><br /><a href="https://github.com/sarim2000/pennywiseai-tracker/issues?q=author%3Arhsjudb" title="Bug reports">🐛</a></td>
    </tr>
  </tbody>
</table>

<!-- markdownlint-restore -->
<!-- prettier-ignore-end -->

<!-- ALL-CONTRIBUTORS-LIST:END -->

This project follows the [all-contributors](https://github.com/all-contributors/all-contributors) specification. Contributions of any kind welcome!

## Star History

[![Star History Chart](https://api.star-history.com/svg?repos=sarim2000/pennywiseai-tracker&type=Date)](https://star-history.com/#sarim2000/pennywiseai-tracker&Date)

## License

GNU Affero General Public License v3.0 - see [LICENSE](LICENSE)

This project is licensed under AGPL v3, which means:
- ✅ You can use, modify, and distribute this software
- ✅ You must share your modifications under the same license
- ✅ If you run a modified version on a server, you must make the source code available to users
- ✅ Patent rights are explicitly granted and protected

---

<p align="center">
<a href="https://github.com/sarim2000/pennywiseai-tracker/releases">Download</a> •
<a href="https://github.com/sarim2000/pennywiseai-tracker/issues">Report Bug</a> •
<a href="https://github.com/sarim2000/pennywiseai-tracker/issues">Request Feature</a>
</p>
