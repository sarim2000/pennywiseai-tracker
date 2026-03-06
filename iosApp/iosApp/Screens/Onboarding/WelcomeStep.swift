import SwiftUI

struct WelcomeStep: View {
    @ObservedObject private var themeManager = ThemeManager.shared

    var body: some View {
        VStack(spacing: AppSpacing.xl) {
            Spacer()

            Image(systemName: "chart.bar.doc.horizontal")
                .font(.system(size: 80))
                .foregroundColor(themeManager.accentColor)

            VStack(spacing: AppSpacing.sm) {
                Text("Welcome to PennyWise")
                    .font(AppTypography.largeTitle)
                    .multilineTextAlignment(.center)

                Text("Your minimalist expense tracker")
                    .font(AppTypography.body)
                    .foregroundColor(.secondary)
            }

            VStack(alignment: .leading, spacing: AppSpacing.md) {
                featureBullet(
                    icon: "dollarsign.circle.fill",
                    text: "Track expenses effortlessly"
                )
                featureBullet(
                    icon: "chart.pie.fill",
                    text: "Beautiful charts & insights"
                )
                featureBullet(
                    icon: "lock.shield.fill",
                    text: "All data stays on your device"
                )
            }
            .padding(.horizontal, AppSpacing.lg)

            Spacer()
            Spacer()
        }
        .padding(AppSpacing.lg)
    }

    private func featureBullet(icon: String, text: String) -> some View {
        HStack(spacing: AppSpacing.md) {
            Image(systemName: icon)
                .font(.title2)
                .foregroundColor(themeManager.accentColor)
                .frame(width: 32)

            Text(text)
                .font(AppTypography.body)
        }
    }
}

struct WelcomeStep_Previews: PreviewProvider {
    static var previews: some View {
        WelcomeStep()
    }
}
