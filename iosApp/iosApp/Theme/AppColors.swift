import SwiftUI

enum AppColors {

    // MARK: - Transaction Type Colors (adaptive light/dark)

    static let income = Color.incomeColor
    static let expense = Color.expenseColor
    static let credit = Color.creditColor
    static let transfer = Color.transferColor
    static let investment = Color.investmentColor

    static func transactionColor(for type: String) -> Color {
        switch type {
        case "INCOME": return income
        case "EXPENSE": return expense
        case "CREDIT": return credit
        case "TRANSFER": return transfer
        case "INVESTMENT": return investment
        default: return .secondary
        }
    }

    static func transactionPrefix(for type: String) -> String {
        switch type {
        case "INCOME": return "+"
        case "EXPENSE", "CREDIT", "INVESTMENT": return "-"
        default: return ""
        }
    }

    static func transactionIcon(for type: String) -> String {
        switch type {
        case "INCOME": return "arrow.down.circle.fill"
        case "EXPENSE": return "arrow.up.circle.fill"
        case "CREDIT": return "creditcard.fill"
        case "TRANSFER": return "arrow.left.arrow.right.circle.fill"
        case "INVESTMENT": return "chart.line.uptrend.xyaxis.circle.fill"
        default: return "circle.fill"
        }
    }

    // MARK: - Category Colors

    static let categoryFood = Color(hex: 0xFF7043)
    static let categoryTransport = Color(hex: 0x5C6BC0)
    static let categoryShopping = Color(hex: 0xAB47BC)
    static let categoryBills = Color(hex: 0x42A5F5)
    static let categoryEntertainment = Color(hex: 0xEC407A)
    static let categoryHealth = Color(hex: 0x26A69A)
    static let categoryOther = Color(hex: 0x78909C)

    static func categoryColor(for name: String) -> Color {
        switch name.lowercased() {
        case "food", "food & dining", "dining": return categoryFood
        case "transport", "transportation", "travel": return categoryTransport
        case "shopping": return categoryShopping
        case "bills", "utilities", "bills & utilities": return categoryBills
        case "entertainment": return categoryEntertainment
        case "health", "healthcare", "medical": return categoryHealth
        default: return categoryOther
        }
    }

    // MARK: - Budget Progress Colors

    static let budgetSafe = Color.budgetSafeColor
    static let budgetWarning = Color.budgetWarningColor
    static let budgetDanger = Color.budgetDangerColor

    static func budgetColor(for ratio: Double) -> Color {
        if ratio < 0.5 { return budgetSafe }
        if ratio < 0.8 { return budgetWarning }
        return budgetDanger
    }
}

// MARK: - Color Extensions

extension Color {
    init(hex: UInt, alpha: Double = 1.0) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: alpha
        )
    }

    init(lightHex: UInt, darkHex: UInt) {
        let light = Color(hex: lightHex)
        let dark = Color(hex: darkHex)
        self.init(uiColor: UIColor { traits in
            traits.userInterfaceStyle == .dark ? UIColor(dark) : UIColor(light)
        })
    }
}

// MARK: - Semantic Color Definitions

extension Color {
    static let incomeColor = Color(lightHex: 0x00796B, darkHex: 0x80CBC4)
    static let expenseColor = Color(lightHex: 0xC62828, darkHex: 0xE57373)
    static let creditColor = Color(lightHex: 0xEF6C00, darkHex: 0xFFD54F)
    static let transferColor = Color(lightHex: 0x303F9F, darkHex: 0x9FA8DA)
    static let investmentColor = Color(lightHex: 0x37474F, darkHex: 0x90A4AE)
    static let budgetSafeColor = Color(lightHex: 0x2E7D32, darkHex: 0x81C784)
    static let budgetWarningColor = Color(lightHex: 0xFF8F00, darkHex: 0xFFCA28)
    static let budgetDangerColor = Color(lightHex: 0xC62828, darkHex: 0xE57373)
}
