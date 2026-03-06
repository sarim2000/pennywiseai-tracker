import Foundation
import SwiftUI
import Shared

final class ExportImportManager: ObservableObject {
    private let facade = PennyWiseSharedFacade()

    @Published var isExporting = false
    @Published var isImporting = false
    @Published var statusMessage: String?

    // MARK: - Export

    func exportBackup(from viewController: UIViewController? = nil) {
        isExporting = true
        statusMessage = nil

        let transactions = facade.getAllTransactions()
        let categories = facade.getAllCategories()
        let budgets = facade.getAllBudgets()
        let accounts = facade.getAllAccounts()

        let backup = BackupPayload(
            version: 1,
            exportDate: ISO8601DateFormatter().string(from: Date()),
            transactions: transactions.map { TransactionBackup(from: $0) },
            categories: categories.map { CategoryBackup(from: $0) },
            budgets: budgets.map { BudgetBackup(from: $0) },
            accounts: accounts.map { AccountBackup(from: $0) }
        )

        do {
            let encoder = JSONEncoder()
            encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
            let data = try encoder.encode(backup)

            let fileName = "PennyWise-Backup-\(backupDateString()).json"
            let tempURL = FileManager.default.temporaryDirectory.appendingPathComponent(fileName)
            try data.write(to: tempURL)

            isExporting = false
            presentShareSheet(fileURL: tempURL, from: viewController)
        } catch {
            isExporting = false
            statusMessage = "Export failed: \(error.localizedDescription)"
        }
    }

    // MARK: - Import

    func importBackup(from url: URL) {
        isImporting = true
        statusMessage = nil

        let accessing = url.startAccessingSecurityScopedResource()
        defer {
            if accessing { url.stopAccessingSecurityScopedResource() }
        }

        do {
            let data = try Data(contentsOf: url)
            let decoder = JSONDecoder()
            let backup = try decoder.decode(BackupPayload.self, from: data)

            guard backup.version == 1 else {
                isImporting = false
                statusMessage = "Unsupported backup version \(backup.version)"
                return
            }

            var txImported = 0
            var catImported = 0
            var budImported = 0
            var accImported = 0

            for cat in backup.categories {
                let ok = facade.createCategory(
                    name: cat.name,
                    colorHex: cat.colorHex,
                    isIncome: cat.isIncome
                )
                if ok { catImported += 1 }
            }

            for acc in backup.accounts {
                let ok = facade.createAccount(
                    bankName: acc.bankName,
                    accountLast4: acc.accountLast4,
                    accountType: acc.accountType ?? "SAVINGS",
                    balanceMinor: acc.balanceMinor,
                    currency: acc.currency
                )
                if ok { accImported += 1 }
            }

            for bud in backup.budgets {
                let catLimits = bud.categoryBreakdowns.map { cb in
                    SharedBudgetCategoryBreakdown(
                        categoryName: cb.categoryName,
                        limitMinor: cb.limitMinor,
                        spentMinor: 0
                    )
                }
                let id = facade.createBudget(
                    name: bud.name,
                    limitMinor: bud.limitMinor,
                    periodType: bud.periodType,
                    startEpochMillis: 0,
                    endEpochMillis: Int64.max,
                    groupType: bud.groupType,
                    currency: bud.currency,
                    categoryLimits: catLimits
                )
                if id > 0 { budImported += 1 }
            }

            for tx in backup.transactions {
                let snapshot = facade.addTransactionAndLoadHome(
                    merchantName: tx.merchantName,
                    category: tx.category,
                    amountMinor: tx.amountMinor,
                    note: tx.note,
                    currency: tx.currency,
                    transactionType: tx.transactionType,
                    occurredAtEpochMillis: tx.occurredAtEpochMillis,
                    bankName: tx.bankName,
                    accountLast4: tx.accountLast4
                )
                if snapshot.lastError == nil { txImported += 1 }
            }

            isImporting = false
            statusMessage = "Imported \(txImported) transactions, \(catImported) categories, \(budImported) budgets, \(accImported) accounts"
        } catch {
            isImporting = false
            statusMessage = "Import failed: \(error.localizedDescription)"
        }
    }

    // MARK: - Helpers

    private func backupDateString() -> String {
        let fmt = DateFormatter()
        fmt.dateFormat = "yyyy-MM-dd"
        return fmt.string(from: Date())
    }

    private func presentShareSheet(fileURL: URL, from viewController: UIViewController?) {
        let vc = viewController ?? topViewController()
        guard let vc = vc else { return }

        let activityVC = UIActivityViewController(
            activityItems: [fileURL],
            applicationActivities: nil
        )
        if let popover = activityVC.popoverPresentationController {
            popover.sourceView = vc.view
            popover.sourceRect = CGRect(x: vc.view.bounds.midX, y: vc.view.bounds.midY, width: 0, height: 0)
            popover.permittedArrowDirections = []
        }
        vc.present(activityVC, animated: true)
    }

    private func topViewController() -> UIViewController? {
        guard let scene = UIApplication.shared.connectedScenes
            .compactMap({ $0 as? UIWindowScene })
            .first,
              let root = scene.windows.first(where: { $0.isKeyWindow })?.rootViewController
        else { return nil }

        var top = root
        while let presented = top.presentedViewController {
            top = presented
        }
        return top
    }
}

// MARK: - Backup Data Models

struct BackupPayload: Codable {
    let version: Int
    let exportDate: String
    let transactions: [TransactionBackup]
    let categories: [CategoryBackup]
    let budgets: [BudgetBackup]
    let accounts: [AccountBackup]
}

struct TransactionBackup: Codable {
    let merchantName: String
    let category: String
    let amountMinor: Int64
    let currency: String
    let transactionType: String
    let occurredAtEpochMillis: Int64
    let note: String?
    let bankName: String?
    let accountLast4: String?

    init(from item: SharedRecentTransactionItem) {
        merchantName = item.merchantName
        category = item.category
        amountMinor = item.amountMinor
        currency = item.currency
        transactionType = item.transactionType
        occurredAtEpochMillis = item.occurredAtEpochMillis
        note = item.note
        bankName = item.bankName
        accountLast4 = item.accountLast4
    }
}

struct CategoryBackup: Codable {
    let name: String
    let colorHex: String
    let isIncome: Bool
    let isSystem: Bool
    let displayOrder: Int32

    init(from item: SharedCategoryItem) {
        name = item.name
        colorHex = item.colorHex
        isIncome = item.isIncome
        isSystem = item.isSystem
        displayOrder = item.displayOrder
    }
}

struct BudgetBackup: Codable {
    let name: String
    let limitMinor: Int64
    let periodType: String
    let groupType: String
    let currency: String
    let isActive: Bool
    let categoryBreakdowns: [BudgetCategoryBackup]

    init(from item: SharedBudgetItem) {
        name = item.name
        limitMinor = item.limitMinor
        periodType = item.periodType
        groupType = item.groupType
        currency = item.currency
        isActive = item.isActive
        categoryBreakdowns = item.categoryBreakdowns.map { BudgetCategoryBackup(from: $0) }
    }
}

struct BudgetCategoryBackup: Codable {
    let categoryName: String
    let limitMinor: Int64

    init(from item: SharedBudgetCategoryBreakdown) {
        categoryName = item.categoryName
        limitMinor = item.limitMinor
    }
}

struct AccountBackup: Codable {
    let bankName: String
    let accountLast4: String
    let balanceMinor: Int64
    let currency: String
    let accountType: String?
    let isCreditCard: Bool

    init(from item: SharedAccountItem) {
        bankName = item.bankName
        accountLast4 = item.accountLast4
        balanceMinor = item.balanceMinor
        currency = item.currency
        accountType = item.accountType
        isCreditCard = item.isCreditCard
    }
}
