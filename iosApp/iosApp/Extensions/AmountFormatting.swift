import Foundation
import Shared

enum AmountFormatter {
    /// Plain text for an edit field: "12.50", "1.235" for JOD — every stored digit, no symbol.
    static func plain(minorUnits: Int64, currency: String) -> String {
        MinorUnits.shared.toPlain(amountMinor: minorUnits, currency: currency)
    }

    /// A typed amount in [currency]'s minor units (× 1000 for 3-decimal currencies, else × 100).
    static func minorUnits(_ value: Double, currency: String) -> Double {
        (value * Double(MinorUnits.shared.factor(currency: currency))).rounded()
    }

    static func format(minorUnits: Int64, currency: String) -> String {
        CurrencyFormatter.format(amountMinor: minorUnits, currencyCode: currency)
    }
}
