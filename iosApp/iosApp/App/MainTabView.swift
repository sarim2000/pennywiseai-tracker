import Shared
import SwiftUI

enum AppTab: String, CaseIterable {
    case home = "Home"
    case transactions = "Transactions"
    case analytics = "Analytics"
    case settings = "Settings"

    var icon: String {
        switch self {
        case .home: "house.fill"
        case .transactions: "list.bullet"
        case .analytics: "chart.bar.fill"
        case .settings: "gearshape.fill"
        }
    }
}

struct MainTabView: View {
    @State private var selectedTab: AppTab = .home
    @ObservedObject private var themeManager = ThemeManager.shared
    @ObservedObject private var appLockManager = AppLockManager.shared

    var body: some View {
        ZStack {
            TabView(selection: $selectedTab) {
                NavigationStack {
                    HomeScreen(onSeeAllTransactions: { selectedTab = .transactions })
                        .navigationTitle("PennyWise")
                }
                .tabItem {
                    Label(AppTab.home.rawValue, systemImage: AppTab.home.icon)
                }
                .tag(AppTab.home)

                NavigationStack {
                    TransactionListView(facade: PennyWiseSharedFacade())
                }
                .tabItem {
                    Label(AppTab.transactions.rawValue, systemImage: AppTab.transactions.icon)
                }
                .tag(AppTab.transactions)

                NavigationStack {
                    AnalyticsScreen()
                }
                .tabItem {
                    Label(AppTab.analytics.rawValue, systemImage: AppTab.analytics.icon)
                }
                .tag(AppTab.analytics)

                NavigationStack {
                    SettingsScreen()
                }
                .tabItem {
                    Label(AppTab.settings.rawValue, systemImage: AppTab.settings.icon)
                }
                .tag(AppTab.settings)
            }
            .tint(themeManager.accentColor)

            if appLockManager.isLocked {
                LockScreenView()
            }
        }
        .preferredColorScheme(themeManager.colorScheme)
    }
}
