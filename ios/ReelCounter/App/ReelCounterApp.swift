import BackgroundTasks
import SwiftUI

@main
struct ReelCounterApp: App {
    @StateObject private var session: SessionStore
    @StateObject private var tracking: TrackingStore
    @StateObject private var friends: FriendsStore
    private let sync: ActivitySync
    @Environment(\.scenePhase) private var scenePhase

    init() {
        let session = SessionStore()
        let tracking = TrackingStore()
        let friends = FriendsStore()
        let sync = ActivitySync(tracking: tracking)
        // Another account may sign in next on this phone: forget this one's local data.
        session.onSignedOut = {
            tracking.reset()
            sync.reset()
            Task { await friends.reset() }
        }
        _session = StateObject(wrappedValue: session)
        _tracking = StateObject(wrappedValue: tracking)
        _friends = StateObject(wrappedValue: friends)
        self.sync = sync

        BGTaskScheduler.shared.register(forTaskWithIdentifier: ActivitySync.backgroundTaskId, using: nil) { task in
            ActivitySync.scheduleBackgroundSync()
            let work = Task { @MainActor in
                let ok = await sync.syncNow()
                task.setTaskCompleted(success: ok)
            }
            task.expirationHandler = { work.cancel() }
        }
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(session)
                .environmentObject(tracking)
                .environmentObject(friends)
                .environment(\.activitySync, sync)
                .tint(Palette.accent)
        }
        .onChange(of: scenePhase) { phase in
            switch phase {
            case .active:
                tracking.refreshStatus()
                Task { await sync.syncNow() }
            case .background:
                ActivitySync.scheduleBackgroundSync()
            default:
                break
            }
        }
    }
}

private struct ActivitySyncKey: EnvironmentKey {
    static let defaultValue: ActivitySync? = nil
}

extension EnvironmentValues {
    var activitySync: ActivitySync? {
        get { self[ActivitySyncKey.self] }
        set { self[ActivitySyncKey.self] = newValue }
    }
}
