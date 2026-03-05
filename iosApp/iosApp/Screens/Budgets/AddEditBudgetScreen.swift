import SwiftUI

struct AddEditBudgetScreen: View {
    @ObservedObject var viewModel: BudgetViewModel
    var editingBudget: BudgetItem?

    @Environment(\.dismiss) private var dismiss

    @State private var name = ""
    @State private var totalLimitText = ""
    @State private var periodType = "monthly"
    @State private var groupType = "LIMIT"
    @State private var selectedCategories: Set<String> = []
    @State private var categoryAmounts: [String: String] = [:]

    private var isEditing: Bool { editingBudget != nil }

    var body: some View {
        Form {
            budgetInfoSection
            periodSection
            categorySection
            if !selectedCategories.isEmpty {
                categoryLimitsSection
            }
        }
        .navigationTitle(isEditing ? "Edit Budget" : "New Budget")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button("Cancel") { dismiss() }
            }
            ToolbarItem(placement: .confirmationAction) {
                Button(isEditing ? "Save" : "Create") { saveBudget() }
                    .disabled(!isValid)
            }
        }
        .onAppear {
            viewModel.loadCategories()
            if let budget = editingBudget {
                name = budget.name
                totalLimitText = String(budget.limitMinor / 100)
                periodType = budget.periodType
                groupType = budget.groupType
                for cat in budget.categoryBreakdowns {
                    selectedCategories.insert(cat.categoryName)
                    categoryAmounts[cat.categoryName] = String(cat.limitMinor / 100)
                }
            }
        }
    }

    // MARK: - Sections

    private var budgetInfoSection: some View {
        Section("Budget Info") {
            TextField("Budget Name", text: $name)
            HStack {
                Text("INR")
                    .foregroundStyle(.secondary)
                TextField("Total Limit", text: $totalLimitText)
                    .keyboardType(.numberPad)
            }
            Picker("Type", selection: $groupType) {
                Text("Spending Limit").tag("LIMIT")
                Text("Savings Target").tag("TARGET")
                Text("Expected").tag("EXPECTED")
            }
        }
    }

    private var periodSection: some View {
        Section("Period") {
            Picker("Period", selection: $periodType) {
                Text("Monthly").tag("monthly")
                Text("Weekly").tag("weekly")
            }
            .pickerStyle(.segmented)
        }
    }

    private var categorySection: some View {
        Section("Categories") {
            if viewModel.categories.isEmpty {
                Text("Loading categories...")
                    .foregroundStyle(.secondary)
            } else {
                ForEach(viewModel.categories, id: \.self) { category in
                    Button {
                        toggleCategory(category)
                    } label: {
                        HStack {
                            Circle()
                                .fill(AppColors.categoryColor(for: category))
                                .frame(width: 10, height: 10)
                            Text(category)
                                .foregroundStyle(.primary)
                            Spacer()
                            if selectedCategories.contains(category) {
                                Image(systemName: "checkmark.circle.fill")
                                    .foregroundStyle(.accent)
                            }
                        }
                    }
                }
            }
        }
    }

    private var categoryLimitsSection: some View {
        Section("Category Limits") {
            ForEach(Array(selectedCategories).sorted(), id: \.self) { category in
                HStack {
                    Circle()
                        .fill(AppColors.categoryColor(for: category))
                        .frame(width: 8, height: 8)
                    Text(category)
                        .font(AppTypography.body)
                    Spacer()
                    HStack {
                        Text("INR")
                            .font(AppTypography.caption)
                            .foregroundStyle(.secondary)
                        TextField("Amount", text: binding(for: category))
                            .keyboardType(.numberPad)
                            .frame(width: 80)
                            .multilineTextAlignment(.trailing)
                    }
                }
            }
        }
    }

    // MARK: - Helpers

    private var isValid: Bool {
        !name.trimmingCharacters(in: .whitespaces).isEmpty &&
        (Int64(totalLimitText) ?? 0) > 0
    }

    private func toggleCategory(_ category: String) {
        if selectedCategories.contains(category) {
            selectedCategories.remove(category)
            categoryAmounts.removeValue(forKey: category)
        } else {
            selectedCategories.insert(category)
        }
    }

    private func binding(for category: String) -> Binding<String> {
        Binding(
            get: { categoryAmounts[category] ?? "" },
            set: { categoryAmounts[category] = $0 }
        )
    }

    private func saveBudget() {
        let limitMinor = (Int64(totalLimitText) ?? 0) * 100

        let calendar = Calendar.current
        let now = Date()
        let startOfMonth = calendar.date(from: calendar.dateComponents([.year, .month], from: now))!
        let endOfMonth = calendar.date(byAdding: .month, value: 1, to: startOfMonth)!

        let startMillis = Int64(startOfMonth.timeIntervalSince1970 * 1000)
        let endMillis = Int64(endOfMonth.timeIntervalSince1970 * 1000)

        let catLimits: [(String, Int64)] = selectedCategories.sorted().compactMap { cat in
            let amount = (Int64(categoryAmounts[cat] ?? "0") ?? 0) * 100
            return amount > 0 ? (cat, amount) : (cat, limitMinor / Int64(max(1, selectedCategories.count)))
        }

        if let existing = editingBudget {
            _ = viewModel.updateBudget(
                id: existing.id,
                name: name.trimmingCharacters(in: .whitespaces),
                limitMinor: limitMinor,
                periodType: periodType,
                startEpochMillis: startMillis,
                endEpochMillis: endMillis,
                groupType: groupType,
                currency: "INR",
                categoryLimits: catLimits
            )
        } else {
            _ = viewModel.createBudget(
                name: name.trimmingCharacters(in: .whitespaces),
                limitMinor: limitMinor,
                periodType: periodType,
                startEpochMillis: startMillis,
                endEpochMillis: endMillis,
                groupType: groupType,
                currency: "INR",
                categoryLimits: catLimits
            )
        }
        dismiss()
    }
}
