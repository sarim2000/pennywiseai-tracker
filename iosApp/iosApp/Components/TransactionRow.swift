import Shared
import SwiftUI

/// The one transaction row: a card on Home / Account detail, plain inside the
/// Transactions list. Callers wrap it in a Button or NavigationLink.
struct TransactionRow: View {
    let item: SharedRecentTransactionItem
    var isCard = true
    @Environment(\.isAmoledActive) private var isAmoled

    private var iconInfo: CategoryIconInfo {
        AppColors.categoryIcon(for: item.category)
    }

    /// "21 Oct", with the year only when it isn't this year.
    private var dateText: String {
        let date = Date(epochMillis: item.occurredAtEpochMillis)
        let thisYear = Calendar.current.isDate(date, equalTo: Date(), toGranularity: .year)
        return date.formatted(as: thisYear ? "dd MMM" : "dd MMM yyyy")
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
                    .foregroundStyle(.primary)
                    .lineLimit(1)
                HStack(spacing: AppSpacing.xs) {
                    Text(item.category)
                        .lineLimit(1)
                    Text("\u{00B7}")
                    Text(dateText)
                        .lineLimit(1)
                        .fixedSize()
                }
                .font(AppTypography.caption2)
                .foregroundStyle(.secondary)
            }

            Spacer()

            AmountText(
                amountMinor: item.amountMinor,
                currency: item.currency,
                transactionType: item.transactionType
            )
            // The amount wins the row's width: the merchant/category truncate
            // instead of the number wrapping mid-figure.
            .lineLimit(1)
            .minimumScaleFactor(0.7)
            .layoutPriority(1)
        }
        .padding(isCard ? AppSpacing.md : 0)
        .padding(.vertical, isCard ? 0 : AppSpacing.xs)
        .background(isCard ? AppColors.surface(isAmoled: isAmoled) : .clear)
        .clipShape(RoundedRectangle(cornerRadius: AppCornerRadius.medium))
        .contentShape(Rectangle())
    }
}
