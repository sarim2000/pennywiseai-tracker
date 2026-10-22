import SwiftUI
import Shared
import UserNotifications

/// Settings › Auto-add bank SMS: how to point a Shortcuts automation at
/// AddBankSmsIntent, plus a dry run on a sample message.
struct BankSmsSetupScreen: View {
    @Environment(\.isAmoledActive) private var isAmoled
    @State private var testResult: String?
    @State private var notificationStatus: UNAuthorizationStatus = .authorized
    @State private var copied: String?

    // Most-used Indian banks first. `phrase` appears in the bank's transaction texts (checked
    // against parser-core's sample SMS); `id` is what the parser matches as the sender (canHandle).
    private static let banks: [(bank: String, phrase: String, id: String)] = [
        ("Kotak Mahindra Bank", "Kotak", "KOTAK"),
        ("HDFC Bank", "HDFC Bank", "HDFCBK"),
        ("ICICI Bank", "ICICI Bank", "ICICIB"),
        ("State Bank of India", "SBI", "SBI"),
        ("Axis Bank", "Axis Bank", "AXISBK")
    ]

    // The Message trigger's Sender only takes contacts, so the trigger matches on text instead.
    private static let steps = [
        "Open the Shortcuts app, tap Automation, then tap +.",
        "Choose Message.",
        "Choose Message Contains and type your bank's name as it appears in its texts (below).",
        "Choose Run Immediately, then tap Next.",
        "Add the action \u{201C}Add bank SMS to PennyWise\u{201D}. Set Message to Shortcut Input, then tap the arrow and set Sender to your bank's sender ID (below)."
    ]

    var body: some View {
        List {
            Section {
                Text("iPhone apps can't read your texts, but a Shortcuts automation can pass each bank SMS to PennyWise. It's read and saved on this phone only.")
                    .font(AppTypography.body)
                    .foregroundStyle(.secondary)
            }

            Section {
                ForEach(Array(Self.steps.enumerated()), id: \.offset) { index, step in
                    HStack(alignment: .firstTextBaseline, spacing: AppSpacing.md) {
                        Text("\(index + 1)")
                            .font(AppTypography.headline)
                            .foregroundStyle(.tint)
                            .frame(width: 20)
                        Text(step)
                            .font(AppTypography.body)
                    }
                }
                Button("Open Shortcuts") {
                    if let url = URL(string: "shortcuts://") { UIApplication.shared.open(url) }
                }
            } header: {
                Text("Set it up")
            } footer: {
                Text("OTPs and offers that contain the phrase also run the automation. PennyWise skips them with \u{201C}No transaction found\u{201D}; that's expected.")
            }

            Section {
                ForEach(Self.banks, id: \.bank) { bank in
                    VStack(alignment: .leading, spacing: AppSpacing.sm) {
                        Text(bank.bank)
                            .font(AppTypography.headline)
                        copyRow(label: "Message contains", value: bank.phrase)
                        copyRow(label: "Sender ID", value: bank.id)
                    }
                    .padding(.vertical, AppSpacing.xs)
                }
            } header: {
                Text("Your bank")
            } footer: {
                Text("Make one automation per bank. PennyWise reads 160+ banks; for another bank, use its name as it appears in its texts and the ID they come from.")
            }

            Section {
                Button("Test with a sample message") { runSample() }
                if let testResult {
                    Text(testResult)
                        .font(AppTypography.body)
                }
            } header: {
                Text("Try it")
            } footer: {
                Text("Runs a made-up HDFC Bank SMS through the same steps as the automation, then removes it again.")
            }

            if notificationStatus == .notDetermined || notificationStatus == .denied {
                Section {
                    Button("Allow notifications") { requestNotifications() }
                } footer: {
                    Text("Get an alert like \u{201C}Spent ₹450 at GroceryMart\u{201D} each time a bank SMS is saved.")
                }
            }
        }
        .scrollContentBackground(isAmoled ? .hidden : .visible)
        .background(isAmoled ? AppColors.amoledBackground : Color.clear)
        .navigationTitle("Auto-add bank SMS")
        .navigationBarTitleDisplayMode(.inline)
        .task { await refreshNotificationStatus() }
        // Back from the Settings app, where a denied permission gets switched on.
        .onReceive(NotificationCenter.default.publisher(for: UIApplication.willEnterForegroundNotification)) { _ in
            Task { await refreshNotificationStatus() }
        }
    }

    private func copyRow(label: String, value: String) -> some View {
        HStack {
            Text(label)
                .font(AppTypography.caption)
                .foregroundStyle(.secondary)
            Spacer()
            Text(value)
                .font(.body.monospaced().weight(.semibold))
            Button {
                UIPasteboard.general.string = value
                copied = value
            } label: {
                Image(systemName: copied == value ? "checkmark" : "doc.on.doc")
            }
            .buttonStyle(.borderless)
            .accessibilityLabel("Copy \(value)")
        }
    }

    private func runSample() {
        // A fresh made-up reference each time, so a second test isn't a duplicate.
        let ref = (0..<12).map { _ in String(Int.random(in: 0...9)) }.joined()
        let date = Date().formatted(.dateTime.day(.twoDigits).month(.twoDigits).year(.twoDigits))
        let sample = "Sent Rs.450.00 From HDFC Bank A/C *0000 To GroceryMart On \(date) Ref \(ref) Not You? Call 18000000000/SMS BLOCK UPI to 7000000000"
        let outcome = BankSmsImport.run(sender: "AX-HDFCBK-S", body: sample)
        if let id = outcome.savedTransactionId {
            let removed = PennyWiseSharedFacade.companion.shared.deleteTransaction(transactionId: id)
            testResult = removed
                ? "\(outcome.message). It works. The sample has been removed."
                : "\(outcome.message). It works, but the sample couldn't be removed. Delete it from Transactions."
        } else {
            testResult = outcome.message
        }
    }

    private func requestNotifications() {
        // iOS asks only once; after a "Don't Allow" the switch lives in the app's Settings page.
        if notificationStatus == .denied {
            if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) }
            return
        }
        Task {
            _ = try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound])
            await refreshNotificationStatus()
        }
    }

    @MainActor
    private func refreshNotificationStatus() async {
        notificationStatus = await UNUserNotificationCenter.current().notificationSettings().authorizationStatus
    }
}
