import Foundation
import SwiftUI

@MainActor
final class CurrencyManager: ObservableObject {
    static let shared = CurrencyManager()

    @AppStorage("displayCurrency") var displayCurrency: String = "INR"

    private init() {}

    var currencySymbol: String {
        CurrencyFormatter.symbol(for: displayCurrency)
    }
}
