import Shared
import SwiftUI

struct TransactionDetailSheet: View {
    let item: SharedRecentTransactionItem

    var body: some View {
        VStack(alignment: .leading, spacing: AppSpacing.md) {
            HStack(spacing: AppSpacing.sm) {
                Image(systemName: AppColors.transactionIcon(for: item.transactionType))
                    .font(.title2)
                    .foregroundStyle(AppColors.transactionColor(for: item.transactionType))
                Text("Transaction Details")
                    .font(AppTypography.headline)
            }

            Divider()

            VStack(alignment: .leading, spacing: AppSpacing.sm) {
                detailRow("Merchant", item.merchantName)

                HStack(spacing: AppSpacing.xs) {
                    Text("Amount")
                        .foregroundStyle(.secondary)
                    Spacer()
                    AmountText(
                        amountMinor: item.amountMinor,
                        currency: item.currency,
                        transactionType: item.transactionType,
                        font: AppTypography.amountMedium
                    )
                }

                detailRow("Type", item.transactionType)
                detailRow("Category", item.category)
                detailRow("Date", Date(epochMillis: item.occurredAtEpochMillis).formatted())

                if let note = item.note, !note.isEmpty {
                    detailRow("Note", note)
                }
                if let bank = item.bankName, !bank.isEmpty {
                    detailRow("Bank", bank)
                }
                if let account = item.accountLast4, !account.isEmpty {
                    detailRow("Account", "\u{2022}\u{2022}\(account)")
                }
            }

            Spacer()
        }
        .padding(AppSpacing.lg)
    }

    @ViewBuilder
    private func detailRow(_ label: String, _ value: String) -> some View {
        HStack {
            Text(label)
                .foregroundStyle(.secondary)
            Spacer()
            Text(value)
        }
    }
}
