import SwiftUI

struct ProfileStep: View {
    @AppStorage("userName") private var userName: String = "User"
    @AppStorage("userAvatarIndex") private var userAvatarIndex: Int = 0
    @ObservedObject private var themeManager = ThemeManager.shared

    private let avatarIcons: [String] = [
        "person.fill", "figure.walk", "figure.run",
        "brain.head.profile", "face.smiling", "star.fill",
        "heart.fill", "leaf.fill", "flame.fill",
        "bolt.fill", "music.note", "gamecontroller.fill"
    ]

    private let columns = Array(repeating: GridItem(.flexible(), spacing: AppSpacing.md), count: 4)

    var body: some View {
        ScrollView {
            VStack(spacing: AppSpacing.xl) {
                Spacer()
                    .frame(height: AppSpacing.lg)

                VStack(spacing: AppSpacing.sm) {
                    Text("What should we call you?")
                        .font(AppTypography.title)
                        .multilineTextAlignment(.center)

                    TextField("Your name", text: $userName)
                        .textFieldStyle(.roundedBorder)
                        .font(AppTypography.body)
                        .padding(.horizontal, AppSpacing.xl)
                }

                VStack(spacing: AppSpacing.md) {
                    Text("Choose your avatar")
                        .font(AppTypography.headline)

                    LazyVGrid(columns: columns, spacing: AppSpacing.md) {
                        ForEach(0..<avatarIcons.count, id: \.self) { index in
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
            Image(systemName: avatarIcons[index])
                .font(.title2)
                .foregroundColor(userAvatarIndex == index ? .white : themeManager.accentColor)
                .frame(width: 56, height: 56)
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
