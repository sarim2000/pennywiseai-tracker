# Automation: open Add Transaction pre-filled

Automation apps such as Tasker and MacroDroid, or `adb`, can open PennyWise's
Add Transaction form with values already filled in. PennyWise never saves from
an automation: the form opens, you check it, and you tap **Save**. With App Lock
on, you unlock first, as with any other way into the app.

There are two ways in. Both take the same keys.

## 1. The intent

| | |
|---|---|
| Action | `com.pennywiseai.tracker.action.ADD_TRANSACTION` |
| Category | `android.intent.category.DEFAULT` |
| Package | `com.pennywiseai.tracker` (debug builds: `com.pennywiseai.tracker.debug`) |
| Target | Activity |

Values go in as extras. Text and number extras both work.

## 2. The link

`pennywise://add` opens the same form. The keys go in as query parameters,
URL-encoded (a space is `%20`):

```
pennywise://add?amount=12.50&currency=USD&merchant=Test%20Cafe&note=from%20tasker
```

Use the link when your automation app has fewer extra fields than you need, or
when it turns a value such as `0123` into the number `123`.

## Keys

| Key | Value | Example |
|---|---|---|
| `amount` | A positive number with a dot for decimals, no thousands separators | `12.50` |
| `currency` | A three-letter currency code PennyWise supports | `USD` |
| `merchant` | Who was paid, or who paid you | `Test Cafe` |
| `category` | A category name as it appears in PennyWise | `Food & Dining` |
| `type` | `EXPENSE` or `INCOME` | `EXPENSE` |
| `note` | Free text, saved as the transaction's note | `from tasker` |
| `account_last4` | The account's last 4 digits, to pick that account | `1234` |
| `bank` | The account's bank name, as PennyWise shows it, used with `account_last4` | `Test Bank` |

Every key is optional. A value PennyWise can't use is skipped and that field is
left for you to fill in: an amount that isn't a positive number, a currency it
doesn't know, a type other than `EXPENSE` or `INCOME`. Text is trimmed and cut
at 200 characters. Without `currency`, the form uses your base currency. With a
`currency` PennyWise doesn't know, the amount is skipped too, so it is never
shown in the wrong currency. An account is picked only when one in the form's
currency matches.

## Examples

All values below are made up.

### adb

```sh
adb shell am start \
  -a com.pennywiseai.tracker.action.ADD_TRANSACTION \
  -p com.pennywiseai.tracker \
  --es amount 12.50 --es currency USD \
  --es merchant "Test Cafe" --es note "from tasker" --es type EXPENSE
```

### Tasker

Add a **System → Send Intent** action:

- **Action:** `com.pennywiseai.tracker.action.ADD_TRANSACTION`
- **Cat:** Default
- **Package:** `com.pennywiseai.tracker`
- **Extra:** `amount:12.50`
- **Extra:** `merchant:Test Cafe`
- **Extra:** `note:from tasker`
- **Target:** Activity

Tasker variables work in the values, e.g. `amount:%amount`. For more keys than
the action has Extra fields, use the link instead: set **Action** to
`android.intent.action.VIEW` and **Data** to
`pennywise://add?amount=12.50&currency=USD&merchant=Test%20Cafe&type=EXPENSE&note=from%20tasker`.

### MacroDroid

Add an **Applications → Send Intent** action:

- **Target:** Activity
- **Action:** `com.pennywiseai.tracker.action.ADD_TRANSACTION`
- **Package:** `com.pennywiseai.tracker`
- **Extra parameters** (type String): `amount` = `12.50`, `currency` = `USD`,
  `merchant` = `Test Cafe`, `note` = `from macrodroid`, `type` = `EXPENSE`

MacroDroid's magic text works in the values, e.g. the text of a notification
that triggered the macro.

## Older extras

The long extra names PennyWise uses internally
(`com.pennywiseai.tracker.ADD_AMOUNT`, `…ADD_MERCHANT`, `…ADD_CATEGORY`,
`…ADD_BANK`, `…ADD_LAST4`, `…ADD_CURRENCY`, `…ADD_TYPE`) are also read with the
action and the link, and checked the same way. When both are given, the short
key wins. Use the short keys in new automations.
