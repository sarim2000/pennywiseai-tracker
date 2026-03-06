import SwiftUI

struct AboutScreen: View {
    private var appVersion: String {
        Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0"
    }

    private var buildNumber: String {
        Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "1"
    }

    var body: some View {
        List {
            Section {
                VStack(spacing: AppSpacing.md) {
                    Image(systemName: "dollarsign.circle.fill")
                        .font(.system(size: 72))
                        .foregroundStyle(.tint)

                    Text("PennyWise")
                        .font(AppTypography.title)

                    Text("Version \(appVersion) (\(buildNumber))")
                        .font(AppTypography.caption)
                        .foregroundStyle(.secondary)

                    Text("AI-powered expense tracker that helps you understand your spending habits.")
                        .font(AppTypography.body)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, AppSpacing.lg)
            }

            Section("Links") {
                Button {
                    if let url = URL(string: "https://github.com/nicekid1/pennywiseai-tracker") {
                        UIApplication.shared.open(url)
                    }
                } label: {
                    Label {
                        Text("GitHub Repository")
                            .font(AppTypography.body)
                            .foregroundStyle(.primary)
                    } icon: {
                        Image(systemName: "chevron.left.forwardslash.chevron.right")
                            .foregroundStyle(.blue)
                    }
                }

                Button {
                    if let url = URL(string: "https://github.com/nicekid1/pennywiseai-tracker/issues/new/choose") {
                        UIApplication.shared.open(url)
                    }
                } label: {
                    Label {
                        Text("Report an Issue")
                            .font(AppTypography.body)
                            .foregroundStyle(.primary)
                    } icon: {
                        Image(systemName: "exclamationmark.bubble")
                            .foregroundStyle(.orange)
                    }
                }
            }

            Section {
                VStack(spacing: AppSpacing.xs) {
                    Text("Made with care for mindful spending")
                        .font(AppTypography.caption)
                        .foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, AppSpacing.sm)
            }
        }
        .navigationTitle("About")
        .navigationBarTitleDisplayMode(.inline)
    }
}
