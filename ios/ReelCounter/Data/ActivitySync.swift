import BackgroundTasks
import Foundation
import ReelCounterCore
import Supabase

/**
 Uploads this iPhone's daily totals so friends (on iPhone or Android) can see them.

 - Rows are absolute daily totals upserted on (user, device, platform, date): retrying is safe.
 - Each upload carries a newer `revision`, so a late, older upload can't overwrite newer figures.
 - The device id is random per install; no hardware identifiers.
 */
@MainActor
final class ActivitySync {
    nonisolated static let backgroundTaskId = "com.reelcounter.app.sync"

    private let tracking: TrackingStore
    private let defaults = UserDefaults.standard
    private var isSyncing = false

    private enum Key {
        static let deviceId = "sync.deviceId"
        static let registeredFor = "sync.deviceRegisteredFor"
        static let lastSync = "sync.lastSync"
    }

    /// Days re-sent on each sync: today and the days before it that may still have changed.
    private let daysToSend = 7

    init(tracking: TrackingStore) {
        self.tracking = tracking
    }

    var lastSync: Date? { defaults.object(forKey: Key.lastSync) as? Date }

    private var deviceId: String {
        if let id = defaults.string(forKey: Key.deviceId) { return id }
        let id = UUID().uuidString.lowercased()
        defaults.set(id, forKey: Key.deviceId)
        return id
    }

    /// Uploads recent days. Quietly does nothing when signed out, not consented or offline.
    @discardableResult
    func syncNow() async -> Bool {
        guard !isSyncing, let client = Backend.client, tracking.disclosureAccepted,
              let session = client.auth.currentSession else { return false }
        isSyncing = true
        defer { isSyncing = false }
        let userId = session.user.id.uuidString.lowercased()
        let today = DayKey.today()
        let days = tracking.days(today.daysEnding(count: daysToSend))
        guard !days.isEmpty else { return true }

        do {
            if defaults.string(forKey: Key.registeredFor) != userId {
                try await client.from("devices")
                    .upsert(DeviceRow(id: deviceId, userId: userId), onConflict: "id", ignoreDuplicates: true)
                    .execute()
                defaults.set(userId, forKey: Key.registeredFor)
            }
            let revision = Int64(Date().timeIntervalSince1970 * 1_000)
            let rows = days.map {
                DailySummaryUpload(day: $0, userId: userId, deviceId: deviceId, timeZone: .current, today: today, revision: revision)
            }
            try await client.from("daily_activity_summaries")
                .upsert(rows, onConflict: "user_id,device_id,platform_id,activity_date")
                .execute()
            defaults.set(Date(), forKey: Key.lastSync)
            return true
        } catch {
            if String(describing: error).contains("42501") {
                // The device id isn't ours on the server (e.g. used by another account): start over.
                defaults.removeObject(forKey: Key.deviceId)
                defaults.removeObject(forKey: Key.registeredFor)
            }
            return false
        }
    }

    /// Forgets the device registration (sign-out), so the next account registers a fresh device id.
    func reset() {
        defaults.removeObject(forKey: Key.deviceId)
        defaults.removeObject(forKey: Key.registeredFor)
        defaults.removeObject(forKey: Key.lastSync)
    }

    // MARK: Background refresh

    /// Asks iOS to wake the app now and then to upload (iOS decides when; often every few hours).
    nonisolated static func scheduleBackgroundSync() {
        let request = BGAppRefreshTaskRequest(identifier: backgroundTaskId)
        request.earliestBeginDate = Date(timeIntervalSinceNow: 30 * 60)
        try? BGTaskScheduler.shared.submit(request)
    }
}

private struct DeviceRow: Encodable {
    let id: String
    let userId: String

    enum CodingKeys: String, CodingKey {
        case id
        case userId = "user_id"
    }
}
