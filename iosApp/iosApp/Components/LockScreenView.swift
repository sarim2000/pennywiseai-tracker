import SwiftUI

struct LockScreenView: View {
    @ObservedObject var appLockManager = AppLockManager.shared

    var body: some View {
        ZStack {
            Rectangle()
                .fill(.ultraThinMaterial)
                .ignoresSafeArea()

            // Icon and title sit in the upper third: the system Face ID HUD
            // draws in the centre of the screen.
            VStack(spacing: AppSpacing.lg) {
                Spacer().frame(maxHeight: 96)

                Image(systemName: "lock.shield.fill")
                    .font(.system(size: 56))
                    .foregroundStyle(.secondary)

                VStack(spacing: AppSpacing.sm) {
                    Text("PennyWise")
                        .font(AppTypography.title)

                    Text("Locked")
                        .font(AppTypography.caption)
                        .foregroundStyle(.secondary)
                }

                Spacer()

                Button {
                    appLockManager.authenticate()
                } label: {
                    Label(
                        "Unlock with \(appLockManager.biometricType)",
                        systemImage: appLockManager.biometricIcon
                    )
                    .font(AppTypography.headline)
                    .frame(maxWidth: .infinity)
                    .padding(AppSpacing.md)
                    .background(.thinMaterial)
                    .clipShape(RoundedRectangle(cornerRadius: AppCornerRadius.medium))
                }
                .buttonStyle(.plain)
                .padding(.horizontal, AppSpacing.xl)
                .padding(.bottom, AppSpacing.xl)
            }
        }
        .onAppear {
            appLockManager.authenticate()
        }
    }
}
