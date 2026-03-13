import Foundation
import Shared

class CategoriesViewModel: ObservableObject {
    let facade: PennyWiseSharedFacade

    @Published var categories: [SharedCategoryItem] = []
    @Published var errorMessage: String?

    init(facade: PennyWiseSharedFacade) {
        self.facade = facade
    }

    func loadCategories() {
        categories = facade.getAllCategories()
    }

    func createCategory(name: String, colorHex: String, isIncome: Bool) -> Bool {
        let success = facade.createCategory(name: name, colorHex: colorHex, isIncome: isIncome)
        if success {
            loadCategories()
        } else {
            errorMessage = "Failed to create category"
        }
        return success
    }

    func updateCategory(id: Int64, name: String, colorHex: String, isIncome: Bool) -> Bool {
        let success = facade.updateCategory(id: id, name: name, colorHex: colorHex, isIncome: isIncome)
        if success {
            loadCategories()
        } else {
            errorMessage = "Failed to update category"
        }
        return success
    }

    func deleteCategory(id: Int64) -> Bool {
        let success = facade.deleteCategory(id: id)
        if success {
            loadCategories()
        } else {
            errorMessage = "Cannot delete system category"
        }
        return success
    }
}
