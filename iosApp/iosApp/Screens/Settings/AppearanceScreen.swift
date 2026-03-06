import SwiftUI

struct AppearanceScreen: View {
    @ObservedObject private var themeManager = ThemeManager.shared

    var body: some View {
        List {
            Section {
                themeModePicker
            } header: {
                Text("Theme Mode")
            } footer: {
                Text("System follows your device settings.")
            }

            Section("Accent Color") {
                accentColorPicker
            }

            if themeManager.themeMode != "light" {
                Section {
                    Toggle(isOn: $themeManager.isAmoledDark) {
                        Label {
                            VStack(alignment: .leading, spacing: AppSpacing.xs) {
                                Text("AMOLED Dark")
                                    .font(AppTypography.body)
                                Text("Pure black background for OLED displays")
                                    .font(AppTypography.caption)
                                    .foregroundStyle(.secondary)
                            }
                        } icon: {
                            Image(systemName: "moon.circle.fill")
                                .foregroundStyle(.indigo)
                        }
                    }
                }
            }
        }
        .navigationTitle("Appearance")
        .navigationBarTitleDisplayMode(.inline)
    }

    // MARK: - Theme Mode Picker

    private var themeModePicker: some View {
        Picker("Theme", selection: $themeManager.themeMode) {
            Label("System", systemImage: "gear")
                .tag("system")
            Label("Light", systemImage: "sun.max.fill")
                .tag("light")
            Label("Dark", systemImage: "moon.fill")
                .tag("dark")
        }
        .pickerStyle(.segmented)
        .listRowBackground(Color.clear)
        .listRowInsets(EdgeInsets(top: AppSpacing.sm, leading: 0, bottom: AppSpacing.sm, trailing: 0))
    }

    // MARK: - Accent Color Picker

    private var accentColorPicker: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: AppSpacing.md) {
                ForEach(AccentColorOption.all) { option in
                    accentColorCircle(option)
                }
            }
            .padding(.vertical, AppSpacing.sm)
        }
        .listRowInsets(EdgeInsets(top: 0, leading: AppSpacing.md, bottom: 0, trailing: AppSpacing.md))
    }

    private func accentColorCircle(_ option: AccentColorOption) -> some View {
        Button {
            themeManager.accentColorKey = option.key
        } label: {
            VStack(spacing: AppSpacing.xs) {
                ZStack {
                    Circle()
                        .fill(option.color)
                        .frame(width: 44, height: 44)

                    if themeManager.accentColorKey == option.key {
                        Image(systemName: "checkmark")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundStyle(.white)
                    }

                    if themeManager.accentColorKey == option.key {
                        Circle()
                            .strokeBorder(option.color, lineWidth: 3)
                            .frame(width: 52, height: 52)
                    }
                }
                .frame(width: 52, height: 52)

                Text(option.name)
                    .font(AppTypography.caption2)
                    .foregroundStyle(themeManager.accentColorKey == option.key ? .primary : .secondary)
            }
        }
        .buttonStyle(.plain)
    }
}

struct AppearanceScreen_Previews: PreviewProvider {
    static var previews: some View {
        NavigationView {
            AppearanceScreen()
        }
    }
}
