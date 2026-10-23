import Shared
import SwiftUI

struct BudgetCarousel: View {
    let budgets: [SharedBudgetItem]
    @ObservedObject private var currencyManager = CurrencyManager.shared

    var body: some View {
        VStack(alignment: .leading, spacing: AppSpacing.sm) {
            Text("Budgets")
                .font(AppTypography.headline)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(alignment: .top, spacing: AppSpacing.md) {
                    ForEach(budgets, id: \.id) { budget in
                        BudgetCarouselCard(budget: budget)
                    }
                }
            }
        }
    }
}

private struct BudgetCarouselCard: View {
    let budget: SharedBudgetItem
    @ObservedObject private var currencyManager = CurrencyManager.shared

    private var spentRatio: Double {
        guard budget.limitMinor > 0 else { return 0 }
        return Double(budget.spentMinor) / Double(budget.limitMinor)
    }

    /// Same rounding as the budget screens (computed once in shared).
    private var percentage: Int {
        Int(budget.percentUsed)
    }

    private var progressColor: Color {
        if spentRatio < 0.75 { return AppColors.budgetSafe }
        if spentRatio < 0.90 { return AppColors.budgetWarning }
        return AppColors.budgetDanger
    }

    private var remainingMinor: Int64 {
        budget.limitMinor - budget.spentMinor
    }

    private var isOver: Bool { remainingMinor < 0 }

    var body: some View {
        VStack(alignment: .leading, spacing: AppSpacing.sm) {
            Text(budget.name)
                .font(AppTypography.caption)
                .foregroundStyle(.secondary)
                .lineLimit(1)

            // Progress bar
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    RoundedRectangle(cornerRadius: 4)
                        .fill(Color.primary.opacity(0.1))
                        .frame(height: 6)

                    RoundedRectangle(cornerRadius: 4)
                        .fill(progressColor)
                        .frame(width: geo.size.width * min(spentRatio, 1.0), height: 6)
                }
            }
            .frame(height: 6)

            Text("\(percentage)% used")
                .font(AppTypography.caption2)
                .foregroundStyle(progressColor)

            if let note = budgetPeriodNote(periodType: budget.periodType) {
                Text(note)
                    .font(AppTypography.caption2)
                    .foregroundStyle(.tertiary)
                    .lineLimit(1)
            }

            Spacer()

            VStack(alignment: .leading, spacing: 2) {
                // "₹499 over" rather than "Remaining ₹0" once the limit is passed.
                Text(isOver ? "Over by" : "Remaining")
                    .font(AppTypography.caption2)
                    .foregroundStyle(.tertiary)

                Text(AmountFormatter.format(minorUnits: abs(remainingMinor), currency: budget.currency))
                    .font(AppTypography.amountSmall)
                    .foregroundStyle(isOver ? AppColors.budgetDanger : .primary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(AppSpacing.md)
        // Min height, not a fixed one: large Dynamic Type grows the card
        // instead of clipping the title and "Remaining".
        .frame(width: 160)
        .frame(minHeight: 140)
        .background(.ultraThinMaterial)
        .clipShape(RoundedRectangle(cornerRadius: AppCornerRadius.large))
        .shadow(color: .black.opacity(0.08), radius: 4, x: 0, y: 2)
    }
}
