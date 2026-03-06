import Shared
import SwiftUI

struct TransactionRow: View {
    let item: SharedRecentTransactionItem
    var onViewDetails: (() -> Void)?
    var onEdit: (() -> Void)?

    private var iconInfo: CategoryIconInfo {
        AppColors.categoryIcon(for: item.category)
    }

    var body: some View {
        HStack(alignment: .center, spacing: AppSpacing.md) {
            ZStack {
                Circle()
                    .fill(iconInfo.color.opacity(0.15))
                    .frame(width: 42, height: 42)
                Image(systemName: iconInfo.systemName)
                    .font(.system(size: 18))
                    .foregroundColor(iconInfo.color)
            }

            VStack(alignment: .leading, spacing: AppSpacing.xs) {
                Text(item.merchantName)
                    .font(AppTypography.body)
                    .lineLimit(1)
                HStack(spacing: AppSpacing.xs) {
                    Text(item.category)
                        .font(AppTypography.caption2)
                        .foregroundStyle(.secondary)
                    Text("\u{00B7}")
                        .foregroundStyle(.secondary)
                    Text(Date(epochMillis: item.occurredAtEpochMillis).formatted(as: "dd MMM"))
                        .font(AppTypography.caption2)
                        .foregroundStyle(.secondary)
                }
            }

            Spacer()

            AmountText(
                amountMinor: item.amountMinor,
                currency: item.currency,
                transactionType: item.transactionType
            )

            if onViewDetails != nil || onEdit != nil {
                Menu {
                    if let onViewDetails {
                        Button("View Details", action: onViewDetails)
                    }
                    if let onEdit {
                        Button("Edit", action: onEdit)
                    }
                } label: {
                    Image(systemName: "ellipsis.circle")
                        .foregroundStyle(.secondary)
                }
            }
        }
        .padding(AppSpacing.md)
        .background(Color(.secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: AppCornerRadius.medium))
    }
}
