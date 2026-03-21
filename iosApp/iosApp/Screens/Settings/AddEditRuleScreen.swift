import Shared
import SwiftUI

struct AddEditRuleScreen: View {
    @ObservedObject var viewModel: RulesViewModel
    var editingRule: SharedRuleItem?
    var onSave: () -> Void

    @Environment(\.dismiss) private var dismiss

    @State private var ruleName = ""
    @State private var keyword = ""
    @State private var selectedCategory: String?
    @State private var selectedType = "EXPENSE"
    @State private var isEnabled = true

    private let transactionTypes = ["EXPENSE", "INCOME", "INVESTMENT", "CREDIT", "TRANSFER"]

    private var isEditing: Bool { editingRule != nil }

    var body: some View {
        Form {
            ruleInfoSection
            matchSection
            actionSection
            if isEditing {
                enabledSection
            }
        }
        .navigationTitle(isEditing ? "Edit Rule" : "New Rule")
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
            if let rule = editingRule {
                ruleName = rule.name
                isEnabled = rule.isEnabled
                loadFromJson(rule: rule)
            }
        }
    }

    // MARK: - Sections

    private var ruleInfoSection: some View {
        Section("Rule Info") {
            TextField("Rule name", text: $ruleName)
        }
    }

    private var matchSection: some View {
        Section {
            TextField("Keyword (e.g. Amazon, Netflix)", text: $keyword)
        } header: {
            Text("Match Condition")
        } footer: {
            Text("Transactions with merchant names containing this keyword will be matched.")
        }
    }

    private var actionSection: some View {
        Section("Assign To") {
            categoryPicker
            typePicker
        }
    }

    private var categoryPicker: some View {
        Group {
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

    private var typePicker: some View {
        Picker("Transaction Type", selection: $selectedType) {
            ForEach(transactionTypes, id: \.self) { type in
                Label {
                    Text(type.capitalized)
                } icon: {
                    Image(systemName: AppColors.transactionIcon(for: type))
                        .foregroundStyle(AppColors.transactionColor(for: type))
                }
                .tag(type)
            }
        }
    }

    private var enabledSection: some View {
        Section {
            Toggle("Rule Enabled", isOn: $isEnabled)
        }
    }

    // MARK: - Helpers

    private var isValid: Bool {
        !ruleName.trimmingCharacters(in: .whitespaces).isEmpty &&
        !keyword.trimmingCharacters(in: .whitespaces).isEmpty
    }

    private func save() {
        let conditionsJson = buildConditionsJson()
        let actionsJson = buildActionsJson()

        if let existing = editingRule {
            _ = viewModel.updateRule(
                id: existing.id,
                name: ruleName.trimmingCharacters(in: .whitespaces),
                conditionsJson: conditionsJson,
                actionsJson: actionsJson,
                isEnabled: isEnabled,
                priority: existing.priority
            )
        } else {
            _ = viewModel.createRule(
                name: ruleName.trimmingCharacters(in: .whitespaces),
                conditionsJson: conditionsJson,
                actionsJson: actionsJson,
                priority: 0
            )
        }
        onSave()
        dismiss()
    }

    private func buildConditionsJson() -> String {
        let dict: [String: Any] = [
            "type": "merchant_contains",
            "value": keyword.trimmingCharacters(in: .whitespaces)
        ]
        guard let data = try? JSONSerialization.data(withJSONObject: dict),
              let json = String(data: data, encoding: .utf8) else {
            return "{}"
        }
        return json
    }

    private func buildActionsJson() -> String {
        var dict: [String: Any] = [
            "transactionType": selectedType
        ]
        if let cat = selectedCategory {
            dict["category"] = cat
        }
        guard let data = try? JSONSerialization.data(withJSONObject: dict),
              let json = String(data: data, encoding: .utf8) else {
            return "{}"
        }
        return json
    }

    private func loadFromJson(rule: SharedRuleItem) {
        if let data = rule.conditionsJson.data(using: .utf8),
           let dict = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
           let value = dict["value"] as? String {
            keyword = value
        }

        if let data = rule.actionsJson.data(using: .utf8),
           let dict = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
            if let cat = dict["category"] as? String {
                selectedCategory = cat
            }
            if let type = dict["transactionType"] as? String {
                selectedType = type
            }
        }
    }
}
