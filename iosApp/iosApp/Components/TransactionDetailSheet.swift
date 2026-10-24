import Shared
import SwiftUI

/// Home, Account detail and Analytics open the same detail screen the
/// Transactions tab pushes (with Edit and Delete), presented in a sheet.
struct TransactionDetailSheet: View {
    let item: SharedRecentTransactionItem
    var onUpdate: (() -> Void)?

    @ObservedObject private var themeManager = ThemeManager.shared
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            TransactionDetailView(
                transactionId: item.transactionId,
                facade: PennyWiseSharedFacade.companion.shared,
                onUpdate: onUpdate
            )
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
        // Sheets don't inherit the TabView's tint.
        .tint(themeManager.accentColor)
    }
}
