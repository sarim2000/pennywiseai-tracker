import Shared
import SwiftUI

extension SharedRecentTransactionItem: @retroactive Identifiable {
    public var id: Int64 { transactionId }
}

struct HomeScreen: View {
    var onSeeAllTransactions: (() -> Void)? = nil

    @ObservedObject private var currencyManager = CurrencyManager.shared
    @ObservedObject private var themeManager = ThemeManager.shared
    @StateObject private var viewModel = HomeViewModel()
    @State private var showAddTransaction = false
    @State private var selectedDetail: SharedRecentTransactionItem?
    @State private var appeared = false
    @State private var fabExpanded = false
    @State private var showImportPicker = false
    @State private var importResult: String?
    @State private var pendingDelete: SharedRecentTransactionItem?
    @Environment(\.isAmoledActive) private var isAmoled

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
                        expense: viewModel.monthlyExpense,
                        income: viewModel.monthlyIncome,
                        net: viewModel.monthlyNet
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
                // Clears the floating + (56pt + its 24pt padding) so the last
                // row and the empty state are never under it.
                .padding(.bottom, 104)
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
                }, showsCancel: true)
            }
        }
        .sheet(item: $selectedDetail) { item in
            TransactionDetailSheet(item: item, onUpdate: { viewModel.loadHome() })
        }
        .fileImporter(
            isPresented: $showImportPicker,
            allowedContentTypes: [.pdf]
        ) { result in
            if case .success(let url) = result {
                importResult = StatementImportService.importPDF(at: url)
                viewModel.loadHome()
            }
        }
        // Same confirmation as the detail screen's Delete.
        .alert(
            "Delete Transaction",
            isPresented: Binding(
                get: { pendingDelete != nil },
                set: { if !$0 { pendingDelete = nil } }
            ),
            presenting: pendingDelete
        ) { item in
            Button("Delete", role: .destructive) {
                viewModel.deleteTransaction(id: item.transactionId)
            }
            Button("Cancel", role: .cancel) {}
        } message: { item in
            Text("Are you sure you want to delete the transaction \"\(item.merchantName)\"?")
        }
        .alert(
            "Statement Import",
            isPresented: Binding(
                get: { importResult != nil },
                set: { if !$0 { importResult = nil } }
            )
        ) {
            Button("OK", role: .cancel) { importResult = nil }
        } message: {
            Text(importResult ?? "")
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
                    Button { selectedDetail = item } label: {
                        TransactionRow(item: item)
                    }
                    .buttonStyle(.plain)
                    .contextMenu {
                        Button(role: .destructive) {
                            pendingDelete = item
                        } label: {
                            Label("Delete", systemImage: "trash")
                        }
                    }
                }
            }
        }
    }

    private var emptyTransactionsView: some View {
        EmptyStateView(
            title: "No Transactions Yet",
            systemImage: "tray",
            message: "Add one by hand or import a statement."
        ) {
            Button("Add Transaction") { showAddTransaction = true }
                .buttonStyle(.borderedProminent)
        }
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
            // One line per currency: never one cross-currency sum.
            VStack(alignment: .trailing, spacing: 2) {
                ForEach(viewModel.subscriptionTotals, id: \.currency) { total in
                    Text(CurrencyFormatter.format(amountMinor: total.amountMinor, currencyCode: total.currency))
                        .font(AppTypography.headline)
                        .foregroundStyle(.primary)
                        .lineLimit(1)
                }
            }
        }
        .padding(AppSpacing.md)
        .background(RoundedRectangle(cornerRadius: 12).fill(AppColors.surface(isAmoled: isAmoled)))
    }

    // MARK: - Floating Add Button

    /// Android-style speed dial: + expands into labeled actions instead of
    /// jumping straight to one form, so statement import is one tap away.
    private var addButton: some View {
        VStack(alignment: .trailing, spacing: AppSpacing.md) {
            if fabExpanded {
                fabAction(
                    label: "Import Statement",
                    systemImage: "doc.text.fill"
                ) {
                    showImportPicker = true
                }
                fabAction(
                    label: "Add Transaction",
                    systemImage: "square.and.pencil"
                ) {
                    showAddTransaction = true
                }
            }

            Button {
                withAnimation(.spring(response: 0.3, dampingFraction: 0.75)) {
                    fabExpanded.toggle()
                }
            } label: {
                Image(systemName: "plus")
                    .font(.title2.bold())
                    .foregroundStyle(themeManager.onAccentColor)
                    .rotationEffect(.degrees(fabExpanded ? 45 : 0))
                    .frame(width: 56, height: 56)
                    .background(Circle().fill(themeManager.accentColor))
                    .shadow(color: themeManager.accentColor.opacity(0.3), radius: 8, x: 0, y: 4)
            }
        }
        .padding(AppSpacing.lg)
    }

    private func fabAction(
        label: String,
        systemImage: String,
        action: @escaping () -> Void
    ) -> some View {
        Button {
            withAnimation(.spring(response: 0.3, dampingFraction: 0.75)) {
                fabExpanded = false
            }
            action()
        } label: {
            HStack(spacing: AppSpacing.sm) {
                Text(label)
                    .font(AppTypography.caption)
                    .fontWeight(.semibold)
                Image(systemName: systemImage)
                    .font(.body)
            }
            .foregroundStyle(themeManager.onAccentColor)
            .padding(.horizontal, AppSpacing.md)
            .frame(height: 44)
            .background(Capsule().fill(themeManager.accentColor))
            .shadow(color: themeManager.accentColor.opacity(0.25), radius: 6, x: 0, y: 3)
        }
        .transition(.scale(scale: 0.6, anchor: .bottomTrailing).combined(with: .opacity))
    }
}
