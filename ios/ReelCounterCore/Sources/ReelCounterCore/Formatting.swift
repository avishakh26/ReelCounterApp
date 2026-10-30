import Foundation

public enum Format {
    /// "0 min", "42 min", "1 h 5 min", "3 h". Rounded down to whole minutes.
    public static func appTime(ms: Int64) -> String {
        let totalMinutes = Int(max(ms, 0) / 60_000)
        let hours = totalMinutes / 60
        let minutes = totalMinutes % 60
        switch (hours, minutes) {
        case (0, _): return "\(minutes) min"
        case (_, 0): return "\(hours) h"
        default: return "\(hours) h \(minutes) min"
        }
    }

    /// "42m", "1h 5m": for chart labels and tight rows.
    public static func compactAppTime(ms: Int64) -> String {
        let totalMinutes = Int(max(ms, 0) / 60_000)
        let hours = totalMinutes / 60
        let minutes = totalMinutes % 60
        switch (hours, minutes) {
        case (0, _): return "\(minutes)m"
        case (_, 0): return "\(hours)h"
        default: return "\(hours)h \(minutes)m"
        }
    }

    /// 1234 → "1,234" in the user's locale.
    public static func count(_ value: Int, locale: Locale = .current) -> String {
        let formatter = NumberFormatter()
        formatter.numberStyle = .decimal
        formatter.locale = locale
        return formatter.string(from: NSNumber(value: value)) ?? "\(value)"
    }

    /// "≈ 1,234" for estimates, "1,234" for exact counts.
    public static func reels(_ value: Int, isEstimate: Bool, locale: Locale = .current) -> String {
        isEstimate ? "≈ \(count(value, locale: locale))" : count(value, locale: locale)
    }

    /// "Likely 55–1,110", or nil when the range is a single value (it would say nothing).
    public static func likelyRange(low: Int, high: Int, locale: Locale = .current) -> String? {
        guard low != high else { return nil }
        return "Likely \(count(low, locale: locale))–\(count(high, locale: locale))"
    }
}
