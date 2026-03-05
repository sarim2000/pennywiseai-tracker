import SwiftUI

enum AppTypography {
    static let largeTitle: Font = .largeTitle.bold()
    static let title: Font = .title2.bold()
    static let headline: Font = .headline
    static let body: Font = .body
    static let caption: Font = .caption
    static let caption2: Font = .caption2

    static let amountLarge: Font = .system(.title, design: .rounded).bold()
    static let amountMedium: Font = .system(.body, design: .rounded).bold()
    static let amountSmall: Font = .system(.subheadline, design: .rounded).bold()
}
