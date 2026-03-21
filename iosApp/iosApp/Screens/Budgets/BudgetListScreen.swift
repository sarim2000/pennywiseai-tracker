import SwiftUI

struct BudgetListScreen: View {
    @StateObject private var viewModel = BudgetViewModel()
    @State private var showingAddBudget = false

    var body: some View {
        Group {
            if viewModel.budgets.isEmpty {
                emptyState
            } else {
                budgetList
            }
        }
        .navigationTitle("Budgets")
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button {
                    showingAddBudget = true
                } label: {
                    Image(systemName: "plus")
                }
            }
        }
        .sheet(isPresented: $showingAddBudget) {
            NavigationStack {
                AddEditBudgetScreen(viewModel: viewModel)
            }
        }
        .onAppear {
            viewModel.loadBudgets()
        }
    }

    private var emptyState: some View {
        VStack(spacing: AppSpacing.md) {
            Image(systemName: "chart.pie")
                .font(.system(size: 48))
                .foregroundStyle(.secondary)
            Text("No Budgets Yet")
                .font(AppTypography.title)
            Text("Create a budget to track your spending by category.")
                .font(AppTypography.body)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
            Button {
                showingAddBudget = true
            } label: {
                Label("Create Budget", systemImage: "plus.circle.fill")
            }
            .buttonStyle(.borderedProminent)
        }
        .padding(AppSpacing.xl)
    }

    private var budgetList: some View {
        List {
            ForEach(viewModel.budgets) { budget in
                NavigationLink {
                    BudgetDetailScreen(budgetId: budget.id, viewModel: viewModel)
                } label: {
                    BudgetRowView(budget: budget)
                }
                .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                    Button(role: .destructive) {
                        _ = viewModel.deleteBudget(id: budget.id)
                    } label: {
                        Label("Delete", systemImage: "trash")
                    }
                }
            }
        }
        .listStyle(.insetGrouped)
    }
}

// MARK: - Budget Row

private struct BudgetRowView: View {
    let budget: BudgetItem

    var body: some View {
        VStack(alignment: .leading, spacing: AppSpacing.sm) {
            HStack {
                VStack(alignment: .leading, spacing: AppSpacing.xs) {
                    Text(budget.name)
                        .font(AppTypography.headline)
                    Text(budget.periodType.capitalized)
                        .font(AppTypography.caption)
                        .foregroundStyle(.secondary)
                }
                Spacer()
                if !budget.isActive {
                    Text("Inactive")
                        .font(AppTypography.caption2)
                        .padding(.horizontal, AppSpacing.sm)
                        .padding(.vertical, AppSpacing.xs)
                        .background(.secondary.opacity(0.15))
                        .clipShape(Capsule())
                }
            }

            BudgetProgressBar(progress: budget.progress)

            HStack {
                Text(AmountFormatter.format(minorUnits: budget.spentMinor, currency: budget.currency))
                    .font(AppTypography.amountSmall)
                    .foregroundStyle(budgetColor(for: budget.progress))
                Spacer()
                Text("of \(AmountFormatter.format(minorUnits: budget.limitMinor, currency: budget.currency))")
                    .font(AppTypography.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(.vertical, AppSpacing.xs)
    }
}

// MARK: - Progress Bar

struct BudgetProgressBar: View {
    let progress: Double

    var body: some View {
        GeometryReader { geometry in
            ZStack(alignment: .leading) {
                RoundedRectangle(cornerRadius: 4)
                    .fill(.secondary.opacity(0.15))
                RoundedRectangle(cornerRadius: 4)
                    .fill(budgetColor(for: progress))
                    .frame(width: geometry.size.width * min(progress, 1.0))
            }
        }
        .frame(height: 8)
    }
}

func budgetColor(for ratio: Double) -> Color {
    AppColors.budgetColor(for: ratio)
}
