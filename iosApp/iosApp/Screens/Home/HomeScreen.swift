import Shared
import SwiftUI

extension SharedRecentTransactionItem: @retroactive Identifiable {
    public var id: Int64 { transactionId }
}

struct HomeScreen: View {
    var onSeeAllTransactions: (() -> Void)? = nil

    @ObservedObject private var currencyManager = CurrencyManager.shared
    @StateObject private var viewModel = HomeViewModel()
    @State private var showAddTransaction = false
    @State private var selectedDetail: SharedRecentTransactionItem?
    @State private var appeared = false

    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            ScrollView {
                VStack(alignment: .leading, spacing: AppSpacing.lg) {
                    // Greeting Card
                    GreetingCard()
                        .opacity(appeared ? 1 : 0)
                        .offset(y: appeared ? 0 : 10)

                    // Spending Summary Card
                    SpendingSummaryCard(
                        monthlyExpenseMinor: viewModel.monthlyExpenseMinor,
                        monthlyIncomeMinor: viewModel.monthlyIncomeMinor,
                        monthlyNetMinor: viewModel.monthlyNetMinor
                    )
                    .opacity(appeared ? 1 : 0)
                    .offset(y: appeared ? 0 : 12)

                    // Budget Carousel
                    if !viewModel.budgets.isEmpty {
                        BudgetCarousel(budgets: viewModel.budgets)
                            .opacity(appeared ? 1 : 0)
                            .offset(y: appeared ? 0 : 14)
                    }

                    // Account Carousel
                    if !viewModel.accounts.isEmpty {
                        AccountCarousel(accounts: viewModel.accounts)
                            .opacity(appeared ? 1 : 0)
                            .offset(y: appeared ? 0 : 14)
                    }

                    // Subscription Summary
                    if viewModel.subscriptionCount > 0 {
                        subscriptionSummaryCard
                            .opacity(appeared ? 1 : 0)
                            .offset(y: appeared ? 0 : 15)
                    }

                    // Recent Transactions
                    recentTransactionsSection
                        .opacity(appeared ? 1 : 0)
                        .offset(y: appeared ? 0 : 16)

                    // Error / Import messages
                    statusMessages
                }
                .padding(.horizontal, AppSpacing.md)
                .padding(.top, AppSpacing.sm)
                .padding(.bottom, 80)
            }
            .refreshable {
                viewModel.loadHome()
            }

            // Floating Add Button
            addButton
        }
        .onAppear {
            viewModel.loadHome()
            withAnimation(.easeOut(duration: 0.5).delay(0.1)) {
                appeared = true
            }
        }
        .sheet(isPresented: $showAddTransaction) {
            NavigationStack {
                AddEditTransactionView(facade: viewModel.facade, onSave: {
                    viewModel.loadHome()
                })
            }
        }
        .sheet(item: $selectedDetail) { item in
            TransactionDetailSheet(item: item)
        }
    }

    // MARK: - Recent Transactions Section

    @ViewBuilder
    private var recentTransactionsSection: some View {
        VStack(alignment: .leading, spacing: AppSpacing.md) {
            HStack {
                Text("Recent Transactions")
                    .font(AppTypography.headline)
                Spacer()
                Button("See All") {
                    onSeeAllTransactions?()
                }
                .font(AppTypography.caption)
            }

            if viewModel.recentTransactions.isEmpty {
                emptyTransactionsView
            } else {
                ForEach(Array(viewModel.recentTransactions.prefix(5))) { item in
                    TransactionRow(
                        item: item,
                        onViewDetails: { selectedDetail = item }
                    )
                    .contextMenu {
                        Button(role: .destructive) {
                            viewModel.deleteTransaction(id: item.transactionId)
                        } label: {
                            Label("Delete", systemImage: "trash")
                        }
                    }
                }
            }
        }
    }

    @ViewBuilder
    private var emptyTransactionsView: some View {
        VStack(spacing: AppSpacing.md) {
            Image(systemName: "tray")
                .font(.largeTitle)
                .foregroundStyle(.quaternary)
            Text("No transactions yet")
                .font(AppTypography.body)
                .foregroundStyle(.secondary)
            Text("Tap + to add your first transaction")
                .font(AppTypography.caption)
                .foregroundStyle(.tertiary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, AppSpacing.xl)
    }

    // MARK: - Status Messages

    @ViewBuilder
    private var statusMessages: some View {
        if let errorMessage = viewModel.errorMessage {
            Text(errorMessage)
                .foregroundStyle(.red)
                .font(.footnote)
        }
        if let importResultText = viewModel.importResultText {
            Text(importResultText)
                .foregroundStyle(.green)
                .font(.footnote)
        }
    }

    // MARK: - Subscription Summary

    @ViewBuilder
    private var subscriptionSummaryCard: some View {
        HStack {
            Label {
                Text("Subscriptions")
                    .font(AppTypography.body)
            } icon: {
                Image(systemName: "repeat")
                    .foregroundStyle(.indigo)
            }
            Spacer()
            Text("\(viewModel.subscriptionCount)")
                .font(AppTypography.caption)
                .padding(.horizontal, AppSpacing.sm)
                .padding(.vertical, AppSpacing.xs)
                .background(Capsule().fill(Color.indigo.opacity(0.15)))
                .foregroundStyle(.indigo)
            Text(CurrencyFormatter.format(
                amountMinor: viewModel.subscriptionMonthlyTotal,
                currencyCode: CurrencyManager.shared.displayCurrency
            ))
            .font(AppTypography.headline)
            .foregroundStyle(.primary)
        }
        .padding(AppSpacing.md)
        .background(RoundedRectangle(cornerRadius: 12).fill(Color(.secondarySystemGroupedBackground)))
    }

    // MARK: - Floating Add Button

    private var addButton: some View {
        Button {
            showAddTransaction = true
        } label: {
            Image(systemName: "plus")
                .font(.title2.bold())
                .foregroundStyle(.white)
                .frame(width: 56, height: 56)
                .background(Circle().fill(.blue))
                .shadow(color: .blue.opacity(0.3), radius: 8, x: 0, y: 4)
        }
        .padding(AppSpacing.lg)
    }
}
