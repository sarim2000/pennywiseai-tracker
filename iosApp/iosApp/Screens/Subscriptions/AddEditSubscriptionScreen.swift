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
                amountText = String(subscription.amountMinor / 100)
                selectedCategory = subscription.category
            }
        }
    }

    // MARK: - Sections

    private var subscriptionInfoSection: some View {
        Section("Subscription Info") {
            TextField("Service Name", text: $merchantName)
            HStack {
                Text(currencyManager.currencySymbol)
                    .foregroundStyle(.secondary)
                TextField("Amount", text: $amountText)
                    .keyboardType(.numberPad)
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
                                    .foregroundStyle(Color.accentColor)
                            }
                        }
                    }
                }
            }
        }
    }

    // MARK: - Helpers

    private var isValid: Bool {
        !merchantName.trimmingCharacters(in: .whitespaces).isEmpty &&
        (Int64(amountText) ?? 0) > 0
    }

    private func save() {
        let amountMinor = (Int64(amountText) ?? 0) * 100
        let currency = currencyManager.displayCurrency

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
