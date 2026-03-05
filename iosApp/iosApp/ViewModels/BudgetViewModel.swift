import Foundation
import Shared

struct BudgetItem: Identifiable {
    let id: Int64
    let name: String
    let limitMinor: Int64
    let spentMinor: Int64
    let periodType: String
    let groupType: String
    let currency: String
    let isActive: Bool
    let categoryBreakdowns: [BudgetCategoryBreakdown]

    var progress: Double {
        guard limitMinor > 0 else { return 0 }
        return Double(spentMinor) / Double(limitMinor)
    }

    var remainingMinor: Int64 {
        max(0, limitMinor - spentMinor)
    }
}

struct BudgetCategoryBreakdown: Identifiable {
    var id: String { categoryName }
    let categoryName: String
    let limitMinor: Int64
    let spentMinor: Int64

    var progress: Double {
        guard limitMinor > 0 else { return 0 }
        return Double(spentMinor) / Double(limitMinor)
    }
}

class BudgetViewModel: ObservableObject {
    private let facade = PennyWiseSharedFacade()

    @Published var budgets: [BudgetItem] = []
    @Published var selectedBudget: BudgetItem?
    @Published var categories: [String] = []

    func loadBudgets() {
        let shared = facade.getAllBudgets()
        budgets = shared.map { mapBudget($0) }
    }

    func loadBudgetDetail(id: Int64) {
        if let shared = facade.getBudgetDetail(budgetId: id) {
            selectedBudget = mapBudget(shared)
        }
    }

    func loadCategories() {
        categories = facade.getAllCategories().map { $0.name }
    }

    func createBudget(
        name: String,
        limitMinor: Int64,
        periodType: String,
        startEpochMillis: Int64,
        endEpochMillis: Int64,
        groupType: String,
        currency: String,
        categoryLimits: [(String, Int64)]
    ) -> Bool {
        let breakdowns = categoryLimits.map { pair in
            SharedBudgetCategoryBreakdown(
                categoryName: pair.0,
                limitMinor: pair.1,
                spentMinor: 0
            )
        }
        let result = facade.createBudget(
            name: name,
            limitMinor: limitMinor,
            periodType: periodType,
            startEpochMillis: startEpochMillis,
            endEpochMillis: endEpochMillis,
            groupType: groupType,
            currency: currency,
            categoryLimits: breakdowns
        )
        if result > 0 {
            loadBudgets()
            return true
        }
        return false
    }

    func updateBudget(
        id: Int64,
        name: String,
        limitMinor: Int64,
        periodType: String,
        startEpochMillis: Int64,
        endEpochMillis: Int64,
        groupType: String,
        currency: String,
        categoryLimits: [(String, Int64)]
    ) -> Bool {
        let breakdowns = categoryLimits.map { pair in
            SharedBudgetCategoryBreakdown(
                categoryName: pair.0,
                limitMinor: pair.1,
                spentMinor: 0
            )
        }
        let success = facade.updateBudget(
            id: id,
            name: name,
            limitMinor: limitMinor,
            periodType: periodType,
            startEpochMillis: startEpochMillis,
            endEpochMillis: endEpochMillis,
            groupType: groupType,
            currency: currency,
            categoryLimits: breakdowns
        )
        if success {
            loadBudgets()
        }
        return success
    }

    func deleteBudget(id: Int64) -> Bool {
        let success = facade.deleteBudget(id: id)
        if success {
            loadBudgets()
        }
        return success
    }

    private func mapBudget(_ shared: SharedBudgetItem) -> BudgetItem {
        BudgetItem(
            id: shared.id,
            name: shared.name,
            limitMinor: shared.limitMinor,
            spentMinor: shared.spentMinor,
            periodType: shared.periodType,
            groupType: shared.groupType,
            currency: shared.currency,
            isActive: shared.isActive,
            categoryBreakdowns: shared.categoryBreakdowns.map { cat in
                BudgetCategoryBreakdown(
                    categoryName: cat.categoryName,
                    limitMinor: cat.limitMinor,
                    spentMinor: cat.spentMinor
                )
            }
        )
    }
}
