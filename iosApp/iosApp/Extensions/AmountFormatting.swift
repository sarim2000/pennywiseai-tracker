import Foundation

enum AmountFormatter {
    static func format(minorUnits: Int64) -> String {
        let whole = minorUnits / 100
        let fraction = abs(minorUnits) % 100
        return "\(whole).\(String(format: "%02d", Int(fraction)))"
    }

    static func format(minorUnits: Int64, currency: String) -> String {
        "\(currency) \(format(minorUnits: minorUnits))"
    }
}
