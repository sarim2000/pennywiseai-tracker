import Shared
import SwiftUI

struct AddEditTransactionView: View {
    let facade: PennyWiseSharedFacade
    var editingTransactionId: Int64? = nil
    var onSave: (() -> Void)?

    @Environment(\.dismiss) private var dismiss

    @State private var merchantName = ""
    @State private var amountText = ""
    @State private var selectedType = "EXPENSE"
    @State private var selectedCategory = "Others"
    @State private var selectedCurrency = CurrencyManager.shared.displayCurrency
    @State private var selectedDate = Date()
    @State private var bankName = ""
    @State private var accountLast4 = ""
    @State private var noteText = ""
    @State private var categories: [String] = []
    @State private var errorMessage: String?

    private let transactionTypes = ["INCOME", "EXPENSE", "CREDIT", "TRANSFER", "INVESTMENT"]
    private let currencies = CurrencyPickerScreen.currencies.map(\.code)

    private var isEditing: Bool { editingTransactionId != nil }

    private var isValid: Bool {
        !merchantName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty &&
            Double(amountText).map { $0 > 0 } ?? false
    }

    var body: some View {
        Form {
            Section("Amount") {
                HStack {
                    Picker("Currency", selection: $selectedCurrency) {
                        ForEach(currencies, id: \.self) { Text($0).tag($0) }
                    }
                    .labelsHidden()
                    .frame(width: 80)

                    TextField("0.00", text: $amountText)
                        .keyboardType(.decimalPad)
                        .font(.title2.bold())
                }
            }

            Section("Details") {
                TextField("Merchant name", text: $merchantName)

                Picker("Type", selection: $selectedType) {
                    ForEach(transactionTypes, id: \.self) { type in
                        Text(type).tag(type)
                    }
                }

                Picker("Category", selection: $selectedCategory) {
                    ForEach(categories, id: \.self) { category in
                        Text(category).tag(category)
                    }
                }

                DatePicker("Date", selection: $selectedDate, displayedComponents: [.date, .hourAndMinute])
            }

            Section("Optional") {
                TextField("Bank name", text: $bankName)
                TextField("Account last 4 digits", text: $accountLast4)
                    .keyboardType(.numberPad)
                TextField("Notes", text: $noteText, axis: .vertical)
                    .lineLimit(3 ... 6)
            }

            if let error = errorMessage {
                Section {
                    Text(error)
                        .foregroundStyle(.red)
                        .font(.footnote)
                }
            }
        }
        .navigationTitle(isEditing ? "Edit Transaction" : "Add Transaction")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button("Save") { saveTransaction() }
                    .bold()
                    .disabled(!isValid)
            }
        }
        .onAppear {
            loadCategories()
            if let editId = editingTransactionId {
                loadExistingTransaction(editId)
            }
        }
    }

    private func loadCategories() {
        let snapshot = facade.initializeAndLoadHome()
        let snapshotCategories = snapshot.categories
        categories = snapshotCategories.isEmpty ? ["Others"] : snapshotCategories
        if !categories.contains(selectedCategory) {
            selectedCategory = categories.first ?? "Others"
        }
    }

    private func loadExistingTransaction(_ id: Int64) {
        guard let txn = facade.getTransactionById(transactionId: id) else { return }
        merchantName = txn.merchantName
        amountText = AmountFormatter.format(minorUnits: txn.amountMinor)
        selectedType = txn.transactionType
        selectedCategory = txn.category
        selectedCurrency = txn.currency
        selectedDate = Date(timeIntervalSince1970: TimeInterval(txn.occurredAtEpochMillis) / 1000.0)
        bankName = txn.bankName ?? ""
        accountLast4 = txn.accountLast4 ?? ""
        noteText = txn.note ?? ""
    }

    private func saveTransaction() {
        guard isValid else { return }
        guard let amountValue = Double(amountText) else { return }

        let amountMinor = Int64((amountValue * 100.0).rounded())
        let epochMillis = Int64((selectedDate.timeIntervalSince1970 * 1000.0).rounded())

        if let editId = editingTransactionId {
            let snapshot = facade.updateTransactionAndLoadHome(
                transactionId: editId,
                merchantName: merchantName.trimmingCharacters(in: .whitespacesAndNewlines),
                category: selectedCategory,
                amountMinor: amountMinor,
                note: noteText.isEmpty ? nil : noteText,
                currency: selectedCurrency,
                transactionType: selectedType,
                occurredAtEpochMillis: epochMillis,
                bankName: bankName.isEmpty ? nil : bankName,
                accountLast4: accountLast4.isEmpty ? nil : accountLast4
            )
            errorMessage = snapshot.lastError
        } else {
            let snapshot = facade.addTransactionAndLoadHome(
                merchantName: merchantName.trimmingCharacters(in: .whitespacesAndNewlines),
                category: selectedCategory,
                amountMinor: amountMinor,
                note: noteText.isEmpty ? nil : noteText,
                currency: selectedCurrency,
                transactionType: selectedType,
                occurredAtEpochMillis: epochMillis,
                bankName: bankName.isEmpty ? nil : bankName,
                accountLast4: accountLast4.isEmpty ? nil : accountLast4
            )
            errorMessage = snapshot.lastError
        }

        if errorMessage == nil {
            onSave?()
            dismiss()
        }
    }
}
