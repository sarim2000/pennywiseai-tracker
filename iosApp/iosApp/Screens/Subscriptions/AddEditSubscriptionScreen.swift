import Shared
import SwiftUI

struct AddEditSubscriptionScreen: View {
    @ObservedObject private var currencyManager = CurrencyManager.shared
    @ObservedObject var viewModel: SubscriptionsViewModel
    var editingSubscription: SharedSubscriptionItem?
    var onSave: () -> Void

    @Environment(\.dismiss) private var dismiss

    @State private var merchantName = ""
    @State private var amountText = ""
    @State private var selectedCategory: String?
    // Starts at the display currency for a new subscription; an edit loads
    // the subscription's own currency so saving never re-tags it.
    @State private var selectedCurrency = CurrencyManager.shared.displayCurrency

    private var isEditing: Bool { editingSubscription != nil }

    var body: some View {
        Form {
            subscriptionInfoSection
            categorySection
        }
        .navigationTitle(isEditing ? "Edit Subscription" : "New Subscription")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button("Cancel") { dismiss() }
            }
            ToolbarItem(placement: .confirmationAction) {
                Button(isEditing ? "Save" : "Create") { save() }
                    .disabled(!isValid)
            }
        }
        .onAppear {
            viewModel.loadCategories()
            if let subscription = editingSubscription {
                merchantName = subscription.merchantName
                // Keep the paise/cents: "2.99", not a truncated "2".
                amountText = AmountFormatter.format(minorUnits: subscription.amountMinor)
                selectedCategory = subscription.category
                selectedCurrency = subscription.currency
            }
        }
    }

    // MARK: - Sections

    private var subscriptionInfoSection: some View {
        Section("Subscription Info") {
            TextField("Service Name", text: $merchantName)
            HStack {
                Picker("Currency", selection: $selectedCurrency) {
                    ForEach(CurrencyPickerScreen.currencies.map(\.code), id: \.self) { Text($0).tag($0) }
                }
                .labelsHidden()
                .fixedSize()
                TextField("Amount", text: $amountText)
                    .keyboardType(.decimalPad)
            }
        }
    }

    private var categorySection: some View {
        Section("Category") {
            if viewModel.categories.isEmpty {
                Text("Loading categories...")
                    .foregroundStyle(.secondary)
            } else {
                ForEach(viewModel.categories, id: \.self) { category in
                    Button {
                        if selectedCategory == category {
                            selectedCategory = nil
                        } else {
                            selectedCategory = category
                        }
                    } label: {
                        HStack {
                            Circle()
                                .fill(AppColors.categoryColor(for: category))
                                .frame(width: 10, height: 10)
                            Text(category)
                                .foregroundStyle(.primary)
                            Spacer()
                            if selectedCategory == category {
                                Image(systemName: "checkmark.circle.fill")
                                    .foregroundStyle(ThemeManager.shared.accentColor)
                            }
                        }
                    }
                    // A primary label, so a selectable row doesn't read as a link.
                    .tint(.primary)
                }
            }
        }
    }

    // MARK: - Helpers

    private var isValid: Bool {
        !merchantName.trimmingCharacters(in: .whitespaces).isEmpty &&
        amountMinor > 0
    }

    private var amountMinor: Int64 {
        Int64(((Double(amountText) ?? 0) * 100).rounded())
    }

    private func save() {
        let currency = selectedCurrency

        if let existing = editingSubscription {
            _ = viewModel.updateSubscription(
                id: existing.id,
                merchantName: merchantName.trimmingCharacters(in: .whitespaces),
                amountMinor: amountMinor,
                category: selectedCategory,
                currency: currency
            )
        } else {
            _ = viewModel.createSubscription(
                merchantName: merchantName.trimmingCharacters(in: .whitespaces),
                amountMinor: amountMinor,
                category: selectedCategory,
                currency: currency
            )
        }
        onSave()
        dismiss()
    }
}
