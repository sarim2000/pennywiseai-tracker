# Android Debug demo data

Install the Debug APK you want to test on a fresh, disposable Android emulator. Run the seed from the repo root.

```sh
ANDROID_SERIAL=<emulator-serial> ./scripts/seed-debug.sh
```

`ANDROID_SERIAL` can be omitted when ADB has one device. Open PennyWise after the command completes. On a fresh install the seed finishes onboarding, skips SMS access and shows JOD as the base currency. Repeating the command leaves the original seed dates and rows in place. Use a fresh disposable installation for a clean slate or a new date anchor. The command refuses a database with existing user content.

The seed writes a synthetic ledger into the app's Room database. It covers six calendar months across JOD, USD and EUR and both profiles.

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

Open Chat in the seeded app. You can try the budget and bill quick actions immediately. For typed questions, use **Download** in Chat to install the on-device model. Keep the profile filter on **All** and unified currency off for these figures. The spending rows for the current month are dated on the seed day. Run these questions in the seed month, before editing the examples.

| English | العربية | Expected data |
| --- | --- | --- |
| How much did I spend on Food & Dining this month? | كم صرفت على الأكل والمطاعم هذا الشهر؟ | JOD 32.000, USD 18.00 and EUR 9.50, shown as three figures |
| How much did I spend on Food & Dining last month? | كم صرفت على الأكل والمطاعم الشهر الماضي؟ | JOD 20.000, USD 10.00 and EUR 6.00 |
| How much did I spend on groceries this month? | كم صرفت على البقالة هذا الشهر؟ | JOD 30.000 after the refund |
| How are my budgets doing? | كيف وضع ميزانياتي؟ | Demo dining is JOD 7.000 over. Demo groceries has JOD 60.000 left. |
| Which bills are due in the next seven days? | ما الفواتير المستحقة خلال الأيام السبعة القادمة؟ | Demo Streaming is due two days after seeding. Demo Internet was due one day before seeding. |
| Add a 3.500 JOD expense for Demo Tea today. | أضف مصروفًا بقيمة ٣٫٥٠٠ دينار أردني لـ Demo Tea اليوم. | Review a draft for JOD 3.500. Confirm it to write the transaction. |

[chat-eval](../scripts/chat-eval/README.md) measures the model and its tool calls separately. [SmsProbeReceiver](../.agents/skills/emulator-verify/SKILL.md#4-seed-synthetic-data) exercises real SMS parsing and alerts.

The seed performs no webhook delivery and requires no bank messages, license or connector credentials. For external AI pairing and the Dashboard, use the [relay's local fake phone](../relay/README.md).
