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

    var body: some View {
        TabView(selection: $selectedTab) {
            NavigationStack {
                HomeScreen()
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
                AnalyticsPlaceholderScreen()
                    .navigationTitle("Analytics")
            }
            .tabItem {
                Label(AppTab.analytics.rawValue, systemImage: AppTab.analytics.icon)
            }
            .tag(AppTab.analytics)

            NavigationStack {
                SettingsPlaceholderScreen()
                    .navigationTitle("Settings")
            }
            .tabItem {
                Label(AppTab.settings.rawValue, systemImage: AppTab.settings.icon)
            }
            .tag(AppTab.settings)
        }
    }
}
