import Shared
import SwiftUI

struct SpendingSummaryCard: View {
    /// Totals in the display currency (nil before the first load).
    let expense: ConvertedTotal?
    let income: ConvertedTotal?
    let net: ConvertedTotal?

    var body: some View {
        VStack(alignment: .leading, spacing: AppSpacing.md) {
            // Hero: "Spent this month"
            VStack(alignment: .leading, spacing: AppSpacing.xs) {
                Text("Spent this month")
                    .font(AppTypography.caption)
                    .foregroundStyle(.secondary)

                if let expense {
                    ConvertedAmountText(total: expense, font: AppTypography.amountLarge)
                        .minimumScaleFactor(0.5)
                }
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

    @ViewBuilder
    private var incomeColumn: some View {
        if let income {
            SummaryColumn(label: "Income", total: income, accentColor: AppColors.income)
        }
    }

    @ViewBuilder
    private var expenseColumn: some View {
        if let expense {
            SummaryColumn(label: "Expenses", total: expense, accentColor: AppColors.expense)
        }
    }

    /// "Overspent ₹500" rather than "Saved -₹500" when spending beats income.
    @ViewBuilder
    private var savedColumn: some View {
        if let net {
            let overspent = net.amountMinor < 0
            SummaryColumn(
                label: overspent ? "Overspent" : "Saved",
                total: net,
                accentColor: overspent ? AppColors.expense : AppColors.income,
                negated: overspent
            )
        }
    }
}

private struct SummaryColumn: View {
    let label: String
    let total: ConvertedTotal
    let accentColor: Color
    var negated = false

    var body: some View {
        HStack(spacing: AppSpacing.sm) {
            RoundedRectangle(cornerRadius: 2)
                .fill(accentColor)
                .frame(width: 3, height: 32)

            VStack(alignment: .leading, spacing: 2) {
                ConvertedAmountText(total: total, color: accentColor, negated: negated)
                Text(label)
                    .font(AppTypography.caption2)
                    .foregroundStyle(.secondary)
            }
        }
    }
}
