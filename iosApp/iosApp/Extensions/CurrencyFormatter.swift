import Foundation

enum CurrencyFormatter {
    // Currencies that use Indian grouping (lakhs/crores): X,XX,XXX.XX
    private static let indianGroupingCurrencies: Set<String> = ["INR", "NPR", "PKR"]

    // Zero-decimal currencies (no fractional units)
    private static let zeroDecimalCurrencies: Set<String> = ["JPY", "KRW", "VND", "CLP", "ISK", "UGX"]

    private static let currencySymbols: [String: String] = [
        "INR": "₹",
        "USD": "$",
        "EUR": "€",
        "GBP": "£",
        "JPY": "¥",
        "CNY": "¥",
        "KRW": "₩",
        "THB": "฿",
        "AED": "د.إ",
        "SAR": "﷼",
        "PKR": "₨",
        "NPR": "₨",
        "BDT": "৳",
        "LKR": "₨",
        "SGD": "S$",
        "MYR": "RM",
        "IDR": "Rp",
        "PHP": "₱",
        "VND": "₫",
        "TWD": "NT$",
        "HKD": "HK$",
        "CAD": "CA$",
        "AUD": "A$",
        "NZD": "NZ$",
        "CHF": "CHF",
        "SEK": "kr",
        "NOK": "kr",
        "DKK": "kr",
        "PLN": "zł",
        "CZK": "Kč",
        "HUF": "Ft",
        "RUB": "₽",
        "TRY": "₺",
        "BRL": "R$",
        "MXN": "MX$",
        "ZAR": "R",
        "KES": "KSh",
        "NGN": "₦",
        "EGP": "E£",
        "KWD": "د.ك",
        "BHD": "BD",
        "OMR": "OMR",
        "QAR": "QR",
        "ILS": "₪",
        "JOD": "JD"
    ]

    static func symbol(for currencyCode: String) -> String {
        if let known = currencySymbols[currencyCode] {
            return known
        }
        let formatter = NumberFormatter()
        formatter.numberStyle = .currency
        formatter.currencyCode = currencyCode
        formatter.locale = localeForCurrency(currencyCode)
        return formatter.currencySymbol ?? currencyCode
    }

    static func format(amountMinor: Int64, currencyCode: String) -> String {
        let isZeroDecimal = zeroDecimalCurrencies.contains(currencyCode)
        let amount: Double = isZeroDecimal
            ? Double(amountMinor)
            : Double(amountMinor) / 100.0

        if indianGroupingCurrencies.contains(currencyCode) {
            return formatIndian(amount: amount, currencyCode: currencyCode, isZeroDecimal: isZeroDecimal)
        }

        let formatter = NumberFormatter()
        formatter.numberStyle = .currency
        formatter.currencyCode = currencyCode
        formatter.locale = localeForCurrency(currencyCode)
        formatter.currencySymbol = symbol(for: currencyCode)
        formatter.minimumFractionDigits = isZeroDecimal ? 0 : 0
        formatter.maximumFractionDigits = isZeroDecimal ? 0 : 2

        // Drop trailing .00
        if !isZeroDecimal {
            let fraction = abs(amountMinor) % 100
            if fraction == 0 {
                formatter.minimumFractionDigits = 0
            }
        }

        return formatter.string(from: NSNumber(value: amount)) ?? "\(currencyCode) \(amount)"
    }

    static func formatAbbreviated(amount: Double, currencyCode: String) -> String {
        let sym = symbol(for: currencyCode)
        let absValue = abs(amount)
        let useIndian = indianGroupingCurrencies.contains(currencyCode)

        switch (useIndian, absValue) {
        case (true, let v) where v >= 1_00_00_000:
            return "\(sym)\(String(format: "%.1f", v / 1_00_00_000))Cr"
        case (true, let v) where v >= 1_00_000:
            return "\(sym)\(String(format: "%.1f", v / 1_00_000))L"
        case (_, let v) where v >= 10_000_000:
            return "\(sym)\(String(format: "%.1f", v / 1_000_000))M"
        case (_, let v) where v >= 1_000:
            return "\(sym)\(String(format: "%.1f", v / 1_000))K"
        case (_, let v) where v > 0:
            return "\(sym)\(Int(v))"
        default:
            return "\(sym)0"
        }
    }

    // MARK: - Indian Notation

    private static func formatIndian(amount: Double, currencyCode: String, isZeroDecimal: Bool) -> String {
        let sym = symbol(for: currencyCode)
        let isNegative = amount < 0
        let absAmount = abs(amount)

        let integerPart = Int64(absAmount)
        let fractionPart = Int(((absAmount - Double(integerPart)) * 100).rounded())

        let intStr = formatIndianGrouping(integerPart)

        let prefix = isNegative ? "-" : ""
        if isZeroDecimal || fractionPart == 0 {
            return "\(prefix)\(sym)\(intStr)"
        }
        return "\(prefix)\(sym)\(intStr).\(String(format: "%02d", fractionPart))"
    }

    private static func formatIndianGrouping(_ value: Int64) -> String {
        if value < 1000 {
            return "\(value)"
        }
        let lastThree = value % 1000
        var remaining = value / 1000
        var parts: [String] = [String(format: "%03d", lastThree)]
        while remaining > 0 {
            let group = remaining % 100
            parts.insert(remaining < 100 ? "\(group)" : String(format: "%02d", group), at: 0)
            remaining /= 100
        }
        return parts.joined(separator: ",")
    }

    // MARK: - Locale Mapping

    private static func localeForCurrency(_ code: String) -> Locale {
        switch code {
        case "INR": return Locale(identifier: "en_IN")
        case "USD": return Locale(identifier: "en_US")
        case "EUR": return Locale(identifier: "de_DE")
        case "GBP": return Locale(identifier: "en_GB")
        case "AED": return Locale(identifier: "en_AE")
        case "SGD": return Locale(identifier: "en_SG")
        case "CAD": return Locale(identifier: "en_CA")
        case "AUD": return Locale(identifier: "en_AU")
        case "JPY": return Locale(identifier: "ja_JP")
        case "CNY": return Locale(identifier: "zh_CN")
        case "PKR": return Locale(identifier: "en_PK")
        case "NPR": return Locale(identifier: "ne_NP")
        case "KRW": return Locale(identifier: "ko_KR")
        case "THB": return Locale(identifier: "th_TH")
        case "MYR": return Locale(identifier: "ms_MY")
        case "KWD": return Locale(identifier: "en_KW")
        default: return Locale(identifier: "en_US")
        }
    }
}
