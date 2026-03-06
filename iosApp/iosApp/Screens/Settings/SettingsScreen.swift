import SwiftUI
import Shared

struct SettingsScreen: View {
    @ObservedObject private var currencyManager = CurrencyManager.shared

    var body: some View {
        List {
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
            }

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

                Label {
                    VStack(alignment: .leading, spacing: AppSpacing.xs) {
                        Text("Theme")
                            .font(AppTypography.body)
                        Text("Coming soon")
                            .font(AppTypography.caption)
                            .foregroundStyle(.secondary)
                    }
                } icon: {
                    Image(systemName: "paintbrush.fill")
                        .foregroundStyle(.orange)
                }
                .foregroundStyle(.secondary)
            }

            Section("About") {
                Label {
                    Text("Version 1.0")
                        .font(AppTypography.body)
                } icon: {
                    Image(systemName: "info.circle.fill")
                        .foregroundStyle(.gray)
                }
            }
        }
        .navigationTitle("Settings")
    }
}
