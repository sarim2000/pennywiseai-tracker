import SwiftUI

struct GreetingCard: View {
    @ObservedObject private var themeManager = ThemeManager.shared
    @AppStorage("userName") private var userName: String = "User"
    @AppStorage("userAvatarIndex") private var userAvatarIndex: Int = 0

    private static let avatarSymbols = [
        "person.fill", "figure.walk", "figure.run", "brain.head.profile",
        "face.smiling", "star.fill", "heart.fill", "leaf.fill",
        "flame.fill", "bolt.fill", "music.note", "gamecontroller.fill"
    ]

    private var greeting: String {
        let hour = Calendar.current.component(.hour, from: Date())
        switch hour {
        case 5...11: return "Good morning"
        case 12...16: return "Good afternoon"
        case 17...21: return "Good evening"
        default: return "Good night"
        }
    }

    private var avatarSymbol: String {
        let index = max(0, min(userAvatarIndex, Self.avatarSymbols.count - 1))
        return Self.avatarSymbols[index]
    }

    var body: some View {
        HStack(spacing: AppSpacing.md) {
            // Avatar circle
            ZStack {
                Circle()
                    .fill(themeManager.accentColor)
                    .frame(width: 44, height: 44)

                Image(systemName: avatarSymbol)
                    .font(.system(size: 20))
                    .foregroundStyle(.white)
            }

            // Greeting text
            VStack(alignment: .leading, spacing: 2) {
                Text(userName.isEmpty ? "User" : userName)
                    .font(AppTypography.headline)
                    .foregroundStyle(.primary)

                Text(greeting)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }

            Spacer()
        }
        .padding(AppSpacing.md)
        .background(.ultraThinMaterial)
        .clipShape(RoundedRectangle(cornerRadius: AppCornerRadius.large))
    }
}
