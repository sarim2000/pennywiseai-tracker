import Foundation
import Shared

class SubscriptionsViewModel: ObservableObject {
    private let facade = PennyWiseSharedFacade()

    @Published var subscriptions: [SharedSubscriptionItem] = []
    @Published var categories: [String] = []

    func loadSubscriptions() {
        subscriptions = facade.getAllSubscriptions()
    }

    func loadCategories() {
        categories = facade.getAllCategories().map { $0.name }
    }

    func createSubscription(
        merchantName: String,
        amountMinor: Int64,
        category: String?,
        currency: String
    ) -> Bool {
        let success = facade.createSubscription(
            merchantName: merchantName,
            amountMinor: amountMinor,
            category: category,
            currency: currency
        )
        if success {
            loadSubscriptions()
        }
        return success
    }

    func updateSubscription(
        id: Int64,
        merchantName: String,
        amountMinor: Int64,
        category: String?,
        currency: String
    ) -> Bool {
        let success = facade.updateSubscription(
            id: id,
            merchantName: merchantName,
            amountMinor: amountMinor,
            category: category,
            currency: currency
        )
        if success {
            loadSubscriptions()
        }
        return success
    }

    func deleteSubscription(id: Int64) -> Bool {
        let success = facade.deleteSubscription(id: id)
        if success {
            loadSubscriptions()
        }
        return success
    }
}
