import SwiftUI

struct AnalyticsSummaryCard: View {
    let summary: AnalyticsSummaryData

    var body: some View {
        VStack(alignment: .leading, spacing: AppSpacing.md) {
            // Total spending
            VStack(alignment: .leading, spacing: AppSpacing.xs) {
                Text("TOTAL SPENDING")
                    .font(AppTypography.caption2)
                    .foregroundStyle(.secondary)
                    .tracking(1)

                Text(AmountFormatter.format(minorUnits: summary.totalSpendingMinor, currency: CurrencyManager.shared.displayCurrency))
                    .font(AppTypography.amountLarge)
                    .foregroundStyle(.primary)
            }

            Divider()

            HStack(spacing: AppSpacing.lg) {
                // Transaction count
                VStack(alignment: .leading, spacing: AppSpacing.xs) {
                    Text("TRANSACTIONS")
                        .font(AppTypography.caption2)
                        .foregroundStyle(.secondary)
                        .tracking(1)
                    HStack(spacing: AppSpacing.xs) {
                        Image(systemName: "list.bullet.rectangle")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        Text("\(summary.transactionCount)")
                            .font(AppTypography.amountMedium)
                    }
                }

                Spacer()

                // Daily average
                VStack(alignment: .leading, spacing: AppSpacing.xs) {
                    Text("DAILY AVG")
                        .font(AppTypography.caption2)
                        .foregroundStyle(.secondary)
                        .tracking(1)
                    Text(AmountFormatter.format(minorUnits: summary.dailyAverageMinor, currency: CurrencyManager.shared.displayCurrency))
                        .font(AppTypography.amountMedium)
                }

                Spacer()

                // Top category
                if let topCategory = summary.topCategoryName {
                    VStack(alignment: .trailing, spacing: AppSpacing.xs) {
                        Text("TOP CATEGORY")
                            .font(AppTypography.caption2)
                            .foregroundStyle(.secondary)
                            .tracking(1)
                        HStack(spacing: AppSpacing.xs) {
                            Circle()
                                .fill(AppColors.categoryColor(for: topCategory))
                                .frame(width: 8, height: 8)
                            Text(topCategory)
                                .font(AppTypography.caption)
                                .fontWeight(.medium)
                                .lineLimit(1)
                        }
                    }
                }
            }
        }
        .padding(AppSpacing.md)
        .background(Color(.secondarySystemGroupedBackground))
        .clipShape(RoundedRectangle(cornerRadius: AppCornerRadius.medium))
    }
}
