import SwiftUI

struct SettingsPlaceholderScreen: View {
    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: "gearshape.fill")
                .font(.system(size: 48))
                .foregroundStyle(.secondary)
            Text("Settings")
                .font(.title2.bold())
            Text("Coming soon")
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}
