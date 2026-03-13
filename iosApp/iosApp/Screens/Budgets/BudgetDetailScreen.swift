import SwiftUI

struct BudgetDetailScreen: View {
    let budgetId: Int64
    @ObservedObject var viewModel: BudgetViewModel
    @State private var showingEditSheet = false
    @State private var showingDeleteAlert = false
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        Group {
            if let budget = viewModel.selectedBudget {
                budgetContent(budget)
            } else {
                ProgressView()
            }
        }
        .navigationTitle(viewModel.selectedBudget?.name ?? "Budget")
        .navigationBarTitleDisplayMode(.large)
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Menu {
                    Button {
                        showingEditSheet = true
                    } label: {
                        Label("Edit", systemImage: "pencil")
                    }
                    Button(role: .destructive) {
                        showingDeleteAlert = true
                    } label: {
                        Label("Delete", systemImage: "trash")
                    }
                } label: {
                    Image(systemName: "ellipsis.circle")
                }
            }
        }
        .sheet(isPresented: $showingEditSheet) {
            NavigationStack {
                AddEditBudgetScreen(viewModel: viewModel, editingBudget: viewModel.selectedBudget)
            }
        }
        .alert("Delete Budget?", isPresented: $showingDeleteAlert) {
            Button("Cancel", role: .cancel) { }
            Button("Delete", role: .destructive) {
                if viewModel.deleteBudget(id: budgetId) {
                    dismiss()
                }
            }
        } message: {
            Text("This will permanently delete this budget.")
        }
        .onAppear {
            viewModel.loadBudgetDetail(id: budgetId)
        }
    }

    @ViewBuilder
    private func budgetContent(_ budget: BudgetItem) -> some View {
        ScrollView {
            VStack(spacing: AppSpacing.lg) {
                overallProgressCard(budget)

                if !budget.categoryBreakdowns.isEmpty {
                    categoryBreakdownSection(budget)
                }

                dailyAllowanceCard(budget)
            }
            .padding(AppSpacing.md)
        }
    }

    private func overallProgressCard(_ budget: BudgetItem) -> some View {
        VStack(spacing: AppSpacing.md) {
            VStack(spacing: AppSpacing.xs) {
                Text("Total Spent")
                    .font(AppTypography.caption)
                    .foregroundStyle(.secondary)
                Text(AmountFormatter.format(minorUnits: budget.spentMinor, currency: budget.currency))
                    .font(AppTypography.amountLarge)
                    .foregroundStyle(budgetColor(for: budget.progress))
                Text("of \(AmountFormatter.format(minorUnits: budget.limitMinor, currency: budget.currency))")
                    .font(AppTypography.body)
                    .foregroundStyle(.secondary)
            }

            BudgetProgressBar(progress: budget.progress)
                .frame(height: 12)

            HStack {
                Label(budget.periodType.capitalized, systemImage: "calendar")
                    .font(AppTypography.caption)
                    .foregroundStyle(.secondary)
                Spacer()
                Text("\(Int(budget.progress * 100))% used")
                    .font(AppTypography.caption)
                    .foregroundStyle(budgetColor(for: budget.progress))
            }
        }
        .padding(AppSpacing.md)
        .background(.regularMaterial)
        .clipShape(RoundedRectangle(cornerRadius: AppCornerRadius.large))
    }

    private func categoryBreakdownSection(_ budget: BudgetItem) -> some View {
        VStack(alignment: .leading, spacing: AppSpacing.sm) {
            Text("Category Breakdown")
                .font(AppTypography.headline)
                .padding(.horizontal, AppSpacing.xs)

            ForEach(budget.categoryBreakdowns) { cat in
                categoryRow(cat, currency: budget.currency)
            }
        }
    }

    private func categoryRow(_ cat: BudgetCategoryBreakdown, currency: String) -> some View {
        VStack(spacing: AppSpacing.sm) {
            HStack {
                Circle()
                    .fill(AppColors.categoryColor(for: cat.categoryName))
                    .frame(width: 10, height: 10)
                Text(cat.categoryName)
                    .font(AppTypography.body)
                Spacer()
                Text(AmountFormatter.format(minorUnits: cat.spentMinor, currency: currency))
                    .font(AppTypography.amountSmall)
                    .foregroundStyle(budgetColor(for: cat.progress))
                Text("/ \(AmountFormatter.format(minorUnits: cat.limitMinor))")
                    .font(AppTypography.caption)
                    .foregroundStyle(.secondary)
            }

            BudgetProgressBar(progress: cat.progress)
                .frame(height: 6)
        }
        .padding(AppSpacing.sm)
        .background(.regularMaterial)
        .clipShape(RoundedRectangle(cornerRadius: AppCornerRadius.small))
    }

    private func dailyAllowanceCard(_ budget: BudgetItem) -> some View {
        let remaining = budget.remainingMinor
        let daysLeft = max(1, daysRemainingInPeriod(budget))

        let dailyAllowance = remaining / Int64(daysLeft)

        return VStack(spacing: AppSpacing.sm) {
            HStack {
                Image(systemName: "calendar.badge.clock")
                    .foregroundStyle(.secondary)
                Text("Daily Allowance")
                    .font(AppTypography.headline)
                Spacer()
            }
            HStack(alignment: .firstTextBaseline) {
                Text(AmountFormatter.format(minorUnits: dailyAllowance, currency: budget.currency))
                    .font(AppTypography.amountMedium)
                    .foregroundStyle(budgetColor(for: budget.progress))
                Text("/ day")
                    .font(AppTypography.caption)
                    .foregroundStyle(.secondary)
                Spacer()
                Text("\(daysLeft) days left")
                    .font(AppTypography.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(AppSpacing.md)
        .background(.regularMaterial)
        .clipShape(RoundedRectangle(cornerRadius: AppCornerRadius.large))
    }

    private func daysRemainingInPeriod(_ budget: BudgetItem) -> Int {
        let now = Date()
        let calendar = Calendar.current
        let endOfMonth = calendar.date(byAdding: .month, value: 1, to: calendar.startOfDay(for: calendar.date(from: calendar.dateComponents([.year, .month], from: now))!))!
        let days = calendar.dateComponents([.day], from: now, to: endOfMonth).day ?? 1
        return max(1, days)
    }
}
