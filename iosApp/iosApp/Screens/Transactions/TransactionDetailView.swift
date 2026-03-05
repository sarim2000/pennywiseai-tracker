import Shared
import SwiftUI

struct TransactionDetailView: View {
    let transactionId: Int64
    let facade: PennyWiseSharedFacade
    var onUpdate: (() -> Void)?

    @Environment(\.dismiss) private var dismiss
    @State private var transaction: SharedRecentTransactionItem?
    @State private var showDeleteConfirmation = false

    var body: some View {
        Group {
            if let txn = transaction {
                detailContent(txn)
            } else {
                VStack(spacing: 12) {
                    Image(systemName: "exclamationmark.triangle")
                        .font(.system(size: 48))
                        .foregroundStyle(.secondary)
                    Text("Transaction Not Found")
                        .font(.title2.bold())
                    Text("This transaction may have been deleted.")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .navigationTitle("Details")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if transaction != nil {
                ToolbarItem(placement: .topBarTrailing) {
                    NavigationLink(destination: editDestination) {
                        Text("Edit")
                    }
                }
            }
        }
        .alert("Delete Transaction", isPresented: $showDeleteConfirmation) {
            Button("Delete", role: .destructive) {
                deleteTransaction()
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This transaction will be removed. This action cannot be undone.")
        }
        .onAppear { loadTransaction() }
    }

    @ViewBuilder
    private func detailContent(_ txn: SharedRecentTransactionItem) -> some View {
        List {
            Section {
                HStack {
                    Spacer()
                    VStack(spacing: AppSpacing.xs) {
                        AmountText(
                            amountMinor: txn.amountMinor,
                            currency: txn.currency,
                            transactionType: txn.transactionType,
                            font: AppTypography.amountLarge
                        )
                        HStack(spacing: AppSpacing.xs) {
                            Image(systemName: AppColors.transactionIcon(for: txn.transactionType))
                                .font(.caption)
                            Text(txn.transactionType)
                        }
                        .font(AppTypography.caption)
                        .padding(.horizontal, 10)
                        .padding(.vertical, AppSpacing.xs)
                        .background(AppColors.transactionColor(for: txn.transactionType).opacity(0.15))
                        .foregroundStyle(AppColors.transactionColor(for: txn.transactionType))
                        .clipShape(Capsule())
                    }
                    Spacer()
                }
                .listRowBackground(Color.clear)
            }

            Section("Details") {
                detailRow("Merchant", txn.merchantName, icon: "storefront")
                detailRow("Category", txn.category, icon: "tag")
                detailRow("Date", Date(epochMillis: txn.occurredAtEpochMillis).formatted(as: "dd MMM yyyy, hh:mm a"), icon: "calendar")
                if let bank = txn.bankName, !bank.isEmpty {
                    detailRow("Bank", bank, icon: "building.columns")
                }
                if let account = txn.accountLast4, !account.isEmpty {
                    detailRow("Account", "••\(account)", icon: "creditcard")
                }
                if let note = txn.note, !note.isEmpty {
                    detailRow("Notes", note, icon: "note.text")
                }
            }

            Section {
                Button(role: .destructive) {
                    showDeleteConfirmation = true
                } label: {
                    HStack {
                        Spacer()
                        Label("Delete Transaction", systemImage: "trash")
                        Spacer()
                    }
                }
            }
        }
    }

    @ViewBuilder
    private func detailRow(_ label: String, _ value: String, icon: String) -> some View {
        HStack {
            Label(label, systemImage: icon)
                .foregroundStyle(.secondary)
                .frame(width: 120, alignment: .leading)
            Spacer()
            Text(value)
                .multilineTextAlignment(.trailing)
        }
    }

    private var editDestination: some View {
        AddEditTransactionView(
            facade: facade,
            editingTransactionId: transactionId,
            onSave: {
                loadTransaction()
                onUpdate?()
            }
        )
    }

    private func loadTransaction() {
        transaction = facade.getTransactionById(transactionId: transactionId)
    }

    private func deleteTransaction() {
        _ = facade.deleteTransaction(transactionId: transactionId)
        onUpdate?()
        dismiss()
    }

}
