---
name: parser-author
description: Add or fix a bank SMS parser in parser-core end-to-end (parser, factory registration, ParserTestUtils tests, regenerated bank docs). Use for a new bank, a bank whose SMS parse wrongly or not at all, or a [BANK] issue that is ready to implement.
---

# Parser author

Sources of truth, read them rather than this file for detail:
[adding-bank-parsers.md](../../../docs/adding-bank-parsers.md),
[parser-test-standards.md](../../../docs/parser-test-standards.md),
[BANK_SUPPORT.md](../../../docs/BANK_SUPPORT.md). Code lives in
`parser-core/src/main/kotlin/com/pennywiseai/parser/core/bank/`, tests in
`parser-core/src/test/kotlin/` (new tests go under `com/pennywiseai/parser/core/bank/`).

## Inputs

Bank name, country, every sender ID seen (exact spelling, including DLT forms such
as `AX-ICICIT-S`), and SMS samples, masked, one per message shape you need to
support (debit, credit, card, UPI/transfer, ...), plus any OTP/promo/reminder the
bank sends. Without at least one sample per shape, stop and ask for them, or hand
the issue to parser-triage. Never guess a template.

## Procedure

1. **Check existing coverage first.** `git grep -n -i '<SENDER>' parser-core/src/main`
   and look in `docs/supported-banks.json`. If a parser already claims the sender,
   this is a fix to that parser, not a new one. Also look for broad matchers that
   would intercept it: `EverestBankParser` claims every 7–10 digit numeric sender,
   `HDFCBankParser` has a broad DLT pattern, and some senders are shared by design
   (`NMB`, M-Pesa, `Transfer365`).
2. **Pick the base class by country:** India → `BaseIndianBankParser`; UAE →
   `UAEBankParser`; Iran → `BaseIranianBankParser`; Thailand →
   `BaseThailandBankParser`; El Salvador → `BaseElSalvadorBankParser`; anything
   else → `BankParser`. Copy the structure of a recent parser from the same country.
3. **Implement.** Override `getBankName()`, `canHandle()` (normalise case, spaces,
   `-`/`_`, DLT prefixes and suffixes), and `getCurrency()` whenever the base is
   `BankParser` (it defaults to `"INR"`). Override only the `extract*` methods the
   template needs. If you override `isTransactionMessage()`, call
   `isNonTransactionMessage()` first, don't re-list the OTP/promo/reminder checks.
   Return `null` for any shape you don't recognise. A non-null result from the wrong
   parser hides the message from every parser registered after it.
4. **Register** in `BankParserFactory.parsers`, next to the same country's parsers
   and **above** any parser whose `canHandle()` also matches your senders. Write
   the inline comment in the existing style: bank, country, sender, and why it
   must come before another parser when that is the case.
5. **Test with `ParserTestUtils`** (`@TestFactory` + `runTestSuite`, with
   `handleCases` for the sender spellings), covering:
   - one case per supported shape, asserting amount, currency, type, and the
     merchant/last4/balance the parser extracts;
   - a regression case reproducing the reported bug, for fixes;
   - `shouldParse = false` cases for that bank's OTP, promo, and due/reminder SMS;
   - factory routing: `runFactoryTestSuite` for a unique sender. For a shared
     sender, assert `BankParserFactory.parse(body, sender, ts)?.bankName`
     directly (see `NMBTanzaniaParserTest`), because `runFactoryTestSuite` uses
     `getParser()`, which only returns the first `canHandle()` match.
6. **Use synthetic data only (Hard Constraint 4).** Replace real names, account and
   card numbers, UPI IDs, phone numbers, and references with clearly fake values
   (`JOHN DOE`, `XX1234`, `merchant@ybl`). Keep the bank's own masking style. Where
   the reporter masked digits with `X`, rebuild a plausible fake value of the same
   shape so the regexes are exercised. Test messages are published verbatim on
   public bank pages (`BankSamplesDocTest` → `bank-samples.json`).
7. **Regenerate** when you added, removed, or renamed a parser, or changed what it
   extracts: `./scripts/update-supported-banks.sh`. It rewrites
   `docs/supported-banks.json`, the README bank block, the Play
   `full_description.txt` coverage line, and pennywise-web's `supported-banks.json`
   and `bank-samples.json`. Check the diff for exactly your bank.
8. **Verify:** `./init.sh parser` must pass. It runs `:parser-core:test`, which
   includes `IosCompatibilityTest`, `SupportedBanksDocTest`, and `BankSamplesDocTest`.

## Commit

One concern per commit (a parser fix doesn't also re-sort the factory). Titles
follow history, with the issue number at the end:
`feat(parser): add City Bank Bangladesh SMS support (#1038)`,
`fix(parser): skip ICICI OTP messages that mention an INR amount (#1141)`.
Commit the regenerated docs together with the parser change that caused them.

## Failure modes the docs don't spell out

- **New currency:** `SupportedBanksDocTest` throws until you add the currency to
  its `currencyMeta` map. A bank quoting another country's currency (USD in El
  Salvador) needs a `countryOverride` entry, or it gets listed under the wrong country.
- **iOS build:** parser-core also compiles for iOS (consumed by `:shared`) and
  feeds the pennywise-web server. Avoid `String.format`, `Locale`, `Math.`,
  `Character.`, and the other APIs listed in adding-bank-parsers.md. Use
  `"...".toBigDecimal()` and `Regex`.
- **iOS parity:** if your change alters a sample in
  `parser-core/src/test/resources/ios-parity.tsv`, the JVM gate stays green but
  iOS doesn't. Regenerate with
  `PARITY_REGEN=1 ./gradlew :parser-core:jvmTest --tests '*IosParityFixture*'`.
- **Base-class keyword filter:** `isNonTransactionMessage()` matches substrings,
  so a real transaction containing `offer`, `otp`, `win `, or `due on` (without a
  completed-money verb) is silently dropped. Add a test case that covers it.
- **Showcase samples:** `BankSamplesDocTest` only harvests literal, single-line
  `message = "..."` strings of 40–260 characters (helper-built messages are
  skipped), and drops placeholder balances such as `999999999`. Keep at least one
  realistic literal sample per bank.
- **Web tester:** `pennywise-web/server/src/main/kotlin/Routing.kt` keeps its own
  hand-picked parser list. Touch it only if the web tester should advertise the bank.
