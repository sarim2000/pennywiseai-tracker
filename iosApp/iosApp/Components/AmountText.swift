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
