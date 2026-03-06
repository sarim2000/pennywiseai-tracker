import SwiftUI

struct TopMerchantsList: View {
    @ObservedObject private var currencyManager = CurrencyManager.shared
    let merchants: [MerchantRankingItem]

    var body: some View {
        VStack(alignment: .leading, spacing: AppSpacing.sm) {
            Text("Top Merchants")
                .font(AppTypography.headline)

            if merchants.isEmpty {
                Text("No merchant data to show")
                    .font(AppTypography.caption)
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity, minHeight: 60)
            } else {
                ForEach(Array(merchants.enumerated()), id: \.element.id) { index, merchant in
                    merchantRow(merchant, rank: index + 1)
                }
            }
        }
        .padding(AppSpacing.md)
        .background(Color(.secondarySystemGroupedBackground))
        .clipShape(RoundedRectangle(cornerRadius: AppCornerRadius.medium))
    }

    private func merchantRow(_ merchant: MerchantRankingItem, rank: Int) -> some View {
        HStack(spacing: AppSpacing.sm) {
            Text("\(rank)")
                .font(AppTypography.caption)
                .foregroundStyle(.secondary)
                .frame(width: 20)

            VStack(alignment: .leading, spacing: 2) {
                Text(merchant.name)
                    .font(AppTypography.body)
                    .lineLimit(1)
                Text("\(merchant.count) \(merchant.count == 1 ? "transaction" : "transactions")")
                    .font(AppTypography.caption2)
                    .foregroundStyle(.secondary)
            }

            Spacer()

            Text(AmountFormatter.format(minorUnits: merchant.totalMinor, currency: CurrencyManager.shared.displayCurrency))
                .font(AppTypography.amountSmall)
        }
        .padding(.vertical, AppSpacing.xs)
    }
}
