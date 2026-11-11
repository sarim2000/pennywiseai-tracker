import Shared
import SwiftUI

struct AddEditAccountScreen: View {
    @ObservedObject var viewModel: AccountsViewModel
    var editBankName: String?
    var editAccountLast4: String?
    var editAccount: SharedAccountItem?
    var onSave: () -> Void
    @Environment(\.dismiss) private var dismiss

    @State private var bankName = ""
    @State private var originalBankName = ""
    @State private var accountLast4 = ""
    @State private var accountType = "SAVINGS"
    @State private var balanceText = ""
    @State private var currency = CurrencyManager.shared.displayCurrency

    private let accountTypes = ["SAVINGS", "CURRENT", "CREDIT"]
    private let currencies = CurrencyPickerScreen.currencies.map(\.code)

    private var isEditing: Bool { editAccount != nil }

    var body: some View {
        Form {
            Section("Account Details") {
                TextField("Bank Name", text: $bankName)
                    .autocorrectionDisabled()

                TextField("Last 4 Digits", text: $accountLast4)
                    .keyboardType(.numberPad)
                    .disabled(isEditing)
                    .onChange(of: accountLast4) { newValue in
                        let filtered = String(newValue.filter(\.isNumber).prefix(4))
                        if filtered != newValue { accountLast4 = filtered }
                    }

                Picker("Account Type", selection: $accountType) {
                    ForEach(accountTypes, id: \.self) { type in
                        Text(type.capitalized).tag(type)
                    }
                }
            }

            Section("Balance") {
                TextField("Initial Balance", text: $balanceText)
                    .keyboardType(.decimalPad)
                if balanceMinor == nil {
                    Text(AmountFormatter.invalidAmountMessage)
                        .font(.footnote)
                        .foregroundStyle(.red)
                }

                Picker("Currency", selection: $currency) {
                    ForEach(currencies, id: \.self) { curr in
                        Text(curr).tag(curr)
                    }
                }
            }
        }
        .navigationTitle(isEditing ? "Edit Account" : "Add Account")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button("Cancel") { dismiss() }
            }
            ToolbarItem(placement: .confirmationAction) {
                Button("Save") {
                    saveAccount()
                }
                .disabled(!isFormValid)
            }
        }
        .onAppear {
            if let account = editAccount {
                bankName = account.bankName
                originalBankName = account.bankName
                accountLast4 = account.accountLast4
                accountType = account.accountType ?? "SAVINGS"
                currency = account.currency
                balanceText = AmountFormatter.plain(minorUnits: account.balanceMinor, currency: account.currency)
            }
        }
    }

    private var isFormValid: Bool {
        !bankName.trimmingCharacters(in: .whitespaces).isEmpty &&
        accountLast4.count == 4 &&
        balanceMinor != nil
    }

    /// Nil when the balance can't be stored: Save stays off, nothing is renamed or written.
    private var balanceMinor: Int64? {
        AmountFormatter.minorUnits(field: balanceText, currency: currency)
    }

    private func saveAccount() {
        let trimmedName = bankName.trimmingCharacters(in: .whitespaces)
        guard let balanceMinor else { return }
        if isEditing && trimmedName != originalBankName {
            _ = viewModel.renameAccount(
                oldBankName: originalBankName,
                newBankName: trimmedName,
                accountLast4: accountLast4
            )
        }
        _ = viewModel.createAccount(
            bankName: trimmedName,
            accountLast4: accountLast4,
            accountType: accountType,
            balanceMinor: balanceMinor,
            currency: currency
        )
        onSave()
        dismiss()
    }
}
