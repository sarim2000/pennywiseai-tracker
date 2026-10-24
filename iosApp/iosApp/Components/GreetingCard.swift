import SwiftUI

struct GreetingCard: View {
    @ObservedObject private var themeManager = ThemeManager.shared
    @AppStorage("userName") private var userName: String = "User"
    @AppStorage("userAvatarIndex") private var userAvatarIndex: Int = 0

    private static let avatarImages = [
        "avatar_1", "avatar_2", "avatar_3", "avatar_4", "avatar_5",
        "avatar_6", "avatar_7", "avatar_8", "avatar_9", "avatar_10"
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

    private var displayName: String? {
        let name = userName.trimmingCharacters(in: .whitespaces)
        return name.isEmpty || name == "User" ? nil : name
    }

    private var avatarImageName: String {
        let index = max(0, min(userAvatarIndex, Self.avatarImages.count - 1))
        return Self.avatarImages[index]
    }

    var body: some View {
        HStack(spacing: AppSpacing.md) {
            // Avatar circle
            ZStack {
                Circle()
                    .fill(themeManager.accentColor)
                    .frame(width: 44, height: 44)

                Image(avatarImageName)
                    .resizable()
                    .scaledToFit()
                    .frame(width: 30, height: 30)
                    .clipShape(Circle())
            }

            // Greeting text
            // No name (onboarding skipped): show just the greeting rather
            // than the "User" placeholder.
            VStack(alignment: .leading, spacing: 2) {
                if let displayName {
                    Text(displayName)
                        .font(AppTypography.headline)
                        .foregroundStyle(.primary)

                    Text(greeting)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                } else {
                    Text(greeting)
                        .font(AppTypography.headline)
                        .foregroundStyle(.primary)
                }
            }

            Spacer()
        }
        .padding(AppSpacing.md)
        .background(.ultraThinMaterial)
        .clipShape(RoundedRectangle(cornerRadius: AppCornerRadius.large))
    }
}
