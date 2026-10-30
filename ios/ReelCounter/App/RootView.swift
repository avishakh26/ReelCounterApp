import SwiftUI

/// Chooses the screen for the current session state.
struct RootView: View {
    @EnvironmentObject private var session: SessionStore

    var body: some View {
        Group {
            switch session.state {
            case .notConfigured:
                NotConfiguredView()
            case .loading:
                SplashView()
            case .signedOut:
                AuthFlowView()
            case .failed(let error):
                ProfileLoadFailedView(error: error)
            case .needsProfile:
                OnboardingView()
            case .signedIn:
                MainTabView()
            }
        }
        .animation(.easeInOut(duration: 0.2), value: session.state)
    }
}

struct SplashView: View {
    var body: some View {
        VStack(spacing: Spacing.lg) {
            BrandMark(size: 72)
            ProgressView()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .screenBackground()
    }
}

/// The ReelCounter mark: the app icon's play tile on the accent color.
struct BrandMark: View {
    var size: CGFloat = 56

    var body: some View {
        Image(systemName: "play.rectangle.on.rectangle.fill")
            .font(.system(size: size * 0.45, weight: .semibold))
            .foregroundStyle(Palette.onAccent)
            .frame(width: size, height: size)
            .background(Palette.accent, in: RoundedRectangle(cornerRadius: size * 0.28, style: .continuous))
            .accessibilityHidden(true)
    }
}

struct NotConfiguredView: View {
    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.lg) {
            BrandMark()
            Text("Backend not configured").font(.rcLargeTitle)
            Text("This build has no Supabase project set. Copy ios/Config/Secrets.example.xcconfig to Secrets.xcconfig, fill in the project URL and publishable key, and build again.")
                .font(.rcBody)
                .foregroundStyle(Palette.textSecondary)
        }
        .padding(Spacing.screen)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
        .screenBackground()
    }
}

struct ProfileLoadFailedView: View {
    @EnvironmentObject private var session: SessionStore
    let error: AppError
    @State private var isRetrying = false

    var body: some View {
        VStack(spacing: Spacing.lg) {
            EmptyStateView(systemImage: "wifi.exclamationmark", title: "Couldn't load your account", message: error.message)
            Button("Try again") {
                isRetrying = true
                Task {
                    await session.loadProfile()
                    isRetrying = false
                }
            }
            .buttonStyle(PrimaryButtonStyle(isLoading: isRetrying))
            Button("Sign out") { Task { await session.signOut() } }
                .buttonStyle(SecondaryButtonStyle())
        }
        .padding(Spacing.screen)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .screenBackground()
    }
}

struct MainTabView: View {
    @EnvironmentObject private var session: SessionStore
    @EnvironmentObject private var friends: FriendsStore
    @State private var tab = Tab.today

    enum Tab: Hashable {
        case today, insights, friends, settings
    }

    var body: some View {
        TabView(selection: $tab) {
            NavigationStack { TodayView(onOpenSettings: { tab = .settings }) }
                .tabItem { Label("Today", systemImage: "house.fill") }
                .tag(Tab.today)
            NavigationStack { InsightsView() }
                .tabItem { Label("Insights", systemImage: "chart.bar.fill") }
                .tag(Tab.insights)
            NavigationStack { FriendsView() }
                .tabItem { Label("Friends", systemImage: "person.2.fill") }
                .tag(Tab.friends)
                .badge(friends.incoming.count)
            NavigationStack { SettingsView() }
                .tabItem { Label("Settings", systemImage: "gearshape.fill") }
                .tag(Tab.settings)
        }
        .task(id: session.userId) {
            guard let userId = session.userId else { return }
            await friends.refresh()
            await friends.startListening(userId: userId)
        }
    }
}
