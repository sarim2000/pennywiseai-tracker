import Shared
import SwiftUI

enum AppTab: String, CaseIterable {
    case home = "Home"
    case transactions = "Transactions"
    case analytics = "Analytics"
    case chat = "Chat"
    case settings = "Settings"

    var icon: String {
        switch self {
        case .home: "house.fill"
        case .transactions: "list.bullet"
        case .analytics: "chart.bar.fill"
        case .chat: "sparkles"
        case .settings: "gearshape.fill"
        }
    }
}

struct MainTabView: View {
    @Binding var showQuickAdd: Bool

    @State private var selectedTab: AppTab = .home
    @ObservedObject private var themeManager = ThemeManager.shared
    @ObservedObject private var appLockManager = AppLockManager.shared
    @AppStorage("hasCompletedOnboarding") private var hasCompletedOnboarding = false
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.scenePhase) private var scenePhase
    // Re-read on every return to the app: the user may have just turned Apple Intelligence on.
    @State private var aiStatus = OnDeviceAI.status

    /// The Chat tab exists only while the on-device model can run.
    private var showsChat: Bool {
        #if DEBUG
        if ProcessInfo.processInfo.arguments.contains("-PennyAIDemo") { return true }
        #endif
        return aiStatus == .available
    }

    private var isAmoled: Bool {
        themeManager.isAmoledActive(for: colorScheme)
    }

    var body: some View {
        if hasCompletedOnboarding {
            ZStack {
                if isAmoled {
                    AppColors.amoledBackground.ignoresSafeArea()
                }

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
                        TransactionListView(facade: PennyWiseSharedFacade.companion.shared)
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

                    #if canImport(FoundationModels)
                    if #available(iOS 26.0, *), showsChat {
                        NavigationStack {
                            ChatScreen()
                        }
                        .tabItem {
                            Label(AppTab.chat.rawValue, systemImage: AppTab.chat.icon)
                        }
                        .tag(AppTab.chat)
                    }
                    #endif

                    NavigationStack {
                        SettingsScreen()
                    }
                    .tabItem {
                        Label(AppTab.settings.rawValue, systemImage: AppTab.settings.icon)
                    }
                    .tag(AppTab.settings)
                }
                .tint(themeManager.accentColor)
                // Quick add from the pennywise://add deep link, so it works
                // whichever tab is showing. Gated on the app lock: a sheet
                // would otherwise draw over the lock screen.
                .sheet(isPresented: Binding(
                    get: { showQuickAdd && !appLockManager.isLocked },
                    set: { showQuickAdd = $0 }
                )) {
                    NavigationStack {
                        AddEditTransactionView(facade: PennyWiseSharedFacade.companion.shared)
                    }
                }

                if appLockManager.isLocked {
                    LockScreenView()
                }
            }
            .environment(\.isAmoledActive, isAmoled)
            .preferredColorScheme(themeManager.colorScheme)
            .onChange(of: scenePhase) { phase in
                guard phase == .active else { return }
                aiStatus = OnDeviceAI.status
                if !showsChat && selectedTab == .chat { selectedTab = .home }
                // Bank texts the model couldn't read in the background.
                Task { await PendingAiSms.processAll() }
            }
            .task { await PendingAiSms.processAll() }
        } else {
            OnboardingScreen {
                hasCompletedOnboarding = true
            }
            .preferredColorScheme(themeManager.colorScheme)
        }
    }
}
