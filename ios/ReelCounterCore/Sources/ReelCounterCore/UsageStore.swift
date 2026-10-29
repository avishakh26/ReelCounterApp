import Foundation

/**
 Minutes of app time per day and platform, shared through the App Group between the Screen Time
 extension (which writes) and the app (which reads, shows and uploads).

 Only the highest threshold reached is kept: thresholds fire in order, and a repeated or late event
 can never lower a figure.
 */
public final class UsageStore: @unchecked Sendable {
    public static let appGroup = "group.com.reelcounter.app"

    private let defaults: UserDefaults
    private let calendar: Calendar
    private let lock = NSLock()

    private enum Key {
        static let usage = "usage.v1"
        static let monitoringSince = "monitoringSince.v1"
    }

    /// Days of history kept on the phone.
    public static let keepDays = 60

    public init(defaults: UserDefaults, calendar: Calendar = .current) {
        self.defaults = defaults
        self.calendar = calendar
    }

    /// The store in the App Group; falls back to the app's own defaults if the group is unavailable.
    public static func shared() -> UsageStore {
        UsageStore(defaults: UserDefaults(suiteName: appGroup) ?? .standard)
    }

    // MARK: Monitoring

    /// When Screen Time monitoring last started; thresholds can't have been reached before it.
    public var monitoringSince: Date? {
        get { defaults.object(forKey: Key.monitoringSince) as? Date }
        set { defaults.set(newValue, forKey: Key.monitoringSince) }
    }

    // MARK: Writing (extension)

    /**
     Records that [platform] reached [minutes] at [now]. Ignores events that can't be real, such as
     more minutes than have passed since midnight or since monitoring started (iOS sometimes fires
     thresholds early right after monitoring begins). Returns whether the figure went up.
     */
    @discardableResult
    public func record(platform: PlatformID, minutes: Int, at now: Date = Date()) -> Bool {
        let day = DayKey(date: now, calendar: calendar)
        let start = max(day.startDate(calendar: calendar), monitoringSince ?? .distantPast)
        let possibleMinutes = Int(now.timeIntervalSince(start) / 60) + 1
        guard minutes <= possibleMinutes else { return false }

        lock.lock()
        defer { lock.unlock() }
        var all = load()
        var dayUsage = all[day.rawValue] ?? [:]
        guard minutes > (dayUsage[platform.rawValue] ?? 0) else { return false }
        dayUsage[platform.rawValue] = minutes
        all[day.rawValue] = dayUsage
        save(prune(all, today: day))
        return true
    }

    // MARK: Reading (app)

    /// Minutes per platform on [day]; empty when nothing was recorded.
    public func minutes(on day: DayKey) -> [PlatformID: Int] {
        let raw = load()[day.rawValue] ?? [:]
        var result: [PlatformID: Int] = [:]
        for (key, value) in raw {
            if let platform = PlatformID(rawValue: key) { result[platform] = value }
        }
        return result
    }

    /// Days that have any figure, oldest first.
    public func recordedDays() -> [DayKey] {
        load().keys.compactMap(DayKey.init(rawValue:)).sorted()
    }

    /**
     [day]'s figures for the platforms being tracked. A tracked platform without a recorded threshold
     shows 0 when monitoring covered the day (Screen Time would have told us otherwise), and is left
     out of days before monitoring began ("No data").
     */
    public func platformDays(on day: DayKey, tracked: Set<PlatformID>, today: DayKey) -> [PlatformDay] {
        let recorded = minutes(on: day)
        let monitored: Bool = {
            guard let since = monitoringSince else { return false }
            return DayKey(date: since, calendar: calendar) <= day && day <= today
        }()
        return PlatformID.ordered.compactMap { platform in
            if let minutes = recorded[platform] {
                return .estimated(platform: platform, date: day, appTimeMs: Int64(minutes) * 60_000)
            }
            guard monitored, tracked.contains(platform) else { return nil }
            return .estimated(platform: platform, date: day, appTimeMs: 0)
        }
    }

    /// Forgets all recorded usage (sign-out or turning tracking off).
    public func clear() {
        lock.lock()
        defer { lock.unlock() }
        defaults.removeObject(forKey: Key.usage)
        defaults.removeObject(forKey: Key.monitoringSince)
    }

    // MARK: Storage

    private func load() -> [String: [String: Int]] {
        guard let data = defaults.data(forKey: Key.usage),
              let value = try? JSONDecoder().decode([String: [String: Int]].self, from: data)
        else { return [:] }
        return value
    }

    private func save(_ value: [String: [String: Int]]) {
        if let data = try? JSONEncoder().encode(value) {
            defaults.set(data, forKey: Key.usage)
        }
    }

    private func prune(_ value: [String: [String: Int]], today: DayKey) -> [String: [String: Int]] {
        let oldest = today.adding(days: -UsageStore.keepDays, calendar: calendar).rawValue
        return value.filter { $0.key >= oldest }
    }
}
