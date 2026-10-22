import SwiftUI
import Shared
import UserNotifications

/// Settings › Auto-add bank SMS: how to point a Shortcuts automation at
/// AddBankSmsIntent, plus a dry run on a sample message.
struct BankSmsSetupScreen: View {
    @Environment(\.isAmoledActive) private var isAmoled
    @State private var testResult: String?
    @State private var notificationsAllowed = true

    // Sender IDs as the parser-core parsers match them (canHandle), most-used Indian banks first.
    private static let senders: [(bank: String, id: String, example: String)] = [
        ("Kotak Mahindra Bank", "KOTAK", "JD-KOTAKB-S"),
        ("HDFC Bank", "HDFCBK", "AX-HDFCBK-S"),
        ("ICICI Bank", "ICICI", "JM-ICICIB-S"),
        ("State Bank of India", "SBI", "ATMSBI, SBIINB, SBIUPI"),
        ("Axis Bank", "AXISBK", "AX-AXISBK-S")
    ]

    private static let steps = [
        "Open the Shortcuts app, tap Automation, then tap +.",
        "Choose Message.",
        "Under Sender, enter your bank's sender ID from the list below.",
        "Choose Run Immediately, then tap Next.",
        "Add the action \u{201C}Add bank SMS to PennyWise\u{201D} and set its Message to Shortcut Input."
    ]

    var body: some View {
        List {
            Section {
                Text("iPhone apps can't read your texts, but a Shortcuts automation can pass each bank SMS to PennyWise. It's read and saved on this phone only.")
                    .font(AppTypography.body)
                    .foregroundStyle(.secondary)
            }

            Section("Set it up") {
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
            }

            Section {
                ForEach(Self.senders, id: \.id) { sender in
                    VStack(alignment: .leading, spacing: AppSpacing.xs) {
                        HStack {
                            Text(sender.bank)
                                .font(AppTypography.body)
                            Spacer()
                            Text(sender.id)
                                .font(.body.monospaced().weight(.semibold))
                                .textSelection(.enabled)
                        }
                        Text("Shows up as \(sender.example)")
                            .font(AppTypography.caption)
                            .foregroundStyle(.secondary)
                    }
                }
            } header: {
                Text("Sender IDs")
            } footer: {
                Text("Make one automation per bank. PennyWise reads 160+ banks; for another bank, use the ID its texts come from.")
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

            if !notificationsAllowed {
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
    }

    private func runSample() {
        // A fresh made-up reference each time, so a second test isn't a duplicate.
        let ref = (0..<12).map { _ in String(Int.random(in: 0...9)) }.joined()
        let date = Date().formatted(.dateTime.day(.twoDigits).month(.twoDigits).year(.twoDigits))
        let sample = "Sent Rs.450.00 From HDFC Bank A/C *0000 To GroceryMart On \(date) Ref \(ref) Not You? Call 18000000000/SMS BLOCK UPI to 7000000000"
        let outcome = BankSmsImport.run(sender: "AX-HDFCBK-S", body: sample)
        if let id = outcome.savedTransactionId {
            _ = PennyWiseSharedFacade.companion.shared.deleteTransaction(transactionId: id)
            testResult = "\(outcome.message). It works. The sample has been removed."
        } else {
            testResult = outcome.message
        }
    }

    private func requestNotifications() {
        Task {
            _ = try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound])
            await refreshNotificationStatus()
        }
    }

    @MainActor
    private func refreshNotificationStatus() async {
        let status = await UNUserNotificationCenter.current().notificationSettings().authorizationStatus
        notificationsAllowed = status == .authorized || status == .provisional || status == .ephemeral
    }
}
