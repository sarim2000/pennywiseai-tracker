import SwiftUI

struct CurrencyPickerScreen: View {
    @ObservedObject private var currencyManager = CurrencyManager.shared
    @State private var searchText = ""
    @Environment(\.dismiss) private var dismiss

    private var filteredCurrencies: [CurrencyInfo] {
        if searchText.isEmpty { return Self.currencies }
        let query = searchText.lowercased()
        return Self.currencies.filter {
            $0.code.lowercased().contains(query) ||
            $0.name.lowercased().contains(query) ||
            $0.symbol.lowercased().contains(query)
        }
    }

    var body: some View {
        List(filteredCurrencies) { currency in
            Button {
                currencyManager.displayCurrency = currency.code
                dismiss()
            } label: {
                HStack(spacing: AppSpacing.md) {
                    Text(currency.symbol)
                        .font(.title3)
                        .frame(width: 32)

                    VStack(alignment: .leading, spacing: AppSpacing.xs) {
                        Text(currency.code)
                            .font(AppTypography.body)
                            .foregroundStyle(.primary)
                        Text(currency.name)
                            .font(AppTypography.caption)
                            .foregroundStyle(.secondary)
                    }

                    Spacer()

                    if currencyManager.displayCurrency == currency.code {
                        Image(systemName: "checkmark.circle.fill")
                            .foregroundStyle(.blue)
                    }
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        }
        .searchable(text: $searchText, prompt: "Search currencies")
        .navigationTitle("Currency")
        .navigationBarTitleDisplayMode(.inline)
    }
}

// MARK: - Currency Data

struct CurrencyInfo: Identifiable {
    var id: String { code }
    let code: String
    let name: String
    let symbol: String
}

extension CurrencyPickerScreen {
    static let currencies: [CurrencyInfo] = [
        CurrencyInfo(code: "INR", name: "Indian Rupee", symbol: "\u{20B9}"),
        CurrencyInfo(code: "USD", name: "US Dollar", symbol: "$"),
        CurrencyInfo(code: "EUR", name: "Euro", symbol: "\u{20AC}"),
        CurrencyInfo(code: "GBP", name: "British Pound", symbol: "\u{00A3}"),
        CurrencyInfo(code: "AED", name: "UAE Dirham", symbol: "AED"),
        CurrencyInfo(code: "SGD", name: "Singapore Dollar", symbol: "S$"),
        CurrencyInfo(code: "CAD", name: "Canadian Dollar", symbol: "C$"),
        CurrencyInfo(code: "AUD", name: "Australian Dollar", symbol: "A$"),
        CurrencyInfo(code: "JPY", name: "Japanese Yen", symbol: "\u{00A5}"),
        CurrencyInfo(code: "CNY", name: "Chinese Yuan", symbol: "\u{00A5}"),
        CurrencyInfo(code: "KRW", name: "South Korean Won", symbol: "\u{20A9}"),
        CurrencyInfo(code: "PKR", name: "Pakistani Rupee", symbol: "Rs"),
        CurrencyInfo(code: "NPR", name: "Nepalese Rupee", symbol: "\u{20A8}"),
        CurrencyInfo(code: "THB", name: "Thai Baht", symbol: "\u{0E3F}"),
        CurrencyInfo(code: "MYR", name: "Malaysian Ringgit", symbol: "RM"),
        CurrencyInfo(code: "KWD", name: "Kuwaiti Dinar", symbol: "KD"),
        CurrencyInfo(code: "BDT", name: "Bangladeshi Taka", symbol: "\u{09F3}"),
        CurrencyInfo(code: "LKR", name: "Sri Lankan Rupee", symbol: "Rs"),
        CurrencyInfo(code: "IDR", name: "Indonesian Rupiah", symbol: "Rp"),
        CurrencyInfo(code: "PHP", name: "Philippine Peso", symbol: "\u{20B1}"),
        CurrencyInfo(code: "VND", name: "Vietnamese Dong", symbol: "\u{20AB}"),
        CurrencyInfo(code: "SAR", name: "Saudi Riyal", symbol: "SAR"),
        CurrencyInfo(code: "QAR", name: "Qatari Riyal", symbol: "QAR"),
        CurrencyInfo(code: "OMR", name: "Omani Rial", symbol: "OMR"),
        CurrencyInfo(code: "BHD", name: "Bahraini Dinar", symbol: "BHD"),
        CurrencyInfo(code: "ZAR", name: "South African Rand", symbol: "R"),
        CurrencyInfo(code: "BRL", name: "Brazilian Real", symbol: "R$"),
        CurrencyInfo(code: "MXN", name: "Mexican Peso", symbol: "MX$"),
        CurrencyInfo(code: "CHF", name: "Swiss Franc", symbol: "CHF"),
        CurrencyInfo(code: "SEK", name: "Swedish Krona", symbol: "kr"),
        CurrencyInfo(code: "NOK", name: "Norwegian Krone", symbol: "kr"),
        CurrencyInfo(code: "DKK", name: "Danish Krone", symbol: "kr"),
        CurrencyInfo(code: "NZD", name: "New Zealand Dollar", symbol: "NZ$"),
        CurrencyInfo(code: "HKD", name: "Hong Kong Dollar", symbol: "HK$"),
        CurrencyInfo(code: "TWD", name: "Taiwan Dollar", symbol: "NT$"),
        CurrencyInfo(code: "TRY", name: "Turkish Lira", symbol: "\u{20BA}"),
        CurrencyInfo(code: "RUB", name: "Russian Ruble", symbol: "\u{20BD}"),
        CurrencyInfo(code: "PLN", name: "Polish Zloty", symbol: "z\u{0142}"),
        CurrencyInfo(code: "CZK", name: "Czech Koruna", symbol: "K\u{010D}"),
        CurrencyInfo(code: "HUF", name: "Hungarian Forint", symbol: "Ft"),
        CurrencyInfo(code: "EGP", name: "Egyptian Pound", symbol: "EGP"),
        CurrencyInfo(code: "KES", name: "Kenyan Shilling", symbol: "KSh"),
        CurrencyInfo(code: "NGN", name: "Nigerian Naira", symbol: "\u{20A6}"),
        CurrencyInfo(code: "GHS", name: "Ghanaian Cedi", symbol: "GH\u{20B5}"),
        CurrencyInfo(code: "ETB", name: "Ethiopian Birr", symbol: "ETB"),
    ]
}
