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
                HStack(spacing: AppSpacing.md) {
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

    private var percentage: Int {
        Int((spentRatio * 100).rounded())
    }

    private var progressColor: Color {
        if spentRatio < 0.75 { return AppColors.budgetSafe }
        if spentRatio < 0.90 { return AppColors.budgetWarning }
        return AppColors.budgetDanger
    }

    private var remainingMinor: Int64 {
        max(0, budget.limitMinor - budget.spentMinor)
    }

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

            Spacer()

            VStack(alignment: .leading, spacing: 2) {
                Text("Remaining")
                    .font(AppTypography.caption2)
                    .foregroundStyle(.tertiary)

                Text(AmountFormatter.format(minorUnits: remainingMinor, currency: currencyManager.displayCurrency))
                    .font(AppTypography.amountSmall)
                    .lineLimit(1)
            }
        }
        .padding(AppSpacing.md)
        .frame(width: 160, height: 140)
        .background(.ultraThinMaterial)
        .clipShape(RoundedRectangle(cornerRadius: AppCornerRadius.large))
        .shadow(color: .black.opacity(0.08), radius: 4, x: 0, y: 2)
    }
}
