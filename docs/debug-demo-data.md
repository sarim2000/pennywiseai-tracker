# Android Debug demo data

Install the Debug APK you want to test on a fresh, disposable Android emulator.
Open PennyWise. The first launch seeds an empty database, finishes onboarding,
skips SMS access and shows JOD as the base currency. A database with existing
content, including customized built-in categories, profiles, or rule templates,
is left alone. Untouched built-in rule templates can be present when seeding runs.
An existing installation that already finished onboarding is skipped as well.
The app remembers the first launch after the check, even if you clear transactions later.

To seed explicitly before opening the app, run this from the repo root.

```sh
ANDROID_SERIAL=<emulator-serial> ./scripts/seed-debug.sh
```

`ANDROID_SERIAL` can be omitted when ADB has one device. Repeating the command
keeps the original dates and rows. Use a fresh disposable install to pick up a
newer fixture or anchor it to a new date.

The fixture writes roughly 4,200–4,700 transactions across 12 calendar months
and both profiles. The exact count depends on the launch date. It includes
Arab Bank and Bank al Etihad (JOD), Chase (USD), and Deutsche Bank (EUR).
All amounts, account suffixes, merchants, and messages are synthetic.
For Unified Currency with JOD as the display currency, the fixture includes
offline demo rates of JOD 0.71 per USD and JOD 0.80 per EUR.
The [chart fixture](../app/src/debug/java/com/pennywiseai/tracker/debug/DemoChartTransactions.kt)
generates daily activity relative to launch, and both entry points insert the
same fixture in one Room transaction.

| Screen or workflow | Examples |
| --- | --- |
| Home, Transactions, Analytics, Insights and Calendar | Salary, expenses, a credit purchase, a transfer pair, an investment, a refund, a category split, a shared bill, an unsorted expense and an excluded expense |
| Transaction details, Groups and Tags | Demo Market has a PDF receipt and a tag, belongs to Demo weekend and funds a goal |
| Accounts, balance history and net worth | Checking, savings, a bank loan, debit and credit cards, a card statement and an account alias |
| Plan, Budgets and History | An over-budget dining limit, a grocery limit with room left and a previous-month snapshot |
| Bills and Recurring transactions | Upcoming and overdue bills, an installment plan, an ended subscription and a future recurring payment |
| Goals and Loans | Contributions and a withdrawal, an archived goal, a borrowed amount and a lent share |
| Categories, Learned Categories, Rules and Unrecognized SMS | A custom subcategory, a merchant mapping and alias, a rule with an application record and a synthetic bank message |
| Chat and Webhooks | A short sample conversation and a disabled webhook with synthetic delivery history |

## Check the real chat

Open Chat in the seeded app. You can try the budget and bill quick actions immediately. For typed questions, use **Download** in Chat to install the on-device model. Keep the profile filter on **All** and unified currency off for these figures. Run these questions in the seed month, before editing the examples. Replace date placeholders with actual dates using YYYY-MM-DD.

| English | العربية | Expected data |
| --- | --- | --- |
| How much did I spend on Food & Dining this month? | كم صرفت على الأكل والمطاعم هذا الشهر؟ | JOD 32.000, USD 18.00 and EUR 9.50, shown as three figures |
| What was my Food & Dining total from [first date] through [last date] of the previous month? | كم صرفت على الأكل والمطاعم من [أول تاريخ] إلى [آخر تاريخ] في الشهر الماضي؟ | JOD 20.000, USD 10.00 and EUR 6.00 |
| Compare Food & Dining this month with the same period last month. | قارن مصروف الأكل والمطاعم هذا الشهر مع نفس الفترة من الشهر الماضي. | For a seed on the 20th, JOD 32.000 vs 20.000, USD 18.00 vs 10.00 and EUR 9.50 vs 0.00 |
| How much did I spend on groceries this month? | كم صرفت على البقالة هذا الشهر؟ | JOD 30.000 after the refund |
| How are my budgets doing? | كيف وضع ميزانياتي؟ | Demo dining is JOD 7.000 over. Demo groceries has JOD 60.000 left. |
| Which bills are due in the next seven days? | ما الفواتير المستحقة خلال الأيام السبعة القادمة؟ | Demo Streaming is due two days after seeding. Demo Internet was due one day before seeding. |
| Add a 3.500 JOD expense for Demo Tea today. | أضف مصروفًا بقيمة ٣٫٥٠٠ دينار أردني لـ Demo Tea اليوم. | Review a draft for JOD 3.500. Confirm it to write the transaction. |

[chat-eval](../scripts/chat-eval/README.md) measures the model and its tool calls separately. [SmsProbeReceiver](../.agents/skills/emulator-verify/SKILL.md#4-seed-synthetic-data) exercises real SMS parsing and alerts.

Check the date range in the reply before comparing figures. Month comparisons use the same elapsed days in both months. The previous month's EUR purchase is on day 24, so it appears in the full-month total and in comparisons seeded on or after the 24th.

The seed performs no webhook delivery and requires no bank messages, license or connector credentials. For external AI pairing and the Dashboard, use the [relay's local fake phone](../relay/README.md).
