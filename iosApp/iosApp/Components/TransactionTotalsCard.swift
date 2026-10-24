import Shared
import SwiftUI

/// Income / Expenses / Net in the display currency. Currencies with no stored
/// rate show as their own small lines under each figure.
struct TransactionTotalsCard: View {
    let income: ConvertedTotal
    let expense: ConvertedTotal
    let net: ConvertedTotal

    @Environment(\.isAmoledActive) private var isAmoled

    var body: some View {
        // Stacks when the three figures don't fit side by side at full size
        // (large Dynamic Type), so they can't run into each other.
        ViewThatFits(in: .horizontal) {
            HStack(alignment: .top, spacing: AppSpacing.md) { columns }
            VStack(spacing: AppSpacing.sm) { columns }
        }
        .padding(AppSpacing.sm)
        .background(AppColors.surface(isAmoled: isAmoled))
        .clipShape(RoundedRectangle(cornerRadius: AppCornerRadius.large))
    }

    @ViewBuilder
    private var columns: some View {
        TotalColumn(icon: "arrow.down", label: "Income", total: income, color: AppColors.income)
        TotalColumn(icon: "arrow.up", label: "Expenses", total: expense, color: AppColors.expense)
        TotalColumn(
            icon: "plus.forwardslash.minus",
            label: "Net",
            total: net,
            color: net.amountMinor >= 0 ? AppColors.income : AppColors.expense,
            prefix: net.amountMinor > 0 ? "+" : ""
        )
    }
}

private struct TotalColumn: View {
    let icon: String
    let label: String
    let total: ConvertedTotal
    let color: Color
    var prefix: String = ""

    var body: some View {
        VStack(spacing: AppSpacing.xs) {
            HStack(spacing: 4) {
                Image(systemName: icon)
                    .font(.caption2)
                    .foregroundStyle(color)
                Text(label)
                    .font(AppTypography.caption2)
                    .foregroundStyle(.secondary)
            }

            ConvertedAmountText(
                total: total,
                font: AppTypography.amountSmall.weight(.semibold),
                color: color,
                alignment: .center,
                prefix: prefix
            )
        }
        .fixedSize()
        .frame(maxWidth: .infinity)
    }
}
