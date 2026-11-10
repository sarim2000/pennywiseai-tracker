import Shared
import XCTest
@testable import iosApp

/// Typed amounts into stored minor units. Every number is made up.
final class MoneyEntryTests: XCTestCase {
    func testTypedAmountsRoundIntoTheCurrencysMinorUnits() {
        // 1.001 × 1000 is 1000.9999… in a Double: truncating stored 1.000.
        XCTAssertEqual(AmountFormatter.minorUnits(1.001, currency: "JOD"), 1001)
        XCTAssertEqual(AmountFormatter.minorUnits(1.235, currency: "KWD"), 1235)
        XCTAssertEqual(AmountFormatter.minorUnits(12.34, currency: "USD"), 1234)
        XCTAssertEqual(AmountFormatter.minorUnits(-4.5, currency: "INR"), -450)
    }

    func testReopeningAThreeDecimalAmountKeepsIt() {
        let shown = AmountFormatter.plain(minorUnits: 1001, currency: "JOD")
        XCTAssertEqual(shown, "1.001")
        XCTAssertEqual(AmountFormatter.minorUnits(Double(shown)!, currency: "JOD"), 1001)
    }

    func testOutOfRangeAmountsAreRejectedNotTrapped() {
        XCTAssertNil(AmountFormatter.minorUnits(.infinity, currency: "USD"))
        XCTAssertNil(AmountFormatter.minorUnits(.nan, currency: "USD"))
        XCTAssertNil(AmountFormatter.minorUnits(1e17, currency: "JOD"))
        XCTAssertEqual(AmountFormatter.minorUnits(wholeUnits: "250", currency: "JOD"), 250_000)
        XCTAssertEqual(AmountFormatter.minorUnits(wholeUnits: "9223372036854775807", currency: "JOD"), 0)
        XCTAssertEqual(AmountFormatter.minorUnits(wholeUnits: "abc", currency: "USD"), 0)
    }
}
