import Foundation

/// A calendar date in the user's time zone, as the server stores it ("yyyy-MM-dd").
public struct DayKey: Hashable, Comparable, Codable, Sendable, CustomStringConvertible {
    public let rawValue: String

    public init?(rawValue: String) {
        guard DayKey.formatter(TimeZone(identifier: "UTC")!).date(from: rawValue) != nil else { return nil }
        self.rawValue = rawValue
    }

    public init(date: Date, calendar: Calendar = .current) {
        let parts = calendar.dateComponents([.year, .month, .day], from: date)
        rawValue = String(format: "%04d-%02d-%02d", parts.year ?? 1970, parts.month ?? 1, parts.day ?? 1)
    }

    public static func today(calendar: Calendar = .current, now: Date = Date()) -> DayKey {
        DayKey(date: now, calendar: calendar)
    }

    /// Midnight at the start of this day in [calendar]'s time zone.
    public func startDate(calendar: Calendar = .current) -> Date {
        let parts = rawValue.split(separator: "-").compactMap { Int($0) }
        return calendar.date(from: DateComponents(year: parts[0], month: parts[1], day: parts[2])) ?? Date()
    }

    public func adding(days: Int, calendar: Calendar = .current) -> DayKey {
        let date = calendar.date(byAdding: .day, value: days, to: startDate(calendar: calendar)) ?? startDate(calendar: calendar)
        return DayKey(date: date, calendar: calendar)
    }

    /// [count] days ending with this one, oldest first.
    public func daysEnding(count: Int, calendar: Calendar = .current) -> [DayKey] {
        (0..<count).reversed().map { adding(days: -$0, calendar: calendar) }
    }

    public var description: String { rawValue }

    public static func < (lhs: DayKey, rhs: DayKey) -> Bool { lhs.rawValue < rhs.rawValue }

    public init(from decoder: Decoder) throws {
        let value = try decoder.singleValueContainer().decode(String.self)
        guard let key = DayKey(rawValue: value) else {
            throw DecodingError.dataCorrupted(.init(codingPath: decoder.codingPath, debugDescription: "Not a date: \(value)"))
        }
        self = key
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        try container.encode(rawValue)
    }

    private static func formatter(_ zone: TimeZone) -> DateFormatter {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = zone
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter
    }
}
