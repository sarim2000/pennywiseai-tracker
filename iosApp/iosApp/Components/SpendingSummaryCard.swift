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
            }

            Divider()

            // Breakdown: Income | Expenses | Saved
            HStack(spacing: 0) {
                SummaryColumn(
                    label: "Income",
                    amountMinor: monthlyIncomeMinor,
                    accentColor: AppColors.income
                )
                Spacer()
                SummaryColumn(
                    label: "Expenses",
                    amountMinor: monthlyExpenseMinor,
                    accentColor: AppColors.expense
                )
                Spacer()
                SummaryColumn(
                    label: "Saved",
                    amountMinor: monthlyNetMinor,
                    accentColor: monthlyNetMinor >= 0 ? AppColors.income : AppColors.expense
                )
            }
        }
        .padding(AppSpacing.md)
        .background(.ultraThinMaterial)
        .clipShape(RoundedRectangle(cornerRadius: AppCornerRadius.large))
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
                Text(label)
                    .font(AppTypography.caption2)
                    .foregroundStyle(.secondary)
            }
        }
    }
}
