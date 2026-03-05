import Foundation
import Shared

class AccountsViewModel: ObservableObject {
    let facade = PennyWiseSharedFacade()

    @Published var accounts: [SharedAccountItem] = []
    @Published var cards: [SharedCardItem] = []
    @Published var totalBalanceMinor: Int64 = 0
    @Published var detailTransactions: [SharedRecentTransactionItem] = []

    func loadAccounts() {
        accounts = facade.getAllAccounts()
        cards = facade.getAllCards()
        totalBalanceMinor = accounts.reduce(Int64(0)) { sum, account in
            sum + account.balanceMinor
        }
    }

    func loadAccountTransactions(bankName: String, accountLast4: String) {
        detailTransactions = facade.getAccountTransactions(
            bankName: bankName,
            accountLast4: accountLast4
        )
    }

    func createAccount(
        bankName: String,
        accountLast4: String,
        accountType: String,
        balanceMinor: Int64,
        currency: String
    ) -> Bool {
        let success = facade.createAccount(
            bankName: bankName,
            accountLast4: accountLast4,
            accountType: accountType,
            balanceMinor: balanceMinor,
            currency: currency
        )
        if success { loadAccounts() }
        return success
    }

    func deleteAccount(bankName: String, accountLast4: String) {
        _ = facade.deleteAccount(bankName: bankName, accountLast4: accountLast4)
        loadAccounts()
    }

    func createCard(
        cardLast4: String,
        cardType: String,
        bankName: String,
        accountLast4: String?
    ) -> Bool {
        let success = facade.createCard(
            cardLast4: cardLast4,
            cardType: cardType,
            bankName: bankName,
            accountLast4: accountLast4
        )
        if success { loadAccounts() }
        return success
    }

    func deleteCard(cardId: Int64) {
        _ = facade.deleteCard(cardId: cardId)
        loadAccounts()
    }
}
