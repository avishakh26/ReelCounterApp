import Foundation

/**
 What is known about one platform on one day, and how it was measured. Every figure on screen carries
 its category (the honesty rules from the Android app):
 - verified and counted figures are exact;
 - estimates always show "≈" and a likely range;
 - a day without data is "No data", never 0.
 */
public enum PlatformMetric: Equatable, Sendable {
    /// The platform's own figure.
    case verified(count: Int, appTimeMs: Int64?)
    /// Counted one by one on the phone (Android's reel-counting service).
    case counted(count: Int, appTimeMs: Int64)
    /// Worked out from app time.
    case estimated(count: Int, low: Int, high: Int, appTimeMs: Int64)
    /// App time only, no reel count.
    case durationOnly(appTimeMs: Int64)
    /// Nothing measured.
    case unavailable

    public var reelCount: Int? {
        switch self {
        case .verified(let count, _), .counted(let count, _), .estimated(let count, _, _, _): return count
        case .durationOnly, .unavailable: return nil
        }
    }

    public var appTimeMs: Int64? {
        switch self {
        case .verified(_, let ms): return ms
        case .counted(_, let ms), .estimated(_, _, _, let ms), .durationOnly(let ms): return ms
        case .unavailable: return nil
        }
    }

    public var isEstimate: Bool {
        if case .estimated = self { return true }
        return false
    }
}

/// One platform's figures for one day.
public struct PlatformDay: Equatable, Sendable, Identifiable {
    public let platform: PlatformID
    public let date: DayKey
    public let metric: PlatformMetric

    public var id: String { "\(date.rawValue).\(platform.rawValue)" }

    public init(platform: PlatformID, date: DayKey, metric: PlatformMetric) {
        self.platform = platform
        self.date = date
        self.metric = metric
    }

    /// An estimate from app time measured on this iPhone (Screen Time).
    public static func estimated(platform: PlatformID, date: DayKey, appTimeMs: Int64) -> PlatformDay {
        let estimate = Estimator.estimate(appTimeMs: appTimeMs, profile: platform.profile)
        return PlatformDay(
            platform: platform,
            date: date,
            metric: .estimated(count: estimate.count, low: estimate.low, high: estimate.high, appTimeMs: appTimeMs)
        )
    }
}

/**
 One day across all platforms. Figures are nil when nothing was measured that day ("No data"),
 never 0, so they can be left out of averages.
 */
public struct DaySummary: Equatable, Sendable, Identifiable {
    public let date: DayKey
    public let count: Int?
    public let countLow: Int?
    public let countHigh: Int?
    public let appTimeMs: Int64?
    /// At least one platform's count is an estimate, so the total is shown with "≈".
    public let isEstimate: Bool

    public var id: String { date.rawValue }
    public var hasData: Bool { count != nil || appTimeMs != nil }

    public init(date: DayKey, count: Int?, countLow: Int?, countHigh: Int?, appTimeMs: Int64?, isEstimate: Bool) {
        self.date = date
        self.count = count
        self.countLow = countLow
        self.countHigh = countHigh
        self.appTimeMs = appTimeMs
        self.isEstimate = isEstimate
    }
}

public extension Array where Element == PlatformDay {
    /// Adds up one day's platforms. Exact counts add to both ends of the range.
    func summarize(date: DayKey) -> DaySummary {
        let day = filter { $0.date == date }
        var count: Int?
        var low: Int?
        var high: Int?
        var appTime: Int64?
        var isEstimate = false
        for item in day {
            switch item.metric {
            case .verified(let c, _), .counted(let c, _):
                count = (count ?? 0) + c
                low = (low ?? 0) + c
                high = (high ?? 0) + c
            case .estimated(let c, let l, let h, _):
                count = (count ?? 0) + c
                low = (low ?? 0) + l
                high = (high ?? 0) + h
                isEstimate = true
            case .durationOnly, .unavailable:
                break
            }
            if let ms = item.metric.appTimeMs {
                appTime = (appTime ?? 0) + ms
            }
        }
        return DaySummary(date: date, count: count, countLow: low, countHigh: high, appTimeMs: appTime, isEstimate: isEstimate)
    }
}

public extension Array where Element == DaySummary {
    /// Average reels per day over the days that have a count; nil when none do.
    var averageCount: Int? {
        let counts = compactMap(\.count)
        guard !counts.isEmpty else { return nil }
        return Int((Double(counts.reduce(0, +)) / Double(counts.count)).rounded())
    }

    var daysWithData: Int { filter(\.hasData).count }
}
