import ReelCounterCore
import SwiftUI
import UIKit
import XCTest
@testable import ReelCounter

/**
 Renders the main screens, light and dark, to PNG files so the design can be reviewed without a
 Mac. CI publishes them to the `ios-screenshots` branch. The figures are test fixtures only.

 Output folder: the SNAPSHOT_DIR environment variable (CI passes TEST_RUNNER_SNAPSHOT_DIR), else tmp.
 */
@MainActor
final class ScreenshotTests: XCTestCase {
    private let width: CGFloat = 393

    private var outputDir: URL {
        let path = ProcessInfo.processInfo.environment["SNAPSHOT_DIR"] ?? NSTemporaryDirectory()
        return URL(fileURLWithPath: path, isDirectory: true)
    }

    // MARK: Fixtures

    private let today = DayKey.today()

    private func myRows() -> [PlatformDay] {
        let minutes: [[PlatformID: Int]] = [
            [.tiktok: 35, .instagram: 20], [.tiktok: 50, .youtube: 30], [:], [.tiktok: 15, .instagram: 45, .youtube: 10],
            [.tiktok: 70, .facebook: 15], [.tiktok: 40, .instagram: 25, .youtube: 20], [.tiktok: 45, .instagram: 30, .youtube: 60, .facebook: 10],
        ]
        return zip(today.daysEnding(count: 7), minutes).flatMap { day, usage in
            usage.map { PlatformDay.estimated(platform: $0.key, date: day, appTimeMs: Int64($0.value) * 60_000) }
        }
    }

    private func insightRows() -> [PlatformDay] {
        today.daysEnding(count: 14).enumerated().flatMap { index, day -> [PlatformDay] in
            guard index != 4 else { return [] }
            return [
                .estimated(platform: .tiktok, date: day, appTimeMs: Int64(20 + (index * 13) % 50) * 60_000),
                .estimated(platform: .instagram, date: day, appTimeMs: Int64(10 + (index * 7) % 30) * 60_000),
                .estimated(platform: .youtube, date: day, appTimeMs: Int64((index * 11) % 40) * 60_000),
            ]
        }
    }

    private func seededFriends() -> FriendsStore {
        let store = FriendsStore()
        let maya = Connection(userId: "maya", username: "maya.reels", displayName: "Maya Rahman", avatarPath: nil, status: .friend)
        let leo = Connection(userId: "leo", username: "leo_watch", displayName: "Leo", avatarPath: nil, status: .friend)
        let sam = Connection(userId: "sam", username: "sam.k", displayName: nil, avatarPath: nil, status: .friend)
        let nadia = Connection(userId: "nadia", username: "nadia", displayName: "Nadia Islam", avatarPath: nil, status: .incoming)
        let mayaDays: [PlatformDay] = today.daysEnding(count: 7).enumerated().flatMap { index, day in
            [
                PlatformDay(platform: .tiktok, date: day, metric: .counted(count: 40 + index * 9, appTimeMs: Int64(30 + index * 5) * 60_000)),
                PlatformDay.estimated(platform: .instagram, date: day, appTimeMs: Int64(15 + index * 3) * 60_000),
            ]
        }
        store.seedForScreenshots(
            connections: [maya, leo, sam, nadia],
            suggestions: [
                Suggestion(id: "ari", username: "ari.v", displayName: "Ari Verma", avatarPath: nil, mutualFriends: 3),
                Suggestion(id: "tom", username: "tomtom", displayName: nil, avatarPath: nil, mutualFriends: 0),
            ],
            activity: [
                "maya": mayaDays,
                "leo": [PlatformDay.estimated(platform: .youtube, date: today, appTimeMs: 95 * 60_000)],
            ]
        )
        return store
    }

    private func seededSession() -> SessionStore {
        let session = SessionStore()
        session.seedForScreenshots(
            profile: Profile(id: "me", username: "qa_tester", displayName: "Avi Chakra", bio: nil, avatarPath: nil),
            email: "avi@example.com"
        )
        return session
    }

    // MARK: Screens

    func testSignIn() throws {
        try snapshot("01-sign-in", height: 852) {
            AuthFlowView().environmentObject(SessionStore())
        }
    }

    func testSignUp() throws {
        try snapshot("02-sign-up", height: 852) {
            NavigationStack { SignUpView(path: .constant([])) }.environmentObject(SessionStore())
        }
    }

    func testToday() throws {
        let rows = myRows()
        try snapshot("03-today", height: 1_520) {
            screen(title: "Good evening, Avi") {
                TodayDashboard(rows: rows, today: today)
            }
        }
    }

    func testInsights() throws {
        let rows = insightRows()
        let keys = today.daysEnding(count: 14)
        try snapshot("04-insights", height: 1_250) {
            screen(title: "Insights") {
                InsightsDashboard(rows: rows, keys: keys, today: today)
            }
        }
    }

    func testFriends() throws {
        let friends = seededFriends()
        try snapshot("05-friends", height: 852) {
            NavigationStack { FriendsView() }
                .environmentObject(friends)
        }
    }

    func testFriendDetail() throws {
        let friends = seededFriends()
        let maya = PersonCard(id: "maya", username: "maya.reels", displayName: "Maya Rahman", avatarPath: nil)
        try snapshot("06-friend-detail", height: 1_500) {
            NavigationStack { FriendDetailView(person: maya) }
                .environmentObject(friends)
        }
    }

    func testSettings() throws {
        let session = seededSession()
        try snapshot("07-settings", height: 1_000) {
            NavigationStack { SettingsView() }
                .environmentObject(session)
                .environmentObject(TrackingStore())
        }
    }

    func testTrackingSetup() throws {
        try snapshot("08-tracking-setup", height: 1_150) {
            TrackingSetupView().environmentObject(TrackingStore())
        }
    }

    func testPickApps() throws {
        try snapshot("09-pick-apps", height: 700) {
            ScrollView { PickAppsSection(showsDone: true).padding(Spacing.screen) }
                .screenBackground()
                .environmentObject(TrackingStore())
        }
    }

    func testHowWeCount() throws {
        try snapshot("10-how-we-count", height: 1_000) {
            NavigationStack { HowWeCountView() }
        }
    }

    // MARK: Rendering

    /// A tab screen's layout: large title and content, scrolling.
    private func screen<Content: View>(title: String, @ViewBuilder content: () -> Content) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.section) {
                Text(title).font(.rcLargeTitle).foregroundStyle(Palette.textPrimary)
                content()
            }
            .padding(.horizontal, Spacing.screen)
            .padding(.vertical, Spacing.lg)
        }
        .screenBackground()
    }

    /// Hosts the view in a real window (so UIKit-backed controls render) and saves light and dark PNGs.
    private func snapshot<V: View>(_ name: String, height: CGFloat, @ViewBuilder view: () -> V) throws {
        try FileManager.default.createDirectory(at: outputDir, withIntermediateDirectories: true)
        for style in [UIUserInterfaceStyle.light, .dark] {
            let window = UIWindow(frame: CGRect(x: 0, y: 0, width: width, height: height))
            window.overrideUserInterfaceStyle = style
            window.rootViewController = UIHostingController(rootView: view().tint(Palette.accent))
            window.makeKeyAndVisible()
            // Let SwiftUI lay out, run .task blocks and load images.
            RunLoop.main.run(until: Date(timeIntervalSinceNow: 1.5))
            let format = UIGraphicsImageRendererFormat()
            format.scale = 2
            let image = UIGraphicsImageRenderer(bounds: window.bounds, format: format).image { _ in
                window.drawHierarchy(in: window.bounds, afterScreenUpdates: true)
            }
            let suffix = style == .dark ? "dark" : "light"
            let url = outputDir.appendingPathComponent("\(name)-\(suffix).png")
            try XCTUnwrap(image.pngData()).write(to: url)
            window.isHidden = true
        }
    }
}
