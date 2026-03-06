import SwiftUI

struct ProfileStep: View {
    @AppStorage("userName") private var userName: String = "User"
    @AppStorage("userAvatarIndex") private var userAvatarIndex: Int = 0
    @ObservedObject private var themeManager = ThemeManager.shared

    private let avatarImages: [String] = [
        "avatar_1", "avatar_2", "avatar_3", "avatar_4", "avatar_5",
        "avatar_6", "avatar_7", "avatar_8", "avatar_9", "avatar_10"
    ]

    private let columns = Array(repeating: GridItem(.flexible(), spacing: AppSpacing.md), count: 5)

    @FocusState private var isNameFocused: Bool

    var body: some View {
        ScrollView {
            VStack(spacing: AppSpacing.xl) {
                Spacer()
                    .frame(height: AppSpacing.lg)

                VStack(spacing: AppSpacing.sm) {
                    Text("What should we call you?")
                        .font(AppTypography.title)
                        .multilineTextAlignment(.center)

                    Text("Enter your name to personalize the app")
                        .font(AppTypography.body)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)

                    TextField("", text: Binding(
                        get: { userName == "User" && !isNameFocused ? "" : userName },
                        set: { userName = $0 }
                    ), prompt: Text("Enter your name").foregroundColor(.secondary))
                        .textFieldStyle(.roundedBorder)
                        .font(.system(size: 18))
                        .padding(.horizontal, AppSpacing.lg)
                        .padding(.vertical, AppSpacing.xs)
                        .focused($isNameFocused)
                }

                VStack(spacing: AppSpacing.md) {
                    Text("Choose your avatar")
                        .font(AppTypography.headline)

                    LazyVGrid(columns: columns, spacing: AppSpacing.md) {
                        ForEach(0..<avatarImages.count, id: \.self) { index in
                            avatarButton(index: index)
                        }
                    }
                    .padding(.horizontal, AppSpacing.lg)
                }

                Spacer()
            }
            .padding(AppSpacing.lg)
        }
    }

    private func avatarButton(index: Int) -> some View {
        Button {
            withAnimation(.easeInOut(duration: 0.2)) {
                userAvatarIndex = index
            }
        } label: {
            Image(avatarImages[index])
                .resizable()
                .scaledToFit()
                .frame(width: 48, height: 48)
                .clipShape(Circle())
                .frame(width: 64, height: 64)
                .background(
                    Circle()
                        .fill(userAvatarIndex == index ? themeManager.accentColor : themeManager.accentColor.opacity(0.15))
                )
                .overlay(
                    Circle()
                        .stroke(
                            userAvatarIndex == index ? themeManager.accentColor : Color.clear,
                            lineWidth: 2
                        )
                )
        }
        .buttonStyle(.plain)
    }
}

struct ProfileStep_Previews: PreviewProvider {
    static var previews: some View {
        ProfileStep()
    }
}
