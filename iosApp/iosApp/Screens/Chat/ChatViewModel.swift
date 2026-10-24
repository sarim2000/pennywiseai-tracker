import Foundation
import Shared
#if canImport(FoundationModels)
import FoundationModels

// The chat's side of Android's LlmRepository on Apple's on-device model. The
// tools mirror Android's PennyWiseTools: lookups answer from the database; an
// add, update or delete only proposes a card (shared ChatAssistant), and
// nothing is written until the user taps its confirm button.

struct ChatLine: Identifiable, Equatable {
    let id = UUID()
    let text: String
    let isUser: Bool
}

/// What the tools share with the view model during one turn.
final class ChatToolContext: @unchecked Sendable {
    private let lock = NSLock()
    private var _userMessage = ""
    private var _replies: [String] = []
    private var _action: PendingChatAction?

    let currency: String
    init(currency: String) { self.currency = currency }

    var userMessage: String { lock.withLock { _userMessage } }

    func startTurn(_ message: String) {
        lock.withLock { _userMessage = message; _replies = []; _action = nil }
    }

    func record(_ result: ChatToolResult) -> String {
        lock.withLock {
            _replies.append(result.reply)
            if let action = result.action { _action = action }
        }
        return result.reply
    }

    func record(_ reply: String) -> String {
        lock.withLock { _replies.append(reply) }
        return reply
    }

    /// Tool replies of the turn, and the last proposal (a later one replaces an earlier one, as on Android).
    func endTurn() -> (replies: [String], action: PendingChatAction?) {
        lock.withLock { (_replies, _action) }
    }
}

@available(iOS 26.0, *)
struct AddTransactionTool: Tool {
    let name = "addTransaction"
    let description = "Record a transaction the user is telling you about: money they spent, paid, bought, or received."
    let context: ChatToolContext

    @Generable
    struct Arguments {
        @Guide(description: "Amount of money as a number, without currency symbol")
        var amount: Double
        @Guide(description: "The shop, service or person the money went to or came from. Never a bank or card.")
        var merchant: String
        @Guide(description: "One of the known category names")
        var category: String
        @Guide(description: "EXPENSE for money spent or paid (the usual case), INCOME only for money received such as salary, refund or cashback", .anyOf(["EXPENSE", "INCOME"]))
        var type: String
        @Guide(description: "The bank, card or cash the user mentioned, or empty if not mentioned")
        var account: String
    }

    func call(arguments: Arguments) async throws -> String {
        context.record(try PennyWiseSharedFacade.companion.shared.chatProposeAdd(
            amount: arguments.amount, merchant: arguments.merchant, category: arguments.category,
            type: arguments.type, account: arguments.account,
            userMessage: context.userMessage, currency: context.currency
        ))
    }
}

/// deleteTransaction and updateTransaction share their lookup arguments.
@available(iOS 26.0, *)
struct DeleteTransactionTool: Tool {
    let name = "deleteTransaction"
    let description = "Delete a transaction the user recorded earlier, e.g. 'delete the starbucks coffee from yesterday'."
    let context: ChatToolContext

    @Generable
    struct Arguments {
        @Guide(description: "Merchant, shop or description words the user used to identify it")
        var merchant: String
        @Guide(description: "Amount if the user mentioned one, else 0")
        var amount: Double
        @Guide(description: "How many days ago: 0 for today, 1 for yesterday, -1 if not mentioned")
        var daysAgo: Int
    }

    func call(arguments: Arguments) async throws -> String {
        context.record(try PennyWiseSharedFacade.companion.shared.chatProposeChange(
            delete: true, words: arguments.merchant, amount: arguments.amount,
            daysAgo: Int32(clamping: arguments.daysAgo), newCategory: "", newMerchant: ""
        ))
    }
}

@available(iOS 26.0, *)
struct UpdateTransactionTool: Tool {
    let name = "updateTransaction"
    let description = "Change the category or merchant name of a transaction the user recorded earlier, e.g. 'the uber yesterday was actually Transportation'."
    let context: ChatToolContext

    @Generable
    struct Arguments {
        @Guide(description: "Merchant, shop or description words the user used to identify it")
        var merchant: String
        @Guide(description: "Amount if the user mentioned one, else 0")
        var amount: Double
        @Guide(description: "How many days ago: 0 for today, 1 for yesterday, -1 if not mentioned")
        var daysAgo: Int
        @Guide(description: "The new category, or empty to keep it")
        var newCategory: String
        @Guide(description: "The new merchant name, or empty to keep it")
        var newMerchant: String
    }

    func call(arguments: Arguments) async throws -> String {
        context.record(try PennyWiseSharedFacade.companion.shared.chatProposeChange(
            delete: false, words: arguments.merchant, amount: arguments.amount,
            daysAgo: Int32(clamping: arguments.daysAgo),
            newCategory: arguments.newCategory, newMerchant: arguments.newMerchant
        ))
    }
}

@available(iOS 26.0, *)
struct MonthSummaryTool: Tool {
    let name = "monthSummary"
    let description = "Look up this month's totals: money spent, money received and number of transactions."
    let context: ChatToolContext

    @Generable
    struct Arguments {}

    func call(arguments: Arguments) async throws -> String {
        context.record(try PennyWiseSharedFacade.companion.shared.chatMonthSummary())
    }
}

@available(iOS 26.0, *)
struct SpendingByCategoryTool: Tool {
    let name = "spendingByCategory"
    let description = "Look up how much the user has spent in a category this month."
    let context: ChatToolContext

    @Generable
    struct Arguments {
        @Guide(description: "One of the known category names")
        var category: String
    }

    func call(arguments: Arguments) async throws -> String {
        context.record(try PennyWiseSharedFacade.companion.shared.chatSpendingByCategory(category: arguments.category))
    }
}

@available(iOS 26.0, *)
@MainActor
final class ChatViewModel: ObservableObject {
    @Published private(set) var lines: [ChatLine] = []
    /// The reply as it streams in; nil between turns.
    @Published private(set) var streaming: String?
    @Published private(set) var pending: PendingChatAction?
    @Published private(set) var isConfirming = false
    @Published var error: String?

    private var session: LanguageModelSession?
    private var context = ChatToolContext(currency: "INR")
    /// Everything the session holds, for the token budget.
    private var contextTexts: [String] = []
    private let facade = PennyWiseSharedFacade.companion.shared

    /// What happened to the last card, told to the model with the next message: the
    /// session can't hear button taps, and "did that save?" must get the right answer.
    private var cardOutcome: String?

    var isResponding: Bool { streaming != nil }
    /// Sending, New chat and Cancel wait while a card's write is in flight.
    var isBusy: Bool { isResponding || isConfirming }

    func send(_ raw: String) {
        let text = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty, !isBusy else { return }
        // A new message supersedes a card still waiting; the model hears it was left.
        if pending != nil { cancel() }
        error = nil
        streaming = "" // busy from the tap on: a second tap can't start a second turn

        Task {
            do {
                let session = try await sessionForTurn()
                let prompt = cardOutcome.map { "(\($0))\n\(text)" } ?? text
                let contextSize = SystemLanguageModel.default.contextSize
                guard ChatPrompt.shared.fitsBudget(context: contextTexts, nextMessage: prompt, contextSize: Int32(clamping: contextSize)) else {
                    streaming = nil
                    error = Self.chatFull
                    return
                }
                cardOutcome = nil
                lines.append(ChatLine(text: text, isUser: true))
                context.startTurn(text)
                for try await snapshot in session.streamResponse(to: prompt) {
                    streaming = snapshot.content
                }
                finishTurn(prompt: prompt)
            } catch {
                streaming = nil
                self.error = Self.message(for: error)
            }
        }
    }

    private func finishTurn(prompt: String) {
        let (replies, action) = context.endTurn()
        var reply = (streaming ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        if let note = ChatAssistant.companion.nothingChangedNote(reply: reply, proposedSomething: action != nil) {
            reply += reply.isEmpty ? note : "\n\(note)"
        }
        streaming = nil
        if !reply.isEmpty { lines.append(ChatLine(text: reply, isUser: false)) }
        pending = action
        contextTexts += [prompt, reply] + replies
    }

    /// A new chat gets fresh instructions: today's date, this month, the user's lists (#959).
    private func sessionForTurn() async throws -> LanguageModelSession {
        if let session { return session }
        let currency = UserDefaults.standard.string(forKey: "displayCurrency") ?? "INR"
        let facade = self.facade
        let instructions = try await Task.detached { try facade.chatSystemPrompt() }.value
        let context = ChatToolContext(currency: currency)
        let tools: [any Tool] = [
            AddTransactionTool(context: context),
            SpendingByCategoryTool(context: context),
            MonthSummaryTool(context: context),
            UpdateTransactionTool(context: context),
            DeleteTransactionTool(context: context),
        ]
        let session = LanguageModelSession(tools: tools, instructions: instructions)
        self.context = context
        self.session = session
        contextTexts = [instructions]
        return session
    }

    func confirm() {
        guard let action = pending, !isBusy else { return }
        isConfirming = true
        let facade = self.facade
        Task {
            defer { isConfirming = false }
            do {
                let failure = try await Task.detached { try facade.chatConfirm(action: action) }.value
                pending = nil
                let line = failure.map { "Nothing changed: \($0)." } ?? Self.done(action)
                lines.append(ChatLine(text: line, isUser: false))
                cardOutcome = "The user tapped the card's button. \(line)"
            } catch {
                self.error = "Couldn't apply that: \(error.localizedDescription)"
            }
        }
    }

    func cancel() {
        guard pending != nil, !isConfirming else { return }
        pending = nil
        lines.append(ChatLine(text: "Cancelled. Nothing changed.", isUser: false))
        cardOutcome = "The user cancelled the card. Nothing was changed."
    }

    func clear() {
        guard !isBusy else { return }
        session = nil
        lines = []
        pending = nil
        error = nil
        contextTexts = []
        cardOutcome = nil
    }

    /// "Added ₹120 at Starbucks (Food & Dining)." Each amount in its own currency.
    private static func done(_ action: PendingChatAction) -> String {
        func money(_ minor: Int64, _ currency: String) -> String {
            CurrencyFormatter.format(amountMinor: minor, currencyCode: currency)
        }
        switch action {
        case let add as PendingChatAction.Add:
            let d = add.draft
            return "Added \(money(d.amountMinor, d.currency)) \(d.type.name == "INCOME" ? "from" : "at") \(d.merchant) (\(d.category))."
        case let delete as PendingChatAction.Delete:
            let t = delete.transaction
            return "Deleted \(money(t.amountMinor, t.currency)) at \(t.merchantName)."
        case let update as PendingChatAction.Update:
            let t = update.transaction
            return "Updated \(money(t.amountMinor, t.currency)) at \(update.newMerchant ?? t.merchantName)"
                + (update.newCategory.map { " → \($0)" } ?? "") + "."
        default:
            return "Done."
        }
    }

    static let chatFull = "Chat is full. Start a new chat to keep going."

    private static func message(for error: Error) -> String {
        if #available(iOS 27.0, *), let e = error as? LanguageModelError {
            if case .contextSizeExceeded = e { return chatFull }
            if case .guardrailViolation = e { return "I can't help with that one." }
        }
        if let e = error as? LanguageModelSession.GenerationError {
            switch e {
            case .exceededContextWindowSize: return chatFull
            case .guardrailViolation, .refusal: return "I can't help with that one."
            case .unsupportedLanguageOrLocale: return "PennyWise AI doesn't support this language yet."
            case .assetsUnavailable: return "Apple Intelligence is still getting ready. Try again in a few minutes."
            default: break
            }
        }
        return "PennyWise AI couldn't answer that. Try again."
    }

    #if DEBUG
    /// UI tests on a simulator without Apple Intelligence: shows a card from the
    /// real shared proposal path, as if the model had called addTransaction.
    func debugPropose(_ text: String, amount: Double, merchant: String) {
        lines.append(ChatLine(text: text, isUser: true))
        let result = try? facade.chatProposeAdd(
            amount: amount, merchant: merchant, category: "", type: "EXPENSE", account: "",
            userMessage: text, currency: UserDefaults.standard.string(forKey: "displayCurrency") ?? "INR"
        )
        lines.append(ChatLine(text: "Here's what I'll add. Check the card below.", isUser: false))
        pending = result?.action
    }

    func debugProposeDelete(_ text: String, words: String) {
        lines.append(ChatLine(text: text, isUser: true))
        let result = try? facade.chatProposeChange(delete: true, words: words, amount: 0, daysAgo: -1, newCategory: "", newMerchant: "")
        lines.append(ChatLine(text: result?.action == nil ? (result?.reply ?? "") : "Delete this one? Confirm below.", isUser: false))
        pending = result?.action
    }

    func debugAnswer(_ text: String, category: String) {
        lines.append(ChatLine(text: text, isUser: true))
        lines.append(ChatLine(text: (try? facade.chatSpendingByCategory(category: category)) ?? "", isUser: false))
    }
    #endif
}
#endif
