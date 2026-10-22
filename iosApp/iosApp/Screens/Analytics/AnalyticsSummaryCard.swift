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
                        .lineLimit(1)
                        .minimumScaleFactor(0.5)

                    if let trend = summary.spendingTrendPct {
                        trendBadge(trend)
                    }
                }
            }

            Divider()

            // Income / Expenses / Net — mirrors the Android summary row.
            // ViewThatFits measures at full size, so the row stacks (rather
            // than wrapping mid-number or shrinking one figure more than the
            // others) when it doesn't fit: SE, or large Dynamic Type.
            ViewThatFits(in: .horizontal) {
                HStack(alignment: .top, spacing: AppSpacing.md) { totalsColumns }
                VStack(alignment: .leading, spacing: AppSpacing.sm) { totalsColumns }
            }

            Divider()

            ViewThatFits(in: .horizontal) {
                HStack(alignment: .top, spacing: AppSpacing.md) { statsColumns }
                VStack(alignment: .leading, spacing: AppSpacing.sm) { statsColumns }
            }
        }
        .padding(AppSpacing.md)
        .background(AppColors.secondaryGroupedBackground(isAmoled: isAmoled))
        .clipShape(RoundedRectangle(cornerRadius: AppCornerRadius.medium))
    }

    @ViewBuilder
    private var totalsColumns: some View {
        statColumn("Income") {
            amount(summary.incomeMinor, tint: .green)
        }
        statColumn("Expenses") {
            amount(summary.totalSpendingMinor, tint: .red)
        }
        statColumn("Net") {
            amount(summary.netMinor, tint: summary.netMinor >= 0 ? .green : .red)
        }
    }

    @ViewBuilder
    private var statsColumns: some View {
        statColumn("Transactions") {
            Text("\(summary.transactionCount)")
                .font(AppTypography.amountMedium)
        }
        statColumn("Daily avg") {
            amount(summary.dailyAverageMinor, tint: .primary)
        }
        if let topCategory = summary.topCategoryName {
            statColumn("Top category") {
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

    private func statColumn<Value: View>(_ label: String, @ViewBuilder value: () -> Value) -> some View {
        VStack(alignment: .leading, spacing: AppSpacing.xs) {
            Text(label)
                .font(AppTypography.caption2)
                .foregroundStyle(.secondary)
                .lineLimit(1)
            value()
        }
        .fixedSize()
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func amount(_ minor: Int64, tint: Color) -> some View {
        Text(AmountFormatter.format(minorUnits: minor, currency: currency))
            .font(AppTypography.amountMedium)
            .foregroundStyle(tint)
            .lineLimit(1)
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
