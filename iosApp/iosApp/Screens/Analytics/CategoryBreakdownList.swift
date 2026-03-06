import SwiftUI

struct CategoryBreakdownList: View {
    @ObservedObject private var currencyManager = CurrencyManager.shared
    let categories: [CategoryBreakdownItem]

    var body: some View {
        VStack(alignment: .leading, spacing: AppSpacing.sm) {
            Text("Categories")
                .font(AppTypography.headline)

            if categories.isEmpty {
                Text("No categories to show")
                    .font(AppTypography.caption)
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity, minHeight: 60)
            } else {
                ForEach(categories) { item in
                    categoryRow(item)
                }
            }
        }
        .padding(AppSpacing.md)
        .background(Color(.secondarySystemGroupedBackground))
        .clipShape(RoundedRectangle(cornerRadius: AppCornerRadius.medium))
    }

    private func categoryRow(_ item: CategoryBreakdownItem) -> some View {
        HStack(spacing: AppSpacing.sm) {
            Circle()
                .fill(AppColors.categoryColor(for: item.name))
                .frame(width: 12, height: 12)

            VStack(alignment: .leading, spacing: 2) {
                Text(item.name)
                    .font(AppTypography.body)
                    .lineLimit(1)
                Text("\(item.count) transactions")
                    .font(AppTypography.caption2)
                    .foregroundStyle(.secondary)
            }

            Spacer()

            VStack(alignment: .trailing, spacing: 2) {
                Text(AmountFormatter.format(minorUnits: item.totalMinor, currency: CurrencyManager.shared.displayCurrency))
                    .font(AppTypography.amountSmall)
                Text(String(format: "%.1f%%", item.percentage))
                    .font(AppTypography.caption2)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(.vertical, AppSpacing.xs)
    }
}
