import Shared
import SwiftUI

struct AccountDetailScreen: View {
    @ObservedObject private var currencyManager = CurrencyManager.shared
    @State var bankName: String
    let accountLast4: String
    @ObservedObject var viewModel: AccountsViewModel
    @State private var showEditSheet = false
    @State private var selectedTransaction: SharedRecentTransactionItem?
    @Environment(\.isAmoledActive) private var isAmoled

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: AppSpacing.lg) {
                balanceCard

                transactionsSection
            }
            .padding(.horizontal, AppSpacing.md)
            .padding(.top, AppSpacing.sm)
            .padding(.bottom, AppSpacing.xl)
        }
        .navigationTitle(bankName)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button {
                    showEditSheet = true
                } label: {
                    Image(systemName: "pencil")
                }
            }
        }
        .onAppear {
            viewModel.loadAccountTransactions(
                bankName: bankName,
                accountLast4: accountLast4
            )
        }
        .sheet(isPresented: $showEditSheet) {
            NavigationStack {
                AddEditAccountScreen(
                    viewModel: viewModel,
                    editBankName: bankName,
                    editAccountLast4: accountLast4,
                    editAccount: currentAccount,
                    onSave: {
                        viewModel.loadAccounts()
                        if let updated = viewModel.accounts.first(where: { $0.accountLast4 == accountLast4 }) {
                            bankName = updated.bankName
                        }
                        viewModel.loadAccountTransactions(
                            bankName: bankName,
                            accountLast4: accountLast4
                        )
                    }
                )
            }
        }
        .sheet(item: $selectedTransaction) { item in
            TransactionDetailSheet(item: item)
        }
    }

    private var currentAccount: SharedAccountItem? {
        viewModel.accounts.first {
            $0.bankName == bankName && $0.accountLast4 == accountLast4
        }
    }

    // MARK: - Balance Card

    @ViewBuilder
    private var balanceCard: some View {
        VStack(spacing: AppSpacing.md) {
            HStack(spacing: AppSpacing.md) {
                Image(systemName: currentAccount?.isCreditCard == true
                      ? "creditcard.fill" : "building.columns.fill")
                    .font(.title2)
                    .foregroundStyle(.blue)

                VStack(alignment: .leading, spacing: AppSpacing.xs) {
                    Text(bankName)
                        .font(AppTypography.headline)
                    HStack(spacing: AppSpacing.xs) {
                        Text("••\(accountLast4)")
                            .font(AppTypography.caption)
                            .foregroundStyle(.secondary)
                        if let type = currentAccount?.accountType {
                            AccountTypeBadge(type: type)
                        }
                    }
                }

                Spacer()
            }

            Divider()

            VStack(spacing: AppSpacing.xs) {
                Text(currentAccount?.isCreditCard == true ? "Outstanding" : "Balance")
                    .font(AppTypography.caption)
                    .foregroundStyle(.secondary)
                Text(AmountFormatter.format(
                    minorUnits: currentAccount?.balanceMinor ?? 0,
                    currency: currentAccount?.currency ?? CurrencyManager.shared.displayCurrency
                ))
                .font(AppTypography.amountLarge)
            }

            if let account = currentAccount {
                Text("Last updated: \(Date(epochMillis: account.lastUpdatedEpochMillis).formatted(as: "dd MMM yyyy, HH:mm"))")
                    .font(AppTypography.caption2)
                    .foregroundStyle(.tertiary)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(AppSpacing.lg)
        .background(AppColors.surface(isAmoled: isAmoled))
        .clipShape(RoundedRectangle(cornerRadius: AppCornerRadius.large))
    }

    // MARK: - Transactions Section

    @ViewBuilder
    private var transactionsSection: some View {
        VStack(alignment: .leading, spacing: AppSpacing.md) {
            HStack {
                Text("Transactions")
                    .font(AppTypography.headline)
                Spacer()
                Text("\(viewModel.detailTransactions.count)")
                    .font(AppTypography.caption)
                    .foregroundStyle(.secondary)
            }

            if viewModel.detailTransactions.isEmpty {
                VStack(spacing: AppSpacing.md) {
                    Image(systemName: "tray")
                        .font(.largeTitle)
                        .foregroundStyle(.quaternary)
                    Text("No transactions for this account")
                        .font(AppTypography.body)
                        .foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, AppSpacing.xl)
            } else {
                ForEach(viewModel.detailTransactions, id: \.transactionId) { item in
                    TransactionRow(
                        item: item,
                        onViewDetails: { selectedTransaction = item }
                    )
                }
            }
        }
    }
}
