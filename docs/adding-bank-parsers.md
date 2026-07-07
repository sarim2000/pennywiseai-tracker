# Adding a Bank SMS Parser

Bank parsers live in the **`parser-core`** module (pure Kotlin, no Android
dependencies) so they can be reused across platforms. For anything more than a
trivial tweak, prefer the **`parser-author`** subagent, which owns this
end-to-end (read samples → write/extend parser → tests → register → run tests).

## Where parsers live
`parser-core/src/main/kotlin/com/pennywiseai/parser/core/bank/`

## Base class — pick the right one
All parsers extend `BankParser`. But:

- **Indian banks** MUST extend `BaseIndianBankParser` to inherit centralized
  mandate, subscription, and balance-update logic.
- **UAE banks** MUST extend `UAEBankParser` for currency and transaction-type
  handling.
- Everything else extends `BankParser` directly.

## Key methods
- `getBankName()` — the bank's display name.
- `canHandle(sender: String)` — whether this parser handles SMS from a sender.
- `parse(smsBody, sender, timestamp)` — returns `ParsedTransaction` or `null`.

## Commonly overridden
- `extractAmount()` — bank-specific amount patterns.
- `extractMerchant()` — bank-specific merchant extraction.
- `extractTransactionType()` — only for special cases.

## Registration
Add the new parser to the `BankParserFactory.parsers` list in
`parser-core/.../bank/BankParserFactory.kt`.

## Return type & imports (parser-core)
Use `ParsedTransaction` from parser-core:
- `com.pennywiseai.parser.core.TransactionType`
- `com.pennywiseai.parser.core.ParsedTransaction`
- `java.math.BigDecimal` for amounts

## Using a parser result in the app
Convert with `com.pennywiseai.tracker.data.mapper.toEntity()`, which maps
`ParsedTransaction` → `TransactionEntity` and handles cross-module type
conversions.

## Tests
Parser tests MUST use the shared `ParserTestUtils` JUnit 5 helpers — see
[`docs/parser-test-standards.md`](parser-test-standards.md). Run them with:

```bash
./gradlew :parser-core:test          # or :parser-core:jvmTest
```

## Coverage
The full list of supported banks and transaction patterns lives in
[`docs/BANK_SUPPORT.md`](BANK_SUPPORT.md) and
[`docs/supported-banks.json`](supported-banks.json) — the authoritative source,
kept in sync with the parsers (do not maintain a hand-copied list elsewhere).
