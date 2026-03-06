import Foundation
import Shared

class RulesViewModel: ObservableObject {
    private let facade = PennyWiseSharedFacade()

    @Published var rules: [SharedRuleItem] = []
    @Published var categories: [String] = []

    func loadRules() {
        rules = facade.getAllRules()
    }

    func loadCategories() {
        categories = facade.getAllCategories().map { $0.name }
    }

    func createRule(
        name: String,
        conditionsJson: String,
        actionsJson: String,
        priority: Int32
    ) -> Bool {
        let success = facade.createRule(
            name: name,
            conditionsJson: conditionsJson,
            actionsJson: actionsJson,
            priority: priority
        )
        if success {
            loadRules()
        }
        return success
    }

    func updateRule(
        id: String,
        name: String,
        conditionsJson: String,
        actionsJson: String,
        isEnabled: Bool,
        priority: Int32
    ) -> Bool {
        let success = facade.updateRule(
            id: id,
            name: name,
            conditionsJson: conditionsJson,
            actionsJson: actionsJson,
            isEnabled: isEnabled,
            priority: priority
        )
        if success {
            loadRules()
        }
        return success
    }

    func toggleRule(id: String, isEnabled: Bool) -> Bool {
        let success = facade.toggleRule(id: id, isEnabled: isEnabled)
        if success {
            loadRules()
        }
        return success
    }

    func deleteRule(id: String) -> Bool {
        let success = facade.deleteRule(id: id)
        if success {
            loadRules()
        }
        return success
    }
}
