import XCTest
@testable import ReelCounterCore

final class EstimatorTests: XCTestCase {
    func testMatchesTheAndroidFormula() {
        // 7 h 42 min of YouTube: ≈ 277, likely 55–1,109 (worked out by hand from Android's formula).
        let estimate = Estimator.estimate(appTimeMs: (7 * 60 + 42) * 60_000, profile: PlatformID.youtube.profile)
        XCTAssertEqual(estimate, ReelEstimate(count: 277, low: 55, high: 1_109))
    }

    func testNoAppTimeMeansNoReels() {
        XCTAssertEqual(Estimator.estimate(appTimeMs: 0, profile: PlatformID.tiktok.profile), ReelEstimate(count: 0, low: 0, high: 0))
        XCTAssertEqual(Estimator.estimate(appTimeMs: -5, profile: PlatformID.tiktok.profile).count, 0)
    }

    func testRangeAlwaysContainsTheCount() {
        for platform in PlatformID.allCases {
            for minutes in [1, 7, 33, 120, 600] {
                let e = Estimator.estimate(appTimeMs: Int64(minutes) * 60_000, profile: platform.profile)
                XCTAssertLessThanOrEqual(e.low, e.count)
                XCTAssertGreaterThanOrEqual(e.high, e.count)
            }
        }
    }
}

final class ThresholdTests: XCTestCase {
    func testStepsAreIncreasingAndStartAtOneMinute() {
        XCTAssertEqual(UsageThresholds.minutes.first, 1)
        XCTAssertEqual(UsageThresholds.minutes, UsageThresholds.minutes.sorted())
        XCTAssertEqual(Set(UsageThresholds.minutes).count, UsageThresholds.minutes.count)
        XCTAssertLessThanOrEqual(UsageThresholds.minutes.count, 50)
    }

    func testEventNamesRoundTrip() {
        let name = UsageThresholds.eventName(platform: .instagram, minutes: 45)
        let parsed = UsageThresholds.parse(eventName: name)
        XCTAssertEqual(parsed?.platform, .instagram)
        XCTAssertEqual(parsed?.minutes, 45)
    }

    func testForeignNamesAreIgnored() {
        XCTAssertNil(UsageThresholds.parse(eventName: "other.tiktok.5"))
        XCTAssertNil(UsageThresholds.parse(eventName: "rc.snapchat.5"))
        XCTAssertNil(UsageThresholds.parse(eventName: "rc.tiktok"))
        XCTAssertNil(UsageThresholds.parse(eventName: "rc.tiktok.0"))
    }
}

final class UsageStoreTests: XCTestCase {
    private var calendar: Calendar = {
        var c = Calendar(identifier: .gregorian)
        c.timeZone = TimeZone(identifier: "Asia/Dhaka")!
        return c
    }()

    private func makeStore() -> UsageStore {
        let name = "test.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: name)!
        defaults.removePersistentDomain(forName: name)
        return UsageStore(defaults: defaults, calendar: calendar)
    }

    private func at(_ day: Int, _ hour: Int, _ minute: Int = 0) -> Date {
        calendar.date(from: DateComponents(year: 2026, month: 10, day: day, hour: hour, minute: minute))!
    }

    func testKeepsTheHighestThreshold() {
        let store = makeStore()
        store.record(platform: .tiktok, minutes: 10, at: at(29, 12))
        store.record(platform: .tiktok, minutes: 5, at: at(29, 12, 5))
        XCTAssertEqual(store.minutes(on: DayKey(date: at(29, 12), calendar: calendar))[.tiktok], 10)
    }

    func testIgnoresThresholdsThatCantBeRealYet() {
        let store = makeStore()
        // 00:20 means at most ~20 minutes of use today.
        XCTAssertFalse(store.record(platform: .youtube, minutes: 60, at: at(29, 0, 20)))
        // Monitoring started at 12:00, so at 12:03 two hours can't have been reached.
        store.monitoringSince = at(29, 12)
        XCTAssertFalse(store.record(platform: .youtube, minutes: 120, at: at(29, 12, 3)))
        XCTAssertTrue(store.record(platform: .youtube, minutes: 3, at: at(29, 12, 3)))
    }

    func testDaysAreSeparate() {
        let store = makeStore()
        store.record(platform: .facebook, minutes: 30, at: at(28, 22))
        store.record(platform: .facebook, minutes: 5, at: at(29, 9))
        XCTAssertEqual(store.minutes(on: DayKey(rawValue: "2026-10-28")!)[.facebook], 30)
        XCTAssertEqual(store.minutes(on: DayKey(rawValue: "2026-10-29")!)[.facebook], 5)
    }

    func testUntrackedOrUnmonitoredDaysHaveNoData() {
        let store = makeStore()
        store.monitoringSince = at(29, 8)
        store.record(platform: .tiktok, minutes: 20, at: at(29, 10))
        let today = DayKey(rawValue: "2026-10-29")!

        let days = store.platformDays(on: today, tracked: [.tiktok, .instagram], today: today)
        XCTAssertEqual(days.map(\.platform), [.tiktok, .instagram])
        XCTAssertEqual(days.first { $0.platform == .instagram }?.metric.appTimeMs, 0)
        // Before monitoring began nothing was measured: no rows, so the day reads "No data".
        XCTAssertTrue(store.platformDays(on: DayKey(rawValue: "2026-10-28")!, tracked: [.tiktok], today: today).isEmpty)
    }
}

final class SummaryTests: XCTestCase {
    private let day = DayKey(rawValue: "2026-10-29")!

    func testAddsCountsRangesAndAppTime() {
        let rows: [PlatformDay] = [
            PlatformDay(platform: .tiktok, date: day, metric: .estimated(count: 10, low: 5, high: 20, appTimeMs: 600_000)),
            PlatformDay(platform: .instagram, date: day, metric: .counted(count: 7, appTimeMs: 120_000)),
            PlatformDay(platform: .youtube, date: day, metric: .durationOnly(appTimeMs: 60_000)),
        ]
        let summary = rows.summarize(date: day)
        XCTAssertEqual(summary.count, 17)
        XCTAssertEqual(summary.countLow, 12)
        XCTAssertEqual(summary.countHigh, 27)
        XCTAssertEqual(summary.appTimeMs, 780_000)
        XCTAssertTrue(summary.isEstimate)
    }

    func testADayWithoutRowsHasNoData() {
        let summary = [PlatformDay]().summarize(date: day)
        XCTAssertNil(summary.count)
        XCTAssertNil(summary.appTimeMs)
        XCTAssertFalse(summary.hasData)
    }

    func testAverageLeavesOutDaysWithoutData() {
        let days = [
            DaySummary(date: day, count: 10, countLow: 10, countHigh: 10, appTimeMs: 1, isEstimate: false),
            DaySummary(date: day.adding(days: -1), count: nil, countLow: nil, countHigh: nil, appTimeMs: nil, isEstimate: false),
            DaySummary(date: day.adding(days: -2), count: 20, countLow: 20, countHigh: 20, appTimeMs: 1, isEstimate: false),
        ]
        XCTAssertEqual(days.averageCount, 15)
        XCTAssertEqual(days.daysWithData, 2)
    }
}

final class DailyTotalTests: XCTestCase {
    private func decode(_ json: String) throws -> DailyTotal {
        try JSONDecoder().decode(DailyTotal.self, from: Data(json.utf8))
    }

    func testEstimatedRowFromTheServer() throws {
        let total = try decode("""
        {"user_id":"u1","platform_id":"tiktok","activity_date":"2026-10-29","foreground_ms":600000.0,
         "estimated_reels":27,"estimated_reels_low":14,"estimated_reels_high":50,"verified_reels":null,
         "best_quality":"ESTIMATED","user_adjusted":false}
        """)
        XCTAssertEqual(total.toPlatformDay()?.metric, .estimated(count: 27, low: 14, high: 50, appTimeMs: 600_000))
    }

    func testCountedRowFromAnAndroidFriend() throws {
        let total = try decode("""
        {"user_id":"u1","platform_id":"youtube","activity_date":"2026-10-29","foreground_ms":"120000",
         "best_quality":"COUNTED","counted_reels":12}
        """)
        XCTAssertEqual(total.toPlatformDay()?.metric, .counted(count: 12, appTimeMs: 120_000))
    }

    func testUnknownPlatformsAreSkipped() throws {
        let total = try decode("""
        {"user_id":"u1","platform_id":"snapchat","activity_date":"2026-10-29","best_quality":"DURATION_ONLY","foreground_ms":5}
        """)
        XCTAssertNil(total.toPlatformDay())
    }

    func testUploadRow() {
        let today = DayKey(rawValue: "2026-10-29")!
        let day = PlatformDay.estimated(platform: .instagram, date: today.adding(days: -1), appTimeMs: 1_800_000)
        let row = DailySummaryUpload(day: day, userId: "u", deviceId: "d", timeZone: TimeZone(identifier: "Asia/Dhaka")!,
                                     today: today, revision: 42)
        XCTAssertEqual(row.quality, "ESTIMATED")
        XCTAssertEqual(row.estimatedReels, 55)
        XCTAssertEqual(row.estimationVersion, 1)
        XCTAssertTrue(row.isFinal)
        XCTAssertEqual(row.source, "usage_stats")
    }
}

final class FormatTests: XCTestCase {
    func testAppTime() {
        XCTAssertEqual(Format.appTime(ms: 0), "0 min")
        XCTAssertEqual(Format.appTime(ms: 42 * 60_000), "42 min")
        XCTAssertEqual(Format.appTime(ms: 65 * 60_000), "1 h 5 min")
        XCTAssertEqual(Format.appTime(ms: 180 * 60_000), "3 h")
    }

    func testReelsAndRanges() {
        let en = Locale(identifier: "en_US")
        XCTAssertEqual(Format.reels(1_234, isEstimate: true, locale: en), "≈ 1,234")
        XCTAssertEqual(Format.reels(7, isEstimate: false, locale: en), "7")
        XCTAssertEqual(Format.likelyRange(low: 55, high: 1_110, locale: en), "Likely 55–1,110")
        XCTAssertNil(Format.likelyRange(low: 0, high: 0, locale: en))
    }
}
