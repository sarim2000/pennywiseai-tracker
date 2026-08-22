# Bank SMS Support

PennyWise parses bank SMS into transactions through a registry of dedicated parsers. As of August 2026 the registry holds **144 parsers covering 23 countries**.

This page explains how support works and where to look things up. It deliberately doesn't repeat the full bank list — that list is machine-generated and would drift the moment someone adds a parser without updating prose.

## The authoritative bank list

`docs/supported-banks.json` is generated directly from the `BankParserFactory` registry and guarded by CI (`SupportedBanksDocTest` fails when it goes stale). Per-country counts, bank names, and feature flags there are ground truth. If a bank appears in the JSON, a parser registered in `BankParserFactory` handles its senders today.

After adding, removing, or renaming any parser, run:

```bash
./scripts/update-supported-banks.sh
```

It regenerates the JSON, the README bank table, the Play listing copy, and copies for pennywise-web. CI fails on the next push if you forget.

## How an SMS becomes a transaction

1. A notification or SMS arrives; `receiver/BankNotificationListenerService` routes the body into `data/manager/SmsTransactionProcessor`.
2. `BankParserFactory.getParsers()` returns every parser whose `canHandle(sender)` matches the sender ID.
3. Each candidate parser's `parse()` runs against the body in **registration order** until one returns a `ParsedTransaction`. Order matters — a broad matcher placed above a narrow one will swallow its messages.

Parsers extend one of five base classes in `parser-core/src/main/kotlin/com/pennywiseai/parser/core/bank/`: `BankParser` (generic international), `BaseIndianBankParser`, `UAEBankParser`, `BaseIranianBankParser`, or `BaseThailandBankParser`. Pick the regional base when one exists; it carries currency handling and common patterns for that market.

Full contributor walkthrough: [adding-bank-parsers.md](adding-bank-parsers.md). Test conventions: [parser-test-standards.md](parser-test-standards.md).

## What gets extracted

Every parser fills a `ParsedTransaction`: amount, credit/debit type, merchant, account last-4, timestamp, and where the message supports it — reference number, balance after the transaction, UPI VPA, card last-4. Which fields a given bank populates depends on what its SMS templates contain; the parser source is the honest answer per bank.

## Balance extraction

The base class ships default patterns (`Bal:`, `Balance:`, `Avl Bal`, `Available Balance`, each followed by an amount). Banks whose messages phrase balances differently override `extractBalance()`. Example: `ICICIBankParser` overrides it with its own `Avl`/`Avb` patterns rather than relying on defaults. When auditing a bank, check the parser file before assuming either way.

## Worked example: HDFC Bank

Two of the current card patterns in `HDFCBankParser.kt` show the flavor:

```kotlin
// Card purchase at a merchant
"Spent Rs.X From HDFC Bank Card X At [MERCHANT]"

// Card spend phrased without merchant
"...spent on Card XX1234"
```

Each pattern lives beside its siblings in the same parser file, so grepping a bank's name inside `parser-core/src/main/kotlin/com/pennywiseai/parser/core/bank/` shows every message shape it handles.

## Adding a bank

Short version: create a parser extending the right base class, implement `getBankName()` and `canHandle()`, register it in `BankParserFactory` **above any broader matcher that could claim the same senders**, add tests with real SMS samples, then run `./scripts/update-supported-banks.sh`. See [adding-bank-parsers.md](adding-bank-parsers.md) for the complete guide.

---

*Last Updated: August 2026*
