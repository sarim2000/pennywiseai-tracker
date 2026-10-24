import SwiftUI

/// The project's GitHub links, in one place so they can't drift apart.
enum AppLinks {
    static let repo = "https://github.com/sarim2000/pennywiseai-tracker"
    static let newIssue = repo + "/issues/new/choose"
}

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
                    Image("AppLogo")
                        .resizable()
                        .aspectRatio(contentMode: .fit)
                        .frame(width: 80, height: 80)
                        .clipShape(RoundedRectangle(cornerRadius: 18))

                    Text("PennyWise")
                        .font(AppTypography.title)

                    Text("Version \(appVersion) (\(buildNumber))")
                        .font(AppTypography.caption)
                        .foregroundStyle(.secondary)

                    Text("Private, on-device expense tracker that helps you understand your spending habits.")
                        .font(AppTypography.body)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, AppSpacing.lg)
            }

            Section("Links") {
                Button {
                    if let url = URL(string: AppLinks.repo) {
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
                    if let url = URL(string: AppLinks.newIssue) {
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
