import Shared
import SwiftUI

struct AmountText: View {
    let amountMinor: Int64
    let currency: String
    var transactionType: String? = nil
    var font: Font = AppTypography.amountSmall

    var body: some View {
        Text(formattedAmount)
            .font(font)
            .foregroundStyle(amountColor)
    }

    private var formattedAmount: String {
        let base = AmountFormatter.format(minorUnits: amountMinor, currency: currency)
        guard let type = transactionType else { return base }
        let prefix = AppColors.transactionPrefix(for: type)
        return "\(prefix)\(base)"
    }

    private var amountColor: Color {
        guard let type = transactionType else { return .primary }
        return AppColors.transactionColor(for: type)
    }
}

extension ConvertedTotal {
    /// One "+ $12.50" line per currency with no stored rate into `currency`.
    /// These are shown next to the total, never added into it.
    func unconvertedLines(negated: Bool = false) -> [String] {
        unconverted.map { total in
            let value = negated ? -total.amountMinor : total.amountMinor
            let sign = value < 0 ? "\u{2212} " : "+ "
            return sign + CurrencyFormatter.format(amountMinor: abs(value), currencyCode: total.currency)
        }
    }
}

/// A total converted into the display currency, plus a small line for each
/// currency that couldn't be converted (no stored rate).
struct ConvertedAmountText: View {
    let total: ConvertedTotal
    var font: Font = AppTypography.amountSmall
    var color: Color = .primary
    var alignment: HorizontalAlignment = .leading
    /// Shows the figure with its sign flipped ("Overspent ₹500" for a -₹500 net).
    var negated = false
    var prefix = ""

    var body: some View {
        VStack(alignment: alignment, spacing: 0) {
            Text(prefix + AmountFormatter.format(
                minorUnits: negated ? -total.amountMinor : total.amountMinor,
                currency: total.currency
            ))
            .font(font)
            .foregroundStyle(color)
            .lineLimit(1)
            ForEach(total.unconvertedLines(negated: negated), id: \.self) { line in
                Text(line)
                    .font(AppTypography.caption2)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
        }
    }
}
