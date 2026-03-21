import SwiftUI

struct CategoryIconInfo {
    let systemName: String
    let color: Color
}

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

    // MARK: - Category Icons & Colors

    static func categoryIcon(for name: String) -> CategoryIconInfo {
        switch name.lowercased() {
        case "food", "food & dining", "dining":
            return CategoryIconInfo(systemName: "fork.knife", color: Color(hex: 0xFC8019))
        case "groceries":
            return CategoryIconInfo(systemName: "cart.fill", color: Color(hex: 0x5AC85A))
        case "transport", "transportation":
            return CategoryIconInfo(systemName: "car.fill", color: Color(hex: 0x37474F))
        case "shopping":
            return CategoryIconInfo(systemName: "bag.fill", color: Color(hex: 0xFF9900))
        case "bills", "utilities", "bills & utilities":
            return CategoryIconInfo(systemName: "doc.text.fill", color: Color(hex: 0x4CAF50))
        case "entertainment":
            return CategoryIconInfo(systemName: "film.fill", color: Color(hex: 0xE50914))
        case "health", "healthcare", "medical":
            return CategoryIconInfo(systemName: "cross.case.fill", color: Color(hex: 0x10847E))
        case "investments", "investment":
            return CategoryIconInfo(systemName: "chart.line.uptrend.xyaxis", color: Color(hex: 0x00D09C))
        case "banking":
            return CategoryIconInfo(systemName: "building.columns.fill", color: Color(hex: 0x004C8F))
        case "personal care":
            return CategoryIconInfo(systemName: "face.smiling.fill", color: Color(hex: 0x6A4C93))
        case "education":
            return CategoryIconInfo(systemName: "graduationcap.fill", color: Color(hex: 0x673AB7))
        case "mobile", "mobile & recharge", "recharge":
            return CategoryIconInfo(systemName: "iphone", color: Color(hex: 0x2A3890))
        case "fitness":
            return CategoryIconInfo(systemName: "figure.walk", color: Color(hex: 0xFF3278))
        case "insurance":
            return CategoryIconInfo(systemName: "shield.fill", color: Color(hex: 0x0066CC))
        case "tax":
            return CategoryIconInfo(systemName: "wallet.pass.fill", color: Color(hex: 0x795548))
        case "bank charges":
            return CategoryIconInfo(systemName: "banknote", color: Color(hex: 0x9E9E9E))
        case "credit card payment":
            return CategoryIconInfo(systemName: "creditcard.fill", color: Color(hex: 0x1565C0))
        case "salary":
            return CategoryIconInfo(systemName: "indianrupeesign.circle.fill", color: Color(hex: 0x2E7D32))
        case "other income":
            return CategoryIconInfo(systemName: "plus.circle.fill", color: Color(hex: 0x388E3C))
        case "travel":
            return CategoryIconInfo(systemName: "airplane", color: Color(hex: 0x00BCD4))
        default:
            return CategoryIconInfo(systemName: "square.grid.2x2.fill", color: Color(hex: 0x757575))
        }
    }

    static func categoryColor(for name: String) -> Color {
        return categoryIcon(for: name).color
    }

    // MARK: - AMOLED Dark Mode Colors (matching Android)

    static let amoledBackground = Color(hex: 0x000000)
    static let amoledSurface = Color(hex: 0x0A0A0A)
    static let amoledSurfaceVariant = Color(hex: 0x121212)
    static let amoledSurfaceContainerLowest = Color(hex: 0x000000)
    static let amoledSurfaceContainerLow = Color(hex: 0x0A0A0A)
    static let amoledSurfaceContainer = Color(hex: 0x111111)
    static let amoledSurfaceContainerHigh = Color(hex: 0x1A1A1A)
    static let amoledSurfaceContainerHighest = Color(hex: 0x222222)

    /// Adaptive background: pure black in AMOLED dark mode, system default otherwise
    static func background(isAmoled: Bool) -> Color {
        isAmoled ? amoledBackground : Color(.systemBackground)
    }

    /// Adaptive surface: near-black in AMOLED dark mode
    static func surface(isAmoled: Bool) -> Color {
        isAmoled ? amoledSurface : Color(.secondarySystemBackground)
    }

    /// Adaptive grouped background
    static func groupedBackground(isAmoled: Bool) -> Color {
        isAmoled ? amoledBackground : Color(.systemGroupedBackground)
    }

    /// Adaptive secondary grouped background (cards within grouped views)
    static func secondaryGroupedBackground(isAmoled: Bool) -> Color {
        isAmoled ? amoledSurfaceVariant : Color(.secondarySystemGroupedBackground)
    }

    /// Adaptive tertiary background
    static func tertiaryBackground(isAmoled: Bool) -> Color {
        isAmoled ? amoledSurfaceContainerHigh : Color(.tertiarySystemBackground)
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

// MARK: - AMOLED Environment Key

private struct AmoledActiveKey: EnvironmentKey {
    static let defaultValue: Bool = false
}

extension EnvironmentValues {
    var isAmoledActive: Bool {
        get { self[AmoledActiveKey.self] }
        set { self[AmoledActiveKey.self] = newValue }
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
