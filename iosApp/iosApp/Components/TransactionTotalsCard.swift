import SwiftUI

struct TransactionTotalsCard: View {
    let incomeMinor: Int64
    let expenseMinor: Int64
    let netMinor: Int64
    let currency: String

    @Environment(\.isAmoledActive) private var isAmoled

    var body: some View {
        // Stacks when the three figures don't fit side by side at full size
        // (large Dynamic Type), so they can't run into each other.
        ViewThatFits(in: .horizontal) {
            HStack(spacing: AppSpacing.md) { columns }
            VStack(spacing: AppSpacing.sm) { columns }
        }
        .padding(AppSpacing.sm)
        .background(AppColors.surface(isAmoled: isAmoled))
        .clipShape(RoundedRectangle(cornerRadius: AppCornerRadius.large))
    }

    @ViewBuilder
    private var columns: some View {
        TotalColumn(
            icon: "arrow.down",
            label: "Income",
            amountMinor: incomeMinor,
            currency: currency,
            color: AppColors.income
        )

        TotalColumn(
            icon: "arrow.up",
            label: "Expenses",
            amountMinor: expenseMinor,
            currency: currency,
            color: AppColors.expense
        )

        TotalColumn(
            icon: "plus.forwardslash.minus",
            label: "Net",
            amountMinor: netMinor,
            currency: currency,
            color: netMinor >= 0 ? AppColors.income : AppColors.expense,
            prefix: netMinor > 0 ? "+" : ""
        )
    }
}

private struct TotalColumn: View {
    let icon: String
    let label: String
    let amountMinor: Int64
    let currency: String
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

            Text("\(prefix)\(AmountFormatter.format(minorUnits: amountMinor, currency: currency))")
                .font(AppTypography.amountSmall)
                .fontWeight(.semibold)
                .foregroundStyle(color)
                .lineLimit(1)
        }
        .fixedSize()
        .frame(maxWidth: .infinity)
    }
}
