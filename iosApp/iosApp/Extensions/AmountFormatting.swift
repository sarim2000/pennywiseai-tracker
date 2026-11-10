import Foundation
import Shared

enum AmountFormatter {
    /// Plain text for an edit field: "12.50", "1.235" for JOD — every stored digit, no symbol.
    static func plain(minorUnits: Int64, currency: String) -> String {
        MinorUnits.shared.toPlain(amountMinor: minorUnits, currency: currency)
    }

    /// A typed amount in `currency`'s minor units (× 1000 for 3-decimal currencies, else × 100),
    /// rounded — 1.001 JOD is 1001, never a truncated 1000 — or nil when it isn't a finite amount
    /// under 10^15 minor units, so `Int64()` can't trap on it.
    static func minorUnits(_ value: Double, currency: String) -> Int64? {
        let scaled = (value * Double(MinorUnits.shared.factor(currency: currency))).rounded()
        guard scaled.isFinite, abs(scaled) < 1e15 else { return nil }
        return Int64(scaled)
    }

    /// Whole units typed in a budget field ("250") in `currency`'s minor units; 0 when it isn't a
    /// number or would overflow.
    static func minorUnits(wholeUnits text: String, currency: String) -> Int64 {
        guard let units = Int64(text) else { return 0 }
        let (minor, overflow) = units.multipliedReportingOverflow(by: MinorUnits.shared.factor(currency: currency))
        return overflow ? 0 : minor
    }

    static func format(minorUnits: Int64, currency: String) -> String {
        CurrencyFormatter.format(amountMinor: minorUnits, currencyCode: currency)
    }
}
