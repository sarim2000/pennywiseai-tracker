# Smart Rules Engine

Rules let you auto-categorize, modify, or block transactions based on configurable **conditions**. A rule consists of one or more "When" conditions and a "Then" action.

**Available condition fields:**

| Field | Description | Example value | Supported operators |
|---|---|---|---|
| `AMOUNT` | Transaction amount | `200` | `<`, `>`, `=` |
| `TYPE` | Income/Expense/Transfer | `INCOME` | `is`, `is not` |
| `CATEGORY` | Assigned category | `Food & Dining` | `contains`, `equals`, `starts with` |
| `MERCHANT` | Merchant/vendor name | `Amazon` | `contains`, `equals`, `starts with` |
| `NARRATION` | Transaction description | `Monthly rent` | `contains`, `equals`, `starts with` (rule files only; the editor doesn't offer it) |
| `SMS_TEXT` | Raw SMS body | `OTP` | `contains`, `equals`, `starts with` |
| `BANK_NAME` | Bank name from SMS | `HDFC Bank` | `contains`, `equals`, `starts with` |
| `TIME` | Time of day | `09:00` | `before`, `after`, `at or after`, `at or before`, `exactly at` |
| `HOUR` | Hour of day | `9` | `is`, `before`, `after` |
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

**JSON representation** (stored on your phone). The same shape is used in rule export files and in the `list_rules` answer of [AI access](ai-access.md), which works only while an AI session you started is on (Pro):
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
