import SwiftUI
import Shared

struct SettingsScreen: View {
    @ObservedObject private var currencyManager = CurrencyManager.shared
    @ObservedObject private var appLockManager = AppLockManager.shared
    @StateObject private var exportImportManager = ExportImportManager()
    @State private var showingDocumentPicker = false

    var body: some View {
        List {
            // MARK: - Personalization

            Section("Personalization") {
                NavigationLink {
                    AppearanceScreen()
                } label: {
                    Label {
                        VStack(alignment: .leading, spacing: AppSpacing.xs) {
                            Text("Appearance")
                                .font(AppTypography.body)
                            Text("Theme, accent color, dark mode")
                                .font(AppTypography.caption)
                                .foregroundStyle(.secondary)
                        }
                    } icon: {
                        Image(systemName: "paintbrush.fill")
                            .foregroundStyle(.orange)
                    }
                }
            }

            // MARK: - Data Management

            Section("Data Management") {
                NavigationLink {
                    CategoriesScreen(facade: PennyWiseSharedFacade())
                } label: {
                    Label {
                        VStack(alignment: .leading, spacing: AppSpacing.xs) {
                            Text("Categories")
                                .font(AppTypography.body)
                            Text("Manage expense and income categories")
                                .font(AppTypography.caption)
                                .foregroundStyle(.secondary)
                        }
                    } icon: {
                        Image(systemName: "square.grid.2x2.fill")
                            .foregroundStyle(.purple)
                    }
                }

                NavigationLink {
                    BudgetListScreen()
                } label: {
                    Label {
                        VStack(alignment: .leading, spacing: AppSpacing.xs) {
                            Text("Budgets")
                                .font(AppTypography.body)
                            Text("Set spending limits by category")
                                .font(AppTypography.caption)
                                .foregroundStyle(.secondary)
                        }
                    } icon: {
                        Image(systemName: "chart.pie.fill")
                            .foregroundStyle(.green)
                    }
                }

                NavigationLink {
                    AccountListScreen()
                } label: {
                    Label {
                        VStack(alignment: .leading, spacing: AppSpacing.xs) {
                            Text("Accounts")
                                .font(AppTypography.body)
                            Text("Manage bank accounts and cards")
                                .font(AppTypography.caption)
                                .foregroundStyle(.secondary)
                        }
                    } icon: {
                        Image(systemName: "building.columns.fill")
                            .foregroundStyle(.blue)
                    }
                }

                NavigationLink {
                    SubscriptionsScreen()
                } label: {
                    Label {
                        VStack(alignment: .leading, spacing: AppSpacing.xs) {
                            Text("Subscriptions")
                                .font(AppTypography.body)
                            Text("Track recurring payments")
                                .font(AppTypography.caption)
                                .foregroundStyle(.secondary)
                        }
                    } icon: {
                        Image(systemName: "repeat")
                            .foregroundStyle(.indigo)
                    }
                }

                NavigationLink {
                    RulesScreen()
                } label: {
                    Label {
                        VStack(alignment: .leading, spacing: AppSpacing.xs) {
                            Text("Smart Rules")
                                .font(AppTypography.body)
                            Text("Auto-categorize transactions")
                                .font(AppTypography.caption)
                                .foregroundStyle(.secondary)
                        }
                    } icon: {
                        Image(systemName: "wand.and.stars")
                            .foregroundStyle(.pink)
                    }
                }

                Button {
                    exportImportManager.exportBackup()
                } label: {
                    Label {
                        VStack(alignment: .leading, spacing: AppSpacing.xs) {
                            Text("Export Backup")
                                .font(AppTypography.body)
                            Text("Save your data as a JSON file")
                                .font(AppTypography.caption)
                                .foregroundStyle(.secondary)
                        }
                    } icon: {
                        Image(systemName: "square.and.arrow.up")
                            .foregroundStyle(.teal)
                    }
                }
                .disabled(exportImportManager.isExporting)

                Button {
                    showingDocumentPicker = true
                } label: {
                    Label {
                        VStack(alignment: .leading, spacing: AppSpacing.xs) {
                            Text("Import Backup")
                                .font(AppTypography.body)
                            Text("Restore from a backup file")
                                .font(AppTypography.caption)
                                .foregroundStyle(.secondary)
                        }
                    } icon: {
                        Image(systemName: "square.and.arrow.down")
                            .foregroundStyle(.teal)
                    }
                }
                .disabled(exportImportManager.isImporting)
            }

            // MARK: - Preferences

            Section("Preferences") {
                NavigationLink {
                    CurrencyPickerScreen()
                } label: {
                    Label {
                        HStack {
                            VStack(alignment: .leading, spacing: AppSpacing.xs) {
                                Text("Currency")
                                    .font(AppTypography.body)
                                Text("Display currency for amounts")
                                    .font(AppTypography.caption)
                                    .foregroundStyle(.secondary)
                            }
                            Spacer()
                            Text(currencyManager.displayCurrency)
                                .font(AppTypography.body)
                                .foregroundStyle(.secondary)
                        }
                    } icon: {
                        Image(systemName: "dollarsign.circle.fill")
                            .foregroundStyle(.teal)
                    }
                }
            }

            // MARK: - Security

            if appLockManager.canUseBiometric {
                Section("Security") {
                    Toggle(isOn: $appLockManager.appLockEnabled) {
                        Label {
                            VStack(alignment: .leading, spacing: AppSpacing.xs) {
                                Text("App Lock")
                                    .font(AppTypography.body)
                                Text("Require \(appLockManager.biometricType) to open")
                                    .font(AppTypography.caption)
                                    .foregroundStyle(.secondary)
                            }
                        } icon: {
                            Image(systemName: "lock.fill")
                                .foregroundStyle(.red)
                        }
                    }

                    if appLockManager.appLockEnabled {
                        Picker(selection: $appLockManager.lockTimeoutMinutes) {
                            Text("Immediately").tag(0)
                            Text("After 1 minute").tag(1)
                            Text("After 5 minutes").tag(5)
                            Text("After 15 minutes").tag(15)
                        } label: {
                            Label {
                                Text("Lock Timeout")
                                    .font(AppTypography.body)
                            } icon: {
                                Image(systemName: "clock.fill")
                                    .foregroundStyle(.red)
                            }
                        }
                    }
                }
            }

            // MARK: - Support

            Section("Support") {
                NavigationLink {
                    FAQScreen()
                } label: {
                    Label {
                        VStack(alignment: .leading, spacing: AppSpacing.xs) {
                            Text("FAQ")
                                .font(AppTypography.body)
                            Text("Frequently asked questions")
                                .font(AppTypography.caption)
                                .foregroundStyle(.secondary)
                        }
                    } icon: {
                        Image(systemName: "questionmark.circle.fill")
                            .foregroundStyle(.blue)
                    }
                }

                Button {
                    if let url = URL(string: "https://github.com/nicekid1/pennywiseai-tracker/issues/new/choose") {
                        UIApplication.shared.open(url)
                    }
                } label: {
                    Label {
                        VStack(alignment: .leading, spacing: AppSpacing.xs) {
                            Text("Report an Issue")
                                .font(AppTypography.body)
                                .foregroundStyle(.primary)
                            Text("Open a bug report on GitHub")
                                .font(AppTypography.caption)
                                .foregroundStyle(.secondary)
                        }
                    } icon: {
                        Image(systemName: "ladybug.fill")
                            .foregroundStyle(.orange)
                    }
                }
            }

            // MARK: - About

            Section("About") {
                NavigationLink {
                    AboutScreen()
                } label: {
                    Label {
                        VStack(alignment: .leading, spacing: AppSpacing.xs) {
                            Text("About PennyWise")
                                .font(AppTypography.body)
                            Text("Version, links, and credits")
                                .font(AppTypography.caption)
                                .foregroundStyle(.secondary)
                        }
                    } icon: {
                        Image(systemName: "info.circle.fill")
                            .foregroundStyle(.gray)
                    }
                }
            }
        }
        .navigationTitle("Settings")
        .sheet(isPresented: $showingDocumentPicker) {
            DocumentPicker { url in
                exportImportManager.importBackup(from: url)
            }
        }
        .overlay {
            if let message = exportImportManager.statusMessage {
                VStack {
                    Spacer()
                    Text(message)
                        .font(AppTypography.caption)
                        .padding(AppSpacing.md)
                        .background(.regularMaterial)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                        .padding(.bottom, AppSpacing.lg)
                }
                .onTapGesture {
                    exportImportManager.statusMessage = nil
                }
                .onAppear {
                    DispatchQueue.main.asyncAfter(deadline: .now() + 4) {
                        exportImportManager.statusMessage = nil
                    }
                }
            }
        }
    }
}
