import SwiftUI

final class ThemeManager: ObservableObject {
    static let shared = ThemeManager()

    @AppStorage("themeMode") var themeMode: String = "system"
    @AppStorage("accentColor") var accentColorKey: String = "rose"
    @AppStorage("isAmoledDark") var isAmoledDark: Bool = false

    var colorScheme: ColorScheme? {
        switch themeMode {
        case "light": return .light
        case "dark": return .dark
        default: return nil
        }
    }

    var accentColor: Color {
        AccentColorOption.all.first { $0.key == accentColorKey }?.color ?? AccentColorOption.all[0].color
    }

    /// Text/icon colour on an accent fill. The dark-mode accents are light
    /// pastels where white text drops to ~3:1, so they get black text there.
    var onAccentColor: Color {
        let accent = UIColor(accentColor)
        return Color(uiColor: UIColor { traits in
            guard traits.userInterfaceStyle == .dark else { return .white }
            var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
            accent.resolvedColor(with: traits).getRed(&r, green: &g, blue: &b, alpha: &a)
            func linear(_ c: CGFloat) -> CGFloat { c <= 0.03928 ? c / 12.92 : pow((c + 0.055) / 1.055, 2.4) }
            let luminance = 0.2126 * linear(r) + 0.7152 * linear(g) + 0.0722 * linear(b)
            // 0.179 is where black and white text have equal contrast.
            return luminance > 0.179 ? .black : .white
        })
    }

    /// Whether AMOLED pure-black mode should be active for the given color scheme
    func isAmoledActive(for scheme: ColorScheme) -> Bool {
        isAmoledDark && scheme == .dark
    }

    private init() {}
}

// MARK: - Accent Color Options

struct AccentColorOption: Identifiable {
    let key: String
    let name: String
    let color: Color
    var id: String { key }

    static let all: [AccentColorOption] = makeAll()

    private static func makeAll() -> [AccentColorOption] {
        let rose = AccentColorOption(key: "rose", name: "Rose", color: Color(lightHex: 0xB4637A, darkHex: 0xEB6F92))
        let pine = AccentColorOption(key: "pine", name: "Pine", color: Color(lightHex: 0x286983, darkHex: 0x31748F))
        let foam = AccentColorOption(key: "foam", name: "Foam", color: Color(lightHex: 0x56949F, darkHex: 0x9CCFD8))
        let iris = AccentColorOption(key: "iris", name: "Iris", color: Color(lightHex: 0x907AA9, darkHex: 0xC4A7E7))
        let gold = AccentColorOption(key: "gold", name: "Gold", color: Color(lightHex: 0xEA9D34, darkHex: 0xF6C177))
        let love = AccentColorOption(key: "love", name: "Love", color: Color(lightHex: 0xB4637A, darkHex: 0xEB6F92))
        let highlight = AccentColorOption(key: "highlight", name: "Highlight", color: Color(lightHex: 0xCECACD, darkHex: 0x524F67))
        let muted = AccentColorOption(key: "muted", name: "Muted", color: Color(lightHex: 0x9893A5, darkHex: 0x6E6A86))
        let subtle = AccentColorOption(key: "subtle", name: "Subtle", color: Color(lightHex: 0x797593, darkHex: 0x908CAA))
        let overlay = AccentColorOption(key: "overlay", name: "Overlay", color: Color(lightHex: 0xF2E9E1, darkHex: 0x393552))
        let surface = AccentColorOption(key: "surface", name: "Surface", color: Color(lightHex: 0xFFFAF3, darkHex: 0x1F1D2E))
        let text = AccentColorOption(key: "text", name: "Text", color: Color(lightHex: 0x575279, darkHex: 0xE0DEF4))
        return [rose, pine, foam, iris, gold, love, highlight, muted, subtle, overlay, surface, text]
    }
}
