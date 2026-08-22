# Test Implementation Standards for Parser Modules

This repository provides a shared Kotlin test harness for all SMS parser implementations (`ParserTestUtils`). Any new or existing parser tests must follow the guidelines below.

## 1. Organise tests with JUnit 5 Dynamic Tests

* Each parser must expose a dedicated JUnit 5 test class. Newer tests use `XxxParserTest.kt` under `src/test/kotlin/com/pennywiseai/parser/core/bank/`; many older tests sit at the test root with a `TestXxx` file prefix (the FAB suite is `TestFABParser.kt` containing `class FABParserTest`). Match whatever the file you're editing already uses.
* Use `@TestFactory` instead of `@Test` for test suites that cover multiple scenarios.
* Return `List<DynamicTest>` by leveraging `ParserTestUtils.runTestSuite` or `runFactoryTestSuite`.
* This provides granular reporting where each test case appears as a separate result in the IDE and CI/CD logs.

## 2. Leverage `ParserTestUtils`

* Import utilities from `com.pennywiseai.parser.core.test`:
  * `ExpectedTransaction`
  * `ParserTestCase`
  * `SimpleTestCase`
  * `ParserTestUtils`
* Use `ParserTestUtils.runTestSuite` for parser-specific assertions and `ParserTestUtils.runFactoryTestSuite` for sender lookups that delegate to `BankParserFactory`.
* `runTestSuite` also accepts an optional `handleCases: List<Pair<String, Boolean>>` argument for asserting `canHandle()` outcomes next to the parsing assertions.

### Parser-specific tests

```kotlin
@TestFactory
fun `fab parser handles key paths`(): List<DynamicTest> {
    val parser = FABParser()

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

    return ParserTestUtils.runTestSuite(parser, cases)
}
```

### Factory coverage

```kotlin
@TestFactory
fun `factory resolves fab`(): List<DynamicTest> {
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

    return ParserTestUtils.runFactoryTestSuite(cases, "Factory smoke tests")
}
```

## 3. Populate `ExpectedTransaction` thoughtfully

* Provide only the fields asserted by the parser implementation. Superfluous expectations (e.g. `creditLimit`) will fail when the parser leaves values `null`.
* Comparison uses exact equality; normalise formatted output in the test case if the parser already strips punctuation or casing.

## 4. Native JUnit 5 Reporting

* The utilities no longer require manual `println` calls for reporting.
* JUnit 5's `DynamicTest` handles per-case status reporting automatically.
* The utilities register per-case JUnit assertions via `assertAll`, so every failure is visible in IDEs and CI.

## 5. Migration checklist for existing tests

1. Replace `@Test` with `@TestFactory`.
2. Ensure the method returns `List<DynamicTest>`.
3. Call `ParserTestUtils.runTestSuite` and return its result.
4. Drop manual `println` reporting. `printTestHeader` survives as a no-op stub that many existing tests still call — it does nothing, so remove the calls only if you're already editing those lines.

Following these standards keeps parser coverage consistent and guarantees that
human-readable logs and JUnit tooling stay in sync.
