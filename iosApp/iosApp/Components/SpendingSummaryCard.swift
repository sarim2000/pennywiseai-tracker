import SwiftUI

struct SpendingSummaryCard: View {
    @ObservedObject private var currencyManager = CurrencyManager.shared
    let monthlyExpenseMinor: Int64
    let monthlyIncomeMinor: Int64
    let monthlyNetMinor: Int64

    var body: some View {
        VStack(alignment: .leading, spacing: AppSpacing.md) {
            // Hero: "Spent this month"
            VStack(alignment: .leading, spacing: AppSpacing.xs) {
                Text("Spent this month")
                    .font(AppTypography.caption)
                    .foregroundStyle(.secondary)

                AmountText(
                    amountMinor: monthlyExpenseMinor,
                    currency: CurrencyManager.shared.displayCurrency,
                    font: AppTypography.amountLarge
                )
                .lineLimit(1)
                .minimumScaleFactor(0.5)
            }

            Divider()

            // Breakdown: Income | Expenses | Saved. Stacks vertically when the
            // row doesn't fit at full size (large Dynamic Type), instead of
            // splitting a figure across two lines.
            ViewThatFits(in: .horizontal) {
                HStack(spacing: 0) {
                    incomeColumn
                    Spacer(minLength: AppSpacing.sm)
                    expenseColumn
                    Spacer(minLength: AppSpacing.sm)
                    savedColumn
                }
                VStack(alignment: .leading, spacing: AppSpacing.sm) {
                    incomeColumn
                    expenseColumn
                    savedColumn
                }
            }
        }
        .padding(AppSpacing.md)
        .background(.ultraThinMaterial)
        .clipShape(RoundedRectangle(cornerRadius: AppCornerRadius.large))
    }

    private var incomeColumn: some View {
        SummaryColumn(label: "Income", amountMinor: monthlyIncomeMinor, accentColor: AppColors.income)
    }

    private var expenseColumn: some View {
        SummaryColumn(label: "Expenses", amountMinor: monthlyExpenseMinor, accentColor: AppColors.expense)
    }

    private var savedColumn: some View {
        SummaryColumn(
            label: "Saved",
            amountMinor: monthlyNetMinor,
            accentColor: monthlyNetMinor >= 0 ? AppColors.income : AppColors.expense
        )
    }
}

private struct SummaryColumn: View {
    @ObservedObject private var currencyManager = CurrencyManager.shared
    let label: String
    let amountMinor: Int64
    let accentColor: Color

    var body: some View {
        HStack(spacing: AppSpacing.sm) {
            RoundedRectangle(cornerRadius: 2)
                .fill(accentColor)
                .frame(width: 3, height: 32)

            VStack(alignment: .leading, spacing: 2) {
                Text(AmountFormatter.format(minorUnits: amountMinor, currency: CurrencyManager.shared.displayCurrency))
                    .font(AppTypography.amountSmall)
                    .foregroundStyle(accentColor)
                    .lineLimit(1)
                Text(label)
                    .font(AppTypography.caption2)
                    .foregroundStyle(.secondary)
            }
        }
    }
}
