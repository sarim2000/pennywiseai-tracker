import Shared
import SwiftUI

struct AccountCarousel: View {
    let accounts: [SharedAccountItem]
    @ObservedObject private var currencyManager = CurrencyManager.shared

    var body: some View {
        VStack(alignment: .leading, spacing: AppSpacing.sm) {
            Text("Accounts")
                .font(AppTypography.headline)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: AppSpacing.md) {
                    ForEach(Array(accounts.enumerated()), id: \.offset) { _, account in
                        AccountCarouselCard(account: account)
                    }
                }
            }
        }
    }
}

private struct AccountCarouselCard: View {
    let account: SharedAccountItem
    @ObservedObject private var currencyManager = CurrencyManager.shared
    @State private var isAmountHidden = true

    private var accountTypeIcon: String {
        if account.isCreditCard { return "creditcard.fill" }
        switch account.accountType?.uppercased() {
        case "SAVINGS": return "banknote.fill"
        case "CURRENT": return "building.columns.fill"
        default: return "banknote.fill"
        }
    }

    private var accountTypeLabel: String {
        if account.isCreditCard { return "Credit" }
        return account.accountType?.capitalized ?? "Savings"
    }

    var body: some View {
        VStack(alignment: .leading, spacing: AppSpacing.sm) {
            HStack(spacing: AppSpacing.sm) {
                Image(systemName: accountTypeIcon)
                    .font(.caption)
                    .foregroundStyle(.secondary)

                Text(accountTypeLabel)
                    .font(AppTypography.caption2)
                    .foregroundStyle(.secondary)
            }

            Text(account.bankName)
                .font(AppTypography.caption)
                .fontWeight(.semibold)
                .lineLimit(1)

            if !account.accountLast4.isEmpty {
                Text("••\(account.accountLast4)")
                    .font(AppTypography.caption2)
                    .foregroundStyle(.tertiary)
            }

            Spacer()

            HStack(spacing: 4) {
                Text(isAmountHidden
                    ? "••••••"
                    : AmountFormatter.format(minorUnits: account.balanceMinor, currency: currencyManager.displayCurrency))
                    .font(AppTypography.amountSmall)
                    .lineLimit(1)

                Button {
                    withAnimation(.easeInOut(duration: 0.2)) {
                        isAmountHidden.toggle()
                    }
                } label: {
                    Image(systemName: isAmountHidden ? "eye.slash.fill" : "eye.fill")
                        .font(.caption2)
                        .foregroundStyle(.tertiary)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(AppSpacing.md)
        .frame(width: 160, height: 140)
        .background(.ultraThinMaterial)
        .clipShape(RoundedRectangle(cornerRadius: AppCornerRadius.large))
        .shadow(color: .black.opacity(0.08), radius: 4, x: 0, y: 2)
    }
}
