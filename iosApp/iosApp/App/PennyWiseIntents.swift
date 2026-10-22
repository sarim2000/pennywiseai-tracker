import AppIntents
import Foundation
import Shared
import UserNotifications

// Shortcuts / Siri entry points. Both run in the background
// (openAppWhenRun = false) and answer with one line built only from what was
// just saved — never balances, totals or other stored data — so app lock does
// not need to gate them. A bank SMS arrives while the phone is locked, and the
// automation must still save it.

struct AddBankSmsIntent: AppIntent {
    static var title: LocalizedStringResource = "Add bank SMS to PennyWise"
    static var description = IntentDescription("Reads a bank SMS and saves the transaction in it.")
    static var openAppWhenRun = false

    @Parameter(title: "Message", inputOptions: String.IntentInputOptions(multiline: true))
    var message: String

    @Parameter(title: "Sender", description: "The bank's sender ID, e.g. AX-HDFCBK-S. Optional.")
    var sender: String?

    static var parameterSummary: some ParameterSummary {
        Summary("Add \(\.$message) to PennyWise") {
            \.$sender
        }
    }

    func perform() async throws -> some IntentResult & ReturnsValue<String> & ProvidesDialog {
        let outcome = BankSmsImport.run(sender: sender, body: message)
        if let alert = outcome.alert {
            await BankSmsImport.notify(alert)
        }
        return .result(value: outcome.message, dialog: IntentDialog(stringLiteral: outcome.message))
    }
}

struct LogExpenseIntent: AppIntent {
    static var title: LocalizedStringResource = "Log expense in PennyWise"
    static var description = IntentDescription("Adds an expense in your default currency.")
    static var openAppWhenRun = false
    // Writing an expense from a locked phone (Siri) needs the owner. iOS 16 has no such policy.
    @available(iOS 17.0, *)
    static var authenticationPolicy: IntentAuthenticationPolicy { .requiresAuthentication }

    @Parameter(title: "Amount")
    var amount: Double

    @Parameter(title: "Merchant")
    var merchant: String

    // Free text matched to an existing category. No options list: a provider would read the
    // user's categories before any unlock, and an options provider can't require one.
    @Parameter(title: "Category")
    var category: String?

    static var parameterSummary: some ParameterSummary {
        Summary("Log \(\.$amount) at \(\.$merchant)") {
            \.$category
        }
    }

    func perform() async throws -> some IntentResult & ReturnsValue<String> & ProvidesDialog {
        let message = Self.log(amount: amount, merchant: merchant, category: category)
        return .result(value: message, dialog: IntentDialog(stringLiteral: message))
    }

    /// Goes through the same manual-add path as the add-transaction screen.
    static func log(amount: Double, merchant: String, category: String?) -> String {
        let name = merchant.trimmingCharacters(in: .whitespacesAndNewlines)
        // A Shortcut can pass a calculated value: reject NaN/inf/huge before Int64() traps on it.
        let scaled = (amount * 100).rounded()
        guard scaled.isFinite, scaled >= 1, scaled < 1e15, !name.isEmpty else {
            return "Give an amount above zero and a merchant"
        }
        let amountMinor = Int64(scaled)
        let facade = PennyWiseSharedFacade.companion.shared
        _ = facade.initializeAndLoadHome() // seeds categories if the app has never been opened

        let currency = UserDefaults.standard.string(forKey: "displayCurrency") ?? "INR"
        // A spoken category is matched to an existing one; otherwise the merchant picks it, as for SMS.
        let spoken = category?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let known = facade.getAllCategories().first { $0.name.lowercased() == spoken }?.name
        let resolved = known ?? SharedCategoryMapping.shared.determineCategory(merchantName: name, transactionType: "EXPENSE")

        let snapshot = facade.addExpenseAndLoadHome(
            merchantName: name, category: resolved, amountMinor: amountMinor, note: nil, currency: currency
        )
        if let error = snapshot.lastError { return error }
        return "Logged \(CurrencyFormatter.format(amountMinor: amountMinor, currencyCode: currency)) at \(name)"
    }
}

struct PennyWiseShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(
            intent: AddBankSmsIntent(),
            phrases: ["Add bank SMS to \(.applicationName)", "Save a bank message in \(.applicationName)"]
        )
        AppShortcut(
            intent: LogExpenseIntent(),
            phrases: ["Log an expense in \(.applicationName)", "Add an expense to \(.applicationName)"]
        )
    }
}

/// Runs the shared ImportBankSmsUseCase and words the result. Used by the intent
/// and by the setup screen's sample test.
enum BankSmsImport {
    struct Outcome {
        let message: String
        /// The local-notification text, only for a newly saved transaction.
        let alert: String?
        let savedTransactionId: Int64?
    }

    static func run(sender: String?, body: String) -> Outcome {
        let result: BankSmsImportResult
        do {
            result = try PennyWiseSharedFacade.companion.shared.importBankSms(
                sender: sender,
                body: body,
                receivedAtEpochMillis: Int64(Date().timeIntervalSince1970 * 1000)
            )
        } catch {
            return Outcome(message: "Couldn't save this message. Try again.", alert: nil, savedTransactionId: nil)
        }

        switch result {
        case let saved as BankSmsImportResult.Saved:
            let txn = saved.transaction
            let amount = CurrencyFormatter.format(amountMinor: txn.amountMinor, currencyCode: txn.currency)
            let isIncome = txn.transactionType.name == "INCOME"
            let place = "\(isIncome ? "from" : "at") \(txn.merchantName)"
            let alert = "\(isIncome ? "Received" : "Spent") \(amount) \(place)"
            return Outcome(message: "Saved \(amount) \(place)", alert: alert, savedTransactionId: txn.id)
        case is BankSmsImportResult.Duplicate:
            return Outcome(message: "Already saved", alert: nil, savedTransactionId: nil)
        case let unparsed as BankSmsImportResult.Unparsed:
            return Outcome(message: "No transaction found in this \(unparsed.bankName) message", alert: nil, savedTransactionId: nil)
        default:
            return Outcome(message: "Not a bank transaction", alert: nil, savedTransactionId: nil)
        }
    }

    /// Mirrors Android's new-transaction alert. Never asks for permission from the background.
    static func notify(_ text: String) async {
        let center = UNUserNotificationCenter.current()
        let status = await center.notificationSettings().authorizationStatus
        guard status == .authorized || status == .provisional || status == .ephemeral else { return }
        let content = UNMutableNotificationContent()
        content.body = text
        try? await center.add(UNNotificationRequest(identifier: UUID().uuidString, content: content, trigger: nil))
    }
}
