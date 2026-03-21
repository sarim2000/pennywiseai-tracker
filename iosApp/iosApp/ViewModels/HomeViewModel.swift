import Foundation
import Shared

class HomeViewModel: ObservableObject {
    let facade = PennyWiseSharedFacade.companion.shared

    @Published var categories: [String] = []
    @Published var recentTransactions: [SharedRecentTransactionItem] = []
    @Published var transactionCount: Int = 0
    @Published var subscriptionCount: Int = 0
    @Published var subscriptionMonthlyTotal: Int64 = 0
    @Published var budgetCount: Int = 0
    @Published var accountCount: Int = 0
    @Published var monthlyIncomeMinor: Int64 = 0
    @Published var monthlyExpenseMinor: Int64 = 0
    @Published var monthlyNetMinor: Int64 = 0
    @Published var budgets: [SharedBudgetItem] = []
    @Published var accounts: [SharedAccountItem] = []
    @Published var errorMessage: String?
    @Published var importResultText: String?

    func loadHome() {
        let snapshot = facade.initializeAndLoadHome()
        apply(snapshot: snapshot)
        budgets = facade.getAllBudgets()
        accounts = facade.getAllAccounts()
        let subs = facade.getAllSubscriptions()
        subscriptionMonthlyTotal = subs.filter { $0.state == "active" }
            .reduce(Int64(0)) { $0 + $1.amountMinor }
    }

    func addTransaction(
        merchantName: String,
        category: String,
        amountMinor: Int64,
        note: String?,
        currency: String,
        transactionType: String,
        occurredAt: Date,
        bankName: String?,
        accountLast4: String?
    ) {
        let snapshot = facade.addTransactionAndLoadHome(
            merchantName: merchantName,
            category: category,
            amountMinor: amountMinor,
            note: note,
            currency: currency,
            transactionType: transactionType,
            occurredAtEpochMillis: occurredAt.epochMillis,
            bankName: bankName,
            accountLast4: accountLast4
        )
        apply(snapshot: snapshot)
    }

    func updateTransaction(
        id: Int64,
        merchantName: String,
        category: String,
        amountMinor: Int64,
        note: String?,
        currency: String,
        transactionType: String,
        occurredAt: Date,
        bankName: String?,
        accountLast4: String?
    ) {
        let snapshot = facade.updateTransactionAndLoadHome(
            transactionId: id,
            merchantName: merchantName,
            category: category,
            amountMinor: amountMinor,
            note: note,
            currency: currency,
            transactionType: transactionType,
            occurredAtEpochMillis: occurredAt.epochMillis,
            bankName: bankName,
            accountLast4: accountLast4
        )
        apply(snapshot: snapshot)
    }

    func importStatementText(_ text: String) {
        let snapshot = facade.importStatementTextAndLoadHome(statementText: text)
        apply(snapshot: snapshot)
    }

    func addSubscription(merchantName: String, amountMinor: Int64, category: String, currency: String) {
        let snapshot = facade.addSubscriptionAndLoadHome(
            merchantName: merchantName,
            amountMinor: amountMinor,
            category: category,
            currency: currency
        )
        apply(snapshot: snapshot)
    }

    func deleteTransaction(id: Int64) {
        _ = facade.deleteTransaction(transactionId: id)
        loadHome()
    }

    private func apply(snapshot: SharedHomeSnapshot) {
        let snapshotCategories = snapshot.categories
        categories = snapshotCategories.isEmpty ? ["Others"] : snapshotCategories
        recentTransactions = snapshot.recentTransactions
        transactionCount = Int(snapshot.transactionCount)
        subscriptionCount = Int(snapshot.subscriptionCount)
        budgetCount = Int(snapshot.budgetCount)
        accountCount = Int(snapshot.accountCount)
        monthlyIncomeMinor = snapshot.monthlyIncomeMinor
        monthlyExpenseMinor = snapshot.monthlyExpenseMinor
        monthlyNetMinor = snapshot.monthlyNetMinor
        errorMessage = snapshot.lastError
        if snapshot.lastImportParsed > 0 {
            importResultText = "Imported \(snapshot.lastImportImported)/\(snapshot.lastImportParsed), skipped \(snapshot.lastImportSkipped)"
        } else {
            importResultText = nil
        }
    }
}
