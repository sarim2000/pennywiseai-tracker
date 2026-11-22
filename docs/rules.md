# Smart Rules Engine

Rules let you auto-categorize, modify, or block transactions based on configurable **conditions**. A rule consists of one or more "When" conditions and a "Then" action.

**Available condition fields** (the Field column is the name used in rule JSON):

| Field | Description | Example value | Operators in the app |
|---|---|---|---|
| `AMOUNT` | Transaction amount | `200` | `<`, `>`, `=` |
| `TYPE` | `INCOME`, `EXPENSE`, `CREDIT`, `TRANSFER` or `INVESTMENT` | `EXPENSE` | `is`, `is not` |
| `CATEGORY` | Assigned category | `Food & Dining` | `contains`, `equals`, `starts with` |
| `MERCHANT` | Merchant/vendor name | `Amazon` | `contains`, `equals`, `starts with` |
| `NARRATION` | Transaction description | `Monthly rent` | `contains`, `equals`, `starts with` (rule files only; the editor doesn't offer it) |
| `SMS_TEXT` | Raw SMS body | `OTP` | `contains`, `equals`, `starts with` |
| `BANK_NAME` | Bank name from SMS | `HDFC Bank` | `contains`, `equals`, `starts with` |
| `TRANSACTION_TIME` | Time of day (`HH:mm`) | `09:00` | `before`, `after`, `at or after`, `at or before`, `exactly at` |
| `TRANSACTION_HOUR` | Hour of day (0-23) | `9` | `is`, `before`, `after` |
| `TRANSACTION_DAY_OF_WEEK` | Day (1=Mon .. 7=Sun) | `1` | `is`, `is any of`, `is not` |
| `TRANSACTION_DAY_OF_MONTH` | Day of month (1-31) | `15` | `is`, `is any of`, `before`, `after` |
| `TRANSACTION_DATE` | Transaction date (`yyyy-MM-dd`) | `2026-03-21` | `is`, `before`, `after` |
| **`ACCOUNT`** | **Bank account composite** | Select from picker | `is`, `is not` |

**Operator names in rule JSON:**

| In the app | In JSON |
|---|---|
| `=`, `is`, `equals`, `exactly at` | `EQUALS` |
| `is not` | `NOT_EQUALS` |
| `contains` | `CONTAINS` |
| `starts with` | `STARTS_WITH` |
| `<`, `before` | `LESS_THAN` |
| `>`, `after` | `GREATER_THAN` |
| `at or before` | `LESS_THAN_OR_EQUAL` |
| `at or after` | `GREATER_THAN_OR_EQUAL` |
| `is any of` | `IN` (comma-separated values, e.g. `"1,6,7"`) |

**Account condition (ACCOUNT)** — Restricts the rule to transactions from a specific bank account. The value is stored as a `BankName||Last4` composite key (e.g. `HDFC Bank||1234`). When selected, the UI shows an account picker populated from your tracked accounts. Only `EQUALS` and `NOT_EQUALS` operators are available.

Conditions can be combined with **AND** / **OR** logic, and multiple conditions per rule are supported.

**Available actions:**
- **Set** — Override a field (category, merchant, type, description)
- **Append / Prepend** — Add text to a field (merchant, description)
- **Clear** — Remove a field value
- **Add tag / Remove tag** — Tag or untag the transaction
- **Block** — Prevent the transaction from being saved

**JSON format.** Rule export files, and the `list_rules` answer of [AI access](ai-access.md) (Pro, only while an AI session you started is on), wrap rules in a versioned envelope:

```json
{
  "version": 1,
  "rules": [
    {
      "name": "Large HDFC spends are food",
      "description": null,
      "priority": 100,
      "conditions": [
        {"field": "ACCOUNT", "operator": "EQUALS", "value": "HDFC Bank||1234", "logicalOperator": "AND"},
        {"field": "AMOUNT", "operator": "GREATER_THAN", "value": "500", "logicalOperator": "AND"}
      ],
      "actions": [
        {"field": "CATEGORY", "actionType": "SET", "value": "Food & Dining"}
      ],
      "isActive": true
    }
  ]
}
```

Action types in JSON: `SET`, `APPEND`, `PREPEND`, `CLEAR`, `ADD_TAG`, `REMOVE_TAG`, `BLOCK`.
