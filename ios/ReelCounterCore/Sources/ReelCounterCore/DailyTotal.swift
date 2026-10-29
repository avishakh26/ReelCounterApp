import Foundation

/**
 One row of the server view `daily_activity_totals`: a user's totals for one platform and day,
 added up across their devices. Postgres sums arrive as JSON numbers that may be decimals, so
 numbers are decoded leniently.
 */
public struct DailyTotal: Decodable, Equatable, Sendable {
    public let userId: String
    public let platformId: String
    public let activityDate: String
    public let foregroundMs: Int64?
    public let estimatedReels: Int?
    public let estimatedReelsLow: Int?
    public let estimatedReelsHigh: Int?
    public let verifiedReels: Int?
    /// VERIFIED, COUNTED, ESTIMATED, DURATION_ONLY or UNAVAILABLE: the best category across devices.
    public let bestQuality: String
    /// Reels counted one by one on an Android phone; absent until the counted-reels migration runs.
    public let countedReels: Int?

    enum CodingKeys: String, CodingKey {
        case userId = "user_id"
        case platformId = "platform_id"
        case activityDate = "activity_date"
        case foregroundMs = "foreground_ms"
        case estimatedReels = "estimated_reels"
        case estimatedReelsLow = "estimated_reels_low"
        case estimatedReelsHigh = "estimated_reels_high"
        case verifiedReels = "verified_reels"
        case bestQuality = "best_quality"
        case countedReels = "counted_reels"
    }

    public init(userId: String, platformId: String, activityDate: String, foregroundMs: Int64? = nil,
                estimatedReels: Int? = nil, estimatedReelsLow: Int? = nil, estimatedReelsHigh: Int? = nil,
                verifiedReels: Int? = nil, bestQuality: String, countedReels: Int? = nil) {
        self.userId = userId
        self.platformId = platformId
        self.activityDate = activityDate
        self.foregroundMs = foregroundMs
        self.estimatedReels = estimatedReels
        self.estimatedReelsLow = estimatedReelsLow
        self.estimatedReelsHigh = estimatedReelsHigh
        self.verifiedReels = verifiedReels
        self.bestQuality = bestQuality
        self.countedReels = countedReels
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        userId = try c.decode(String.self, forKey: .userId)
        platformId = try c.decode(String.self, forKey: .platformId)
        activityDate = try c.decode(String.self, forKey: .activityDate)
        foregroundMs = try c.lenientNumber(.foregroundMs).map { Int64($0) }
        estimatedReels = try c.lenientNumber(.estimatedReels).map { Int($0) }
        estimatedReelsLow = try c.lenientNumber(.estimatedReelsLow).map { Int($0) }
        estimatedReelsHigh = try c.lenientNumber(.estimatedReelsHigh).map { Int($0) }
        verifiedReels = try c.lenientNumber(.verifiedReels).map { Int($0) }
        bestQuality = try c.decodeIfPresent(String.self, forKey: .bestQuality) ?? "UNAVAILABLE"
        countedReels = try c.lenientNumber(.countedReels).map { Int($0) }
    }

    /// The same categories as Android's `NetworkDailyTotal.toActivity()`.
    public func toPlatformDay() -> PlatformDay? {
        guard let platform = PlatformID(rawValue: platformId), let date = DayKey(rawValue: activityDate) else { return nil }
        let appTime = foregroundMs.map { max($0, 0) }
        let metric: PlatformMetric
        if bestQuality == "VERIFIED", let verified = verifiedReels {
            metric = .verified(count: verified, appTimeMs: appTime)
        } else if bestQuality == "COUNTED", let counted = countedReels {
            metric = .counted(count: counted, appTimeMs: appTime ?? 0)
        } else if let estimated = estimatedReels, let low = estimatedReelsLow, let high = estimatedReelsHigh {
            metric = .estimated(count: estimated, low: min(low, estimated), high: max(high, estimated), appTimeMs: appTime ?? 0)
        } else if let appTime {
            metric = .durationOnly(appTimeMs: appTime)
        } else {
            metric = .unavailable
        }
        return PlatformDay(platform: platform, date: date, metric: metric)
    }
}

private extension KeyedDecodingContainer {
    /// A number sent as an integer, a decimal or a string ("123"); nil when absent or null.
    func lenientNumber(_ key: Key) throws -> Double? {
        guard contains(key), try !decodeNil(forKey: key) else { return nil }
        if let value = try? decode(Double.self, forKey: key) { return value }
        if let text = try? decode(String.self, forKey: key), let value = Double(text) { return value }
        return nil
    }
}

/// One row the app uploads to `daily_activity_summaries`: absolute daily totals, never deltas.
public struct DailySummaryUpload: Encodable, Equatable, Sendable {
    public let userId: String
    public let deviceId: String
    public let platformId: String
    public let activityDate: String
    public let tzId: String
    public let foregroundMs: Int64
    public let sessionCount: Int
    public let estimatedReels: Int?
    public let estimatedReelsLow: Int?
    public let estimatedReelsHigh: Int?
    public let quality: String
    public let source: String
    public let estimationVersion: Int?
    public let userAdjusted: Bool
    public let isFinal: Bool
    /// Client-monotonic: the server ignores an upload whose revision isn't higher than what it has.
    public let revision: Int64

    enum CodingKeys: String, CodingKey {
        case userId = "user_id"
        case deviceId = "device_id"
        case platformId = "platform_id"
        case activityDate = "activity_date"
        case tzId = "tz_id"
        case foregroundMs = "foreground_ms"
        case sessionCount = "session_count"
        case estimatedReels = "estimated_reels"
        case estimatedReelsLow = "estimated_reels_low"
        case estimatedReelsHigh = "estimated_reels_high"
        case quality
        case source
        case estimationVersion = "estimation_version"
        case userAdjusted = "user_adjusted"
        case isFinal = "is_final"
        case revision
    }

    /// The upload for one measured day. Days before today are final.
    public init(day: PlatformDay, userId: String, deviceId: String, timeZone: TimeZone, today: DayKey, revision: Int64) {
        self.userId = userId
        self.deviceId = deviceId
        platformId = day.platform.rawValue
        activityDate = day.date.rawValue
        tzId = timeZone.identifier
        foregroundMs = min(day.metric.appTimeMs ?? 0, 90_000_000)
        // Screen Time reports app time, not separate sessions.
        sessionCount = 0
        switch day.metric {
        case .estimated(let count, let low, let high, _):
            estimatedReels = count
            estimatedReelsLow = low
            estimatedReelsHigh = high
            quality = "ESTIMATED"
            estimationVersion = day.platform.profile.version
        default:
            estimatedReels = nil
            estimatedReelsLow = nil
            estimatedReelsHigh = nil
            quality = "DURATION_ONLY"
            estimationVersion = nil
        }
        // The server's activity_source has no Screen Time value; "usage_stats" means on-device usage data.
        source = "usage_stats"
        userAdjusted = false
        isFinal = day.date < today
        self.revision = revision
    }
}
