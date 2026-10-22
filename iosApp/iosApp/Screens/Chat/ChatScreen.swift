import Shared
import SwiftUI
#if canImport(FoundationModels)

@available(iOS 26.0, *)
struct ChatScreen: View {
    @StateObject private var viewModel = ChatViewModel()
    @State private var input = ""
    @FocusState private var inputFocused: Bool
    @Environment(\.isAmoledActive) private var isAmoled

    private static let examples = [
        "Coffee 120 at Starbucks",
        "How much did I spend on food this month?",
        "Delete the tea from today",
    ]

    var body: some View {
        VStack(spacing: 0) {
            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: AppSpacing.sm) {
                        if viewModel.lines.isEmpty && !viewModel.isResponding {
                            emptyState
                        }
                        ForEach(viewModel.lines) { line in
                            bubble(line.text, isUser: line.isUser)
                        }
                        if let streaming = viewModel.streaming {
                            if streaming.isEmpty {
                                ProgressView().padding(AppSpacing.md)
                            } else {
                                bubble(streaming, isUser: false)
                            }
                        }
                        if let action = viewModel.pending {
                            ConfirmCard(action: action, isBusy: viewModel.isConfirming,
                                        onConfirm: viewModel.confirm, onCancel: viewModel.cancel)
                        }
                        if let error = viewModel.error {
                            Text(error)
                                .font(AppTypography.caption)
                                .foregroundStyle(AppColors.expense)
                                .padding(.horizontal, AppSpacing.md)
                        }
                        Color.clear.frame(height: 1).id("bottom")
                    }
                    .padding(.vertical, AppSpacing.md)
                }
                .scrollDismissesKeyboard(.interactively)
                .onChange(of: viewModel.lines) { _, _ in withAnimation { proxy.scrollTo("bottom") } }
                .onChange(of: viewModel.streaming) { _, _ in proxy.scrollTo("bottom") }
                .onChange(of: viewModel.pending == nil) { _, _ in withAnimation { proxy.scrollTo("bottom") } }
            }
            inputBar
        }
        .background(AppColors.background(isAmoled: isAmoled))
        .navigationTitle("PennyWise AI")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    viewModel.clear()
                } label: {
                    Label("New chat", systemImage: "square.and.pencil")
                }
                .disabled(viewModel.isResponding || (viewModel.lines.isEmpty && viewModel.error == nil))
            }
        }
    }

    private var emptyState: some View {
        VStack(spacing: AppSpacing.md) {
            Image(systemName: "sparkles")
                .font(.system(size: 40))
                .foregroundStyle(.tint)
            Text("Log a spend or ask about your money")
                .font(AppTypography.headline)
                .multilineTextAlignment(.center)
            VStack(spacing: AppSpacing.sm) {
                ForEach(Self.examples, id: \.self) { example in
                    Button {
                        send(example)
                    } label: {
                        Text(example)
                            .font(AppTypography.body)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, AppSpacing.sm + 2)
                            .padding(.horizontal, AppSpacing.md)
                            .background(AppColors.surface(isAmoled: isAmoled),
                                        in: RoundedRectangle(cornerRadius: AppCornerRadius.medium))
                    }
                    .buttonStyle(.plain)
                }
            }
            Label("Runs on your iPhone, nothing leaves it", systemImage: "lock.fill")
                .font(AppTypography.caption)
                .foregroundStyle(.secondary)
            Text("Nothing is saved, changed or deleted until you tap the card.")
                .font(AppTypography.caption)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
        }
        .padding(.horizontal, AppSpacing.lg)
        .padding(.top, AppSpacing.xl)
        .frame(maxWidth: .infinity)
    }

    private func bubble(_ text: String, isUser: Bool) -> some View {
        HStack {
            if isUser { Spacer(minLength: AppSpacing.xl) }
            Text(text)
                .font(AppTypography.body)
                .textSelection(.enabled)
                .padding(.vertical, AppSpacing.sm + 2)
                .padding(.horizontal, AppSpacing.md - 2)
                .foregroundStyle(isUser ? Color.white : Color.primary)
                .background(
                    isUser ? AnyShapeStyle(.tint) : AnyShapeStyle(AppColors.surface(isAmoled: isAmoled)),
                    in: RoundedRectangle(cornerRadius: AppCornerRadius.large)
                )
            if !isUser { Spacer(minLength: AppSpacing.xl) }
        }
        .padding(.horizontal, AppSpacing.md)
    }

    private var inputBar: some View {
        HStack(spacing: AppSpacing.sm) {
            TextField("Message", text: $input, axis: .vertical)
                .lineLimit(1...4)
                .focused($inputFocused)
                .padding(.vertical, AppSpacing.sm)
                .padding(.horizontal, AppSpacing.md - 4)
                .background(AppColors.surface(isAmoled: isAmoled),
                            in: RoundedRectangle(cornerRadius: AppCornerRadius.xl))
                .onSubmit { send(input) }
            Button {
                send(input)
            } label: {
                Image(systemName: "arrow.up.circle.fill")
                    .font(.system(size: 32))
            }
            .disabled(input.trimmingCharacters(in: .whitespaces).isEmpty || viewModel.isResponding)
            .accessibilityLabel("Send")
        }
        .padding(.horizontal, AppSpacing.md)
        .padding(.vertical, AppSpacing.sm)
        .background(.bar)
    }

    private func send(_ text: String) {
        #if DEBUG
        if ProcessInfo.processInfo.arguments.contains("-PennyAIDemo") {
            demoSend(text)
            input = ""
            return
        }
        #endif
        viewModel.send(text)
        input = ""
    }

    #if DEBUG
    /// Simulator without Apple Intelligence: stands in for the model's tool call
    /// so the real shared proposal, card and confirm paths can be checked.
    private func demoSend(_ text: String) {
        let lower = text.lowercased()
        if lower.hasPrefix("delete") {
            viewModel.debugProposeDelete(text, words: lower)
        } else if lower.hasPrefix("how much") {
            viewModel.debugAnswer(text, category: lower.contains("food") ? "Food & Dining" : "Others")
        } else if let amount = text.split(separator: " ").compactMap({ Double($0) }).first {
            let merchant = text.components(separatedBy: " at ").last ?? text
            viewModel.debugPropose(text, amount: amount, merchant: merchant)
        }
    }
    #endif
}

/// The card for a proposed add, update or delete. Only its confirm button writes.
@available(iOS 26.0, *)
private struct ConfirmCard: View {
    let action: PendingChatAction
    let isBusy: Bool
    let onConfirm: () -> Void
    let onCancel: () -> Void
    @Environment(\.isAmoledActive) private var isAmoled

    private struct Model {
        let title: String
        let verb: String
        let amount: String
        let isIncome: Bool
        let rows: [(String, String)]
        let destructive: Bool
    }

    private var model: Model {
        switch action {
        case let add as PendingChatAction.Add:
            let d = add.draft
            let income = d.type.name == "INCOME"
            return Model(
                title: income ? "Add this income?" : "Add this expense?", verb: "Add",
                amount: CurrencyFormatter.format(amountMinor: d.amountMinor, currencyCode: d.currency), isIncome: income,
                rows: [("Merchant", d.merchant), ("Category", d.category), ("Date", "Today"), ("Account", d.accountLabel)],
                destructive: false
            )
        case let delete as PendingChatAction.Delete:
            return Model(title: "Delete this transaction?", verb: "Delete",
                         amount: amount(of: delete.transaction), isIncome: delete.transaction.transactionType.name == "INCOME",
                         rows: rows(of: delete.transaction), destructive: true)
        case let update as PendingChatAction.Update:
            var rows = rows(of: update.transaction)
            if let m = update.newMerchant { rows.insert(("New merchant", m), at: 0) }
            if let c = update.newCategory { rows.insert(("New category", c), at: 0) }
            return Model(title: "Update this transaction?", verb: "Update",
                         amount: amount(of: update.transaction), isIncome: update.transaction.transactionType.name == "INCOME",
                         rows: rows, destructive: false)
        default:
            return Model(title: "", verb: "OK", amount: "", isIncome: false, rows: [], destructive: false)
        }
    }

    /// An existing row in its own currency.
    private func amount(of t: SharedTransaction) -> String {
        CurrencyFormatter.format(amountMinor: t.amountMinor, currencyCode: t.currency)
    }

    private func rows(of t: SharedTransaction) -> [(String, String)] {
        let date = Date(timeIntervalSince1970: TimeInterval(t.occurredAtEpochMillis) / 1000)
        let account = t.bankName.map { name in t.accountLast4.map { "\(name) ••\($0)" } ?? name } ?? "Cash / manual"
        return [("Merchant", t.merchantName), ("Category", t.category),
                ("Date", date.formatted(date: .abbreviated, time: .omitted)), ("Account", account)]
    }

    var body: some View {
        let m = model
        VStack(alignment: .leading, spacing: AppSpacing.sm) {
            Text(m.title)
                .font(AppTypography.caption)
                .foregroundStyle(.secondary)
            Text(m.amount)
                .font(AppTypography.amountLarge)
                .foregroundStyle(m.isIncome ? AppColors.income : AppColors.expense)
            ForEach(m.rows, id: \.0) { label, value in
                HStack(alignment: .firstTextBaseline) {
                    Text(label).foregroundStyle(.secondary)
                    Spacer()
                    Text(value).multilineTextAlignment(.trailing)
                }
                .font(AppTypography.body)
            }
            HStack(spacing: AppSpacing.sm) {
                Button(action: onCancel) {
                    Text("Cancel").frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
                Button(role: m.destructive ? .destructive : nil, action: onConfirm) {
                    Group {
                        if isBusy { ProgressView() } else { Text(m.verb).bold() }
                    }
                    .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .tint(m.destructive ? AppColors.expense : nil)
            }
            .controlSize(.large)
            .disabled(isBusy)
            .padding(.top, AppSpacing.xs)
        }
        .padding(AppSpacing.md)
        .background(AppColors.surface(isAmoled: isAmoled),
                    in: RoundedRectangle(cornerRadius: AppCornerRadius.large))
        .padding(.horizontal, AppSpacing.md)
    }
}
#endif
