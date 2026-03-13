import SwiftUI

struct FAQScreen: View {
    var body: some View {
        List {
            Section {
                FAQCategoryView(
                    icon: "arrow.left.arrow.right",
                    iconColor: .blue,
                    title: "Transaction Types",
                    items: [
                        FAQItem(
                            question: "Why are wallet transactions marked as Credit?",
                            answer: "Wallet transactions (Google Pay, PhonePe, etc.) are marked as Credit because they're charged to your bank account or credit card first, not direct bank debits. The actual bank debit is a separate transaction."
                        ),
                        FAQItem(
                            question: "What's the difference between the 5 transaction types?",
                            answer: "Expense: Money going out (purchases, bills)\nIncome: Money coming in (salary, refunds)\nInvestment: Mutual funds, stocks, SIPs\nCredit: Credit card charges, wallet payments\nTransfer: Moving money between your own accounts"
                        ),
                        FAQItem(
                            question: "When should I use Transfer vs Expense?",
                            answer: "Use Transfer when moving money between your own accounts (e.g., savings to checking). Transfers don't affect your net worth. Use Expense for actual spending where money leaves your possession."
                        ),
                    ]
                )
            }

            Section {
                FAQCategoryView(
                    icon: "lock.shield",
                    iconColor: .green,
                    title: "Privacy & Data",
                    items: [
                        FAQItem(
                            question: "Is my financial data secure?",
                            answer: "Yes, all your data stays on your device. We don't have any servers or cloud storage. Your financial information never leaves your phone."
                        ),
                        FAQItem(
                            question: "Can I backup my data?",
                            answer: "Yes! You can export and import your data using the backup features available in Settings."
                        ),
                        FAQItem(
                            question: "What data does the app access?",
                            answer: "PennyWise only accesses data you manually enter. The app does not access any other app data on your device."
                        ),
                    ]
                )
            }

            Section {
                FAQCategoryView(
                    icon: "building.columns",
                    iconColor: .purple,
                    title: "Account Management",
                    items: [
                        FAQItem(
                            question: "What are manual accounts?",
                            answer: "Manual accounts let you track cash, investments, or any accounts where you update balances yourself. They're great for tracking spending that doesn't go through a bank."
                        ),
                        FAQItem(
                            question: "How do I track multiple accounts?",
                            answer: "You can add separate accounts with different names in Account Management. Each account tracks its own transactions and balance independently."
                        ),
                    ]
                )
            }

            Section {
                VStack(spacing: AppSpacing.md) {
                    Text("Still Need Help?")
                        .font(AppTypography.headline)

                    Text("If you couldn't find what you're looking for, feel free to report an issue on GitHub.")
                        .font(AppTypography.caption)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)

                    Button {
                        if let url = URL(string: "https://github.com/nicekid1/pennywiseai-tracker/issues/new/choose") {
                            UIApplication.shared.open(url)
                        }
                    } label: {
                        Label("Report an Issue", systemImage: "exclamationmark.bubble")
                            .font(AppTypography.body)
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                }
                .padding(.vertical, AppSpacing.sm)
                .frame(maxWidth: .infinity)
            }
        }
        .navigationTitle("FAQ")
        .navigationBarTitleDisplayMode(.inline)
    }
}

// MARK: - FAQ Data

struct FAQItem: Identifiable {
    let id = UUID()
    let question: String
    let answer: String
}

// MARK: - FAQ Category View

struct FAQCategoryView: View {
    let icon: String
    let iconColor: Color
    let title: String
    let items: [FAQItem]

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Label {
                Text(title)
                    .font(AppTypography.headline)
            } icon: {
                Image(systemName: icon)
                    .foregroundStyle(iconColor)
            }
            .padding(.bottom, AppSpacing.sm)

            ForEach(items) { item in
                FAQItemView(item: item)
            }
        }
    }
}

// MARK: - FAQ Item View

struct FAQItemView: View {
    let item: FAQItem
    @State private var isExpanded = false

    var body: some View {
        DisclosureGroup(isExpanded: $isExpanded) {
            Text(item.answer)
                .font(AppTypography.caption)
                .foregroundStyle(.secondary)
                .padding(.top, AppSpacing.xs)
                .padding(.bottom, AppSpacing.sm)
        } label: {
            Text(item.question)
                .font(AppTypography.body)
                .foregroundStyle(.primary)
        }
    }
}
