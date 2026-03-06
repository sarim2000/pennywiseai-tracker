import Shared
import SwiftUI

struct AccountListScreen: View {
    @ObservedObject private var currencyManager = CurrencyManager.shared
    @StateObject private var viewModel = AccountsViewModel()
    @State private var showAddAccount = false
    @State private var showAddCard = false
    @State private var appeared = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: AppSpacing.lg) {
                totalBalanceCard
                    .opacity(appeared ? 1 : 0)
                    .offset(y: appeared ? 0 : 12)

                accountsSection
                    .opacity(appeared ? 1 : 0)
                    .offset(y: appeared ? 0 : 16)

                cardsSection
                    .opacity(appeared ? 1 : 0)
                    .offset(y: appeared ? 0 : 20)
            }
            .padding(.horizontal, AppSpacing.md)
            .padding(.top, AppSpacing.sm)
            .padding(.bottom, AppSpacing.xl)
        }
        .navigationTitle("Accounts")
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Menu {
                    Button {
                        showAddAccount = true
                    } label: {
                        Label("Add Account", systemImage: "building.columns")
                    }
                    Button {
                        showAddCard = true
                    } label: {
                        Label("Add Card", systemImage: "creditcard")
                    }
                } label: {
                    Image(systemName: "plus")
                }
            }
        }
        .onAppear {
            viewModel.loadAccounts()
            withAnimation(.easeOut(duration: 0.5).delay(0.1)) {
                appeared = true
            }
        }
        .refreshable {
            viewModel.loadAccounts()
        }
        .sheet(isPresented: $showAddAccount) {
            NavigationStack {
                AddEditAccountScreen(viewModel: viewModel, onSave: {
                    viewModel.loadAccounts()
                })
            }
        }
        .sheet(isPresented: $showAddCard) {
            NavigationStack {
                AddCardSheet(viewModel: viewModel, onSave: {
                    viewModel.loadAccounts()
                })
            }
        }
    }

    // MARK: - Total Balance Card

    @ViewBuilder
    private var totalBalanceCard: some View {
        VStack(spacing: AppSpacing.sm) {
            Text("Total Balance")
                .font(AppTypography.caption)
                .foregroundStyle(.secondary)
            Text(AmountFormatter.format(
                minorUnits: viewModel.totalBalanceMinor,
                currency: viewModel.accounts.first?.currency ?? CurrencyManager.shared.displayCurrency
            ))
            .font(AppTypography.amountLarge)
            .foregroundStyle(.primary)

            Text("\(viewModel.accounts.count) account\(viewModel.accounts.count == 1 ? "" : "s")")
                .font(AppTypography.caption2)
                .foregroundStyle(.tertiary)
        }
        .frame(maxWidth: .infinity)
        .padding(AppSpacing.lg)
        .background(Color(.secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: AppCornerRadius.large))
    }

    // MARK: - Accounts Section

    @ViewBuilder
    private var accountsSection: some View {
        VStack(alignment: .leading, spacing: AppSpacing.md) {
            Text("Accounts")
                .font(AppTypography.headline)

            if viewModel.accounts.isEmpty {
                emptyAccountsView
            } else {
                ForEach(viewModel.accounts, id: \.accountKey) { account in
                    NavigationLink(destination: AccountDetailScreen(
                        bankName: account.bankName,
                        accountLast4: account.accountLast4,
                        viewModel: viewModel
                    )) {
                        AccountRow(account: account)
                    }
                    .buttonStyle(.plain)
                    .contextMenu {
                        Button(role: .destructive) {
                            viewModel.deleteAccount(
                                bankName: account.bankName,
                                accountLast4: account.accountLast4
                            )
                        } label: {
                            Label("Delete", systemImage: "trash")
                        }
                    }
                }
            }
        }
    }

    @ViewBuilder
    private var emptyAccountsView: some View {
        VStack(spacing: AppSpacing.md) {
            Image(systemName: "building.columns")
                .font(.largeTitle)
                .foregroundStyle(.quaternary)
            Text("No accounts yet")
                .font(AppTypography.body)
                .foregroundStyle(.secondary)
            Text("Tap + to add your first account")
                .font(AppTypography.caption)
                .foregroundStyle(.tertiary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, AppSpacing.xl)
    }

    // MARK: - Cards Section

    @ViewBuilder
    private var cardsSection: some View {
        VStack(alignment: .leading, spacing: AppSpacing.md) {
            Text("Cards")
                .font(AppTypography.headline)

            if viewModel.cards.isEmpty {
                VStack(spacing: AppSpacing.sm) {
                    Image(systemName: "creditcard")
                        .font(.title2)
                        .foregroundStyle(.quaternary)
                    Text("No cards added")
                        .font(AppTypography.caption)
                        .foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, AppSpacing.lg)
            } else {
                ForEach(viewModel.cards, id: \.id) { card in
                    CardRow(card: card)
                        .contextMenu {
                            Button(role: .destructive) {
                                viewModel.deleteCard(cardId: card.id)
                            } label: {
                                Label("Delete", systemImage: "trash")
                            }
                        }
                }
            }
        }
    }
}

// MARK: - Account Row

private struct AccountRow: View {
    let account: SharedAccountItem

    var body: some View {
        HStack(spacing: AppSpacing.md) {
            Image(systemName: account.isCreditCard ? "creditcard.fill" : "building.columns.fill")
                .font(.title3)
                .foregroundStyle(.blue)
                .frame(width: 28)

            VStack(alignment: .leading, spacing: AppSpacing.xs) {
                Text(account.bankName)
                    .font(AppTypography.body)
                    .lineLimit(1)
                HStack(spacing: AppSpacing.xs) {
                    Text("••\(account.accountLast4)")
                        .font(AppTypography.caption2)
                        .foregroundStyle(.secondary)
                    AccountTypeBadge(type: account.accountType ?? "SAVINGS")
                }
            }

            Spacer()

            Text(AmountFormatter.format(
                minorUnits: account.balanceMinor,
                currency: account.currency
            ))
            .font(AppTypography.amountSmall)
            .foregroundStyle(.primary)

            Image(systemName: "chevron.right")
                .font(.caption)
                .foregroundStyle(.tertiary)
        }
        .padding(AppSpacing.md)
        .background(Color(.secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: AppCornerRadius.medium))
    }
}

// MARK: - Card Row

private struct CardRow: View {
    let card: SharedCardItem

    var body: some View {
        HStack(spacing: AppSpacing.md) {
            Image(systemName: "creditcard.fill")
                .font(.title3)
                .foregroundStyle(card.cardType == "CREDIT" ? .orange : .blue)
                .frame(width: 28)

            VStack(alignment: .leading, spacing: AppSpacing.xs) {
                Text("\(card.bankName) ••\(card.cardLast4)")
                    .font(AppTypography.body)
                    .lineLimit(1)
                Text(card.cardType.capitalized)
                    .font(AppTypography.caption2)
                    .foregroundStyle(.secondary)
            }

            Spacer()

            if let balance = card.lastBalanceMinor {
                Text(AmountFormatter.format(
                    minorUnits: balance.int64Value,
                    currency: card.currency
                ))
                .font(AppTypography.amountSmall)
                .foregroundStyle(.primary)
            }
        }
        .padding(AppSpacing.md)
        .background(Color(.secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: AppCornerRadius.medium))
    }
}

// MARK: - Account Type Badge

struct AccountTypeBadge: View {
    let type: String

    var body: some View {
        Text(displayText)
            .font(.system(size: 10, weight: .medium))
            .padding(.horizontal, 6)
            .padding(.vertical, 2)
            .background(badgeColor.opacity(0.15))
            .foregroundStyle(badgeColor)
            .clipShape(Capsule())
    }

    private var displayText: String {
        switch type.uppercased() {
        case "SAVINGS": return "Savings"
        case "CURRENT": return "Current"
        case "CREDIT": return "Credit"
        default: return type.capitalized
        }
    }

    private var badgeColor: Color {
        switch type.uppercased() {
        case "SAVINGS": return .green
        case "CURRENT": return .blue
        case "CREDIT": return .orange
        default: return .secondary
        }
    }
}

// MARK: - Identifiable conformance

extension SharedAccountItem {
    var accountKey: String {
        "\(bankName)-\(accountLast4)"
    }
}

// MARK: - Add Card Sheet

struct AddCardSheet: View {
    @ObservedObject var viewModel: AccountsViewModel
    var onSave: () -> Void
    @Environment(\.dismiss) private var dismiss

    @State private var cardLast4 = ""
    @State private var cardType = "CREDIT"
    @State private var bankName = ""

    private let cardTypes = ["CREDIT", "DEBIT"]

    var body: some View {
        Form {
            Section("Card Details") {
                TextField("Bank Name", text: $bankName)
                    .autocorrectionDisabled()

                TextField("Card Last 4 Digits", text: $cardLast4)
                    .keyboardType(.numberPad)
                    .onChange(of: cardLast4) { newValue in
                        let filtered = String(newValue.filter(\.isNumber).prefix(4))
                        if filtered != newValue { cardLast4 = filtered }
                    }

                Picker("Card Type", selection: $cardType) {
                    ForEach(cardTypes, id: \.self) { type in
                        Text(type.capitalized).tag(type)
                    }
                }
            }
        }
        .navigationTitle("Add Card")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button("Cancel") { dismiss() }
            }
            ToolbarItem(placement: .confirmationAction) {
                Button("Save") {
                    _ = viewModel.createCard(
                        cardLast4: cardLast4,
                        cardType: cardType,
                        bankName: bankName,
                        accountLast4: nil
                    )
                    onSave()
                    dismiss()
                }
                .disabled(bankName.isEmpty || cardLast4.count != 4)
            }
        }
    }
}
