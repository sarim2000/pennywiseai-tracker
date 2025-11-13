# Test Implementation Standards for Parser Modules

This repository now provides a shared Kotlin test harness for all SMS parser
implementations (`ParserTestUtils`). Any new or existing parser tests must follow
the guidelines below.

## 1. Organise tests with JUnit 5

* Each parser must expose a dedicated JUnit 5 test class (e.g.
  `class FabParserTest`).
* Avoid `fun main` entry points; every scenario should be expressed through
  standard JUnit test methods annotated with `@Test`.

## 2. Leverage `ParserTestUtils`

* Import utilities from `com.pennywiseai.parser.core.test`:
  * `ExpectedTransaction`
  * `ParserTestCase`
  * `SimpleTestCase`
  * `ParserTestUtils`
* Use `ParserTestUtils.runTestSuite` for parser-specific assertions and
  `ParserTestUtils.runFactoryTestSuite` for sender lookups that delegate to
  `BankParserFactory`.

### Parser-specific tests

```kotlin
@Test
fun `fab parser handles key paths`() {
    val parser = FABParser()

    ParserTestUtils.printTestHeader(
        parserName = "First Abu Dhabi Bank",
        bankName = parser.getBankName(),
        currency = parser.getCurrency()
    )

    val cases = listOf(
        ParserTestCase(
            name = "Card purchase",
            message = "...",
            sender = "FAB",
            expected = ExpectedTransaction(
                amount = BigDecimal("123.45"),
                currency = "AED",
                type = TransactionType.EXPENSE,
                merchant = "Merchant",
                accountLast4 = "1234",
                isFromCard = true
            )
        )
    )

   ParserTestUtils.runTestSuite(parser, cases)

}
```

### Factory coverage

```kotlin
@Test
fun `factory resolves fab`() {
    val cases = listOf(
        SimpleTestCase(
            bankName = "First Abu Dhabi Bank",
            sender = "FAB",
            currency = "AED",
            message = "...",
            expected = ExpectedTransaction(
                amount = BigDecimal("8.00"),
                currency = "AED",
                type = TransactionType.CREDIT
            ),
            shouldHandle = true
        )
    )

   ParserTestUtils.runFactoryTestSuite(cases, "Factory smoke tests")
}
```

## 3. Populate `ExpectedTransaction` thoughtfully

* Provide only the fields asserted by the parser implementation. Superfluous
  expectations (e.g. `creditLimit`) will fail when the parser leaves values
  `null`.
* Comparison uses exact equality; normalise formatted output in the test case if
  the parser already strips punctuation or casing.

## 4. Maintain deterministic test output

* The utilities print per-case status plus a summary table. This output is
  relied on in existing workflows—do not remove it.
* The utilities also register per-case JUnit assertions via `assertAll`, so
  every failure is visible in IDEs and CI.

## 5. Migration checklist for existing tests

1. Replace any ad-hoc `main` runners with JUnit test classes.
2. Move hard-coded assertions into `ExpectedTransaction` entries.
3. Swap manual loops for `ParserTestUtils.runTestSuite`.
4. Keep the final summary print to retain parity with legacy scripts.

Following these standards keeps parser coverage consistent and guarantees that
human-readable logs and JUnit tooling stay in sync.
