import SwiftUI

/// Android-parity summary: spending hero with a trend-vs-previous-period
/// badge, then Income / Expenses / Net, then the secondary stats row.
struct AnalyticsSummaryCard: View {
    @ObservedObject private var currencyManager = CurrencyManager.shared
    let summary: AnalyticsSummaryData
    @Environment(\.isAmoledActive) private var isAmoled

    private var currency: String { currencyManager.displayCurrency }

    var body: some View {
        VStack(alignment: .leading, spacing: AppSpacing.md) {
            // Spending hero + trend vs previous period
            VStack(alignment: .leading, spacing: AppSpacing.xs) {
                Text("SPENT THIS PERIOD")
                    .font(AppTypography.caption2)
                    .foregroundStyle(.secondary)
                    .tracking(1)

                HStack(alignment: .firstTextBaseline, spacing: AppSpacing.sm) {
                    Text(AmountFormatter.format(minorUnits: summary.totalSpendingMinor, currency: currency))
                        .font(AppTypography.amountLarge)
                        .foregroundStyle(.primary)

                    if let trend = summary.spendingTrendPct {
                        trendBadge(trend)
                    }
                }
            }

            Divider()

            // Income / Expenses / Net — mirrors the Android summary row.
            HStack(spacing: AppSpacing.lg) {
                summaryColumn(
                    label: "INCOME",
                    amountMinor: summary.incomeMinor,
                    tint: .green
                )
                Spacer()
                summaryColumn(
                    label: "EXPENSES",
                    amountMinor: summary.totalSpendingMinor,
                    tint: .red
                )
                Spacer()
                summaryColumn(
                    label: "NET",
                    amountMinor: summary.netMinor,
                    tint: summary.netMinor >= 0 ? .green : .red
                )
            }

            Divider()

            HStack(spacing: AppSpacing.lg) {
                VStack(alignment: .leading, spacing: AppSpacing.xs) {
                    Text("TRANSACTIONS")
                        .font(AppTypography.caption2)
                        .foregroundStyle(.secondary)
                        .tracking(1)
                    Text("\(summary.transactionCount)")
                        .font(AppTypography.amountMedium)
                }

                Spacer()

                VStack(alignment: .leading, spacing: AppSpacing.xs) {
                    Text("DAILY AVG")
                        .font(AppTypography.caption2)
                        .foregroundStyle(.secondary)
                        .tracking(1)
                    Text(AmountFormatter.format(minorUnits: summary.dailyAverageMinor, currency: currency))
                        .font(AppTypography.amountMedium)
                }

                Spacer()

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
        .background(AppColors.secondaryGroupedBackground(isAmoled: isAmoled))
        .clipShape(RoundedRectangle(cornerRadius: AppCornerRadius.medium))
    }

    private func summaryColumn(label: String, amountMinor: Int64, tint: Color) -> some View {
        VStack(alignment: .leading, spacing: AppSpacing.xs) {
            Text(label)
                .font(AppTypography.caption2)
                .foregroundStyle(.secondary)
                .tracking(1)
            Text(AmountFormatter.format(minorUnits: amountMinor, currency: currency))
                .font(AppTypography.amountMedium)
                .foregroundStyle(tint)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
        }
    }

    /// "↑ 12%" in red for more spending, "↓ 8%" in green for less.
    private func trendBadge(_ pct: Int) -> some View {
        let up = pct >= 0
        return HStack(spacing: 2) {
            Image(systemName: up ? "arrow.up.right" : "arrow.down.right")
                .font(.caption2.bold())
            Text("\(abs(pct))%")
                .font(AppTypography.caption)
                .fontWeight(.semibold)
        }
        .foregroundStyle(up ? .red : .green)
        .padding(.horizontal, AppSpacing.sm)
        .padding(.vertical, 3)
        .background(Capsule().fill((up ? Color.red : Color.green).opacity(0.12)))
        .accessibilityLabel("\(abs(pct)) percent \(up ? "more" : "less") than the previous period")
    }
}
