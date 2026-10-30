import DeviceActivity
import FamilyControls
import Foundation
import ReelCounterCore

/**
 App time from Apple's Screen Time, for the apps the user assigns to each platform.

 iOS doesn't let apps find other apps by name: the user picks TikTok, Instagram, YouTube and
 Facebook in Apple's own picker, and ReelCounter only gets opaque tokens for them. It then asks
 Screen Time to report each usage step (see `UsageThresholds`), which the monitor extension records.
 */
@MainActor
final class TrackingStore: ObservableObject {
    enum Status: Equatable {
        /// The privacy notice hasn't been accepted (or a newer version needs accepting).
        case needsDisclosure
        /// Screen Time access hasn't been allowed.
        case needsAuthorization
        /// No app has been picked for any platform.
        case needsApps
        case active
        /// This edition can't use Screen Time: the free build has no Family Controls entitlement.
        case unavailable
    }

    /**
     False in the free edition (built with FREE_EDITION for installing with a free Apple ID).
     Apple only grants Screen Time access (Family Controls) to paid developer accounts.
     */
    static let isAvailable: Bool = {
        #if FREE_EDITION
        return false
        #else
        return true
        #endif
    }()

    /// Bump when the notice changes so every user sees and accepts it again.
    static let disclosureVersion = 1

    @Published private(set) var status: Status = .needsDisclosure
    @Published private(set) var selections: [PlatformID: FamilyActivitySelection] = [:]
    @Published private(set) var lastError: AppError?
    /// Changes whenever new usage may have been recorded, so screens reload.
    @Published private(set) var revision = 0

    let usage = UsageStore.shared()
    private let defaults = UserDefaults.standard
    private lazy var center = DeviceActivityCenter()

    private enum Key {
        static let disclosure = "tracking.disclosureVersion"
        static let selections = "tracking.selections.v1"
    }

    init() {
        selections = loadSelections()
        refreshStatus()
    }

    var disclosureAccepted: Bool { defaults.integer(forKey: Key.disclosure) >= Self.disclosureVersion }

    /// Platforms with at least one app picked.
    var trackedPlatforms: Set<PlatformID> {
        Set(selections.filter { !$0.value.isEmpty }.map(\.key))
    }

    func refreshStatus() {
        if !Self.isAvailable {
            status = .unavailable
        } else if !disclosureAccepted {
            status = .needsDisclosure
        } else if AuthorizationCenter.shared.authorizationStatus != .approved {
            status = .needsAuthorization
        } else if trackedPlatforms.isEmpty {
            status = .needsApps
        } else {
            status = .active
        }
        revision += 1
    }

    // MARK: Setup

    func acceptDisclosure() {
        defaults.set(Self.disclosureVersion, forKey: Key.disclosure)
        refreshStatus()
    }

    /// Shows Apple's Screen Time permission prompt (Face ID / passcode).
    func requestAuthorization() async {
        guard Self.isAvailable else { return }
        lastError = nil
        do {
            try await AuthorizationCenter.shared.requestAuthorization(for: .individual)
        } catch {
            lastError = AppError(message: "Screen Time access wasn't allowed. You can try again, or turn it on later in Settings → Screen Time.")
        }
        refreshStatus()
    }

    func setSelection(_ selection: FamilyActivitySelection, for platform: PlatformID) {
        if selection.isEmpty {
            selections.removeValue(forKey: platform)
        } else {
            selections[platform] = selection
        }
        saveSelections()
        startMonitoring()
        refreshStatus()
    }

    /**
     (Re)starts the daily Screen Time schedules, one per platform, each with an event per usage step.
     Restarting begins counting from now, so it is only done when the picked apps change.
     */
    func startMonitoring() {
        guard Self.isAvailable else { return }
        center.stopMonitoring()
        let schedule = DeviceActivitySchedule(
            intervalStart: DateComponents(hour: 0, minute: 0, second: 0),
            intervalEnd: DateComponents(hour: 23, minute: 59, second: 59),
            repeats: true
        )
        var started = false
        for (platform, selection) in selections where !selection.isEmpty {
            var events: [DeviceActivityEvent.Name: DeviceActivityEvent] = [:]
            for minutes in UsageThresholds.minutes {
                events[DeviceActivityEvent.Name(UsageThresholds.eventName(platform: platform, minutes: minutes))] = DeviceActivityEvent(
                    applications: selection.applicationTokens,
                    categories: selection.categoryTokens,
                    webDomains: selection.webDomainTokens,
                    threshold: DateComponents(hour: minutes / 60, minute: minutes % 60)
                )
            }
            do {
                try center.startMonitoring(
                    DeviceActivityName(UsageThresholds.activityName(platform: platform)),
                    during: schedule,
                    events: events
                )
                started = true
            } catch {
                lastError = AppError(message: "Screen Time couldn't start measuring \(platform.displayName). Try picking its app again.")
            }
        }
        if started { usage.monitoringSince = Date() }
    }

    /// Stops measuring and forgets the picked apps and recorded usage (used on sign-out).
    func reset() {
        if Self.isAvailable { center.stopMonitoring() }
        selections = [:]
        defaults.removeObject(forKey: Key.selections)
        usage.clear()
        refreshStatus()
    }

    /// Stops measuring but keeps the history already recorded.
    func stopTracking() {
        if Self.isAvailable { center.stopMonitoring() }
        selections = [:]
        defaults.removeObject(forKey: Key.selections)
        refreshStatus()
    }

    // MARK: Reading

    func days(_ keys: [DayKey]) -> [PlatformDay] {
        let today = DayKey.today()
        let tracked = trackedPlatforms
        return keys.flatMap { usage.platformDays(on: $0, tracked: tracked, today: today) }
    }

    /// Called when the app returns to the front: the extension may have recorded new steps.
    func reload() {
        objectWillChange.send()
        revision += 1
    }

    // MARK: Storage

    private func loadSelections() -> [PlatformID: FamilyActivitySelection] {
        guard let data = defaults.data(forKey: Key.selections),
              let raw = try? JSONDecoder().decode([String: FamilyActivitySelection].self, from: data)
        else { return [:] }
        var result: [PlatformID: FamilyActivitySelection] = [:]
        for (key, value) in raw {
            if let platform = PlatformID(rawValue: key) { result[platform] = value }
        }
        return result
    }

    private func saveSelections() {
        let raw = Dictionary(uniqueKeysWithValues: selections.map { ($0.key.rawValue, $0.value) })
        if let data = try? JSONEncoder().encode(raw) {
            defaults.set(data, forKey: Key.selections)
        }
    }
}

extension FamilyActivitySelection {
    var isEmpty: Bool {
        applicationTokens.isEmpty && categoryTokens.isEmpty && webDomainTokens.isEmpty
    }

    var itemCount: Int {
        applicationTokens.count + categoryTokens.count + webDomainTokens.count
    }
}
