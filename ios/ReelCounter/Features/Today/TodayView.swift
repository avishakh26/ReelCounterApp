import ReelCounterCore
import SwiftUI

/// Today's reels and app time, the last seven days, and today by app.
struct TodayView: View {
    @EnvironmentObject private var session: SessionStore
    @EnvironmentObject private var tracking: TrackingStore
    @EnvironmentObject private var friends: FriendsStore
    @Environment(\.activitySync) private var sync
    var onOpenSettings: () -> Void
    @State private var showSetup = false

    private var today: DayKey { .today() }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.section) {
                header
                if tracking.status == .active || !tracking.usage.recordedDays().isEmpty {
                    content
                } else if tracking.status == .unavailable {
                    if friends.mine.isEmpty {
                        Card(padding: Spacing.xl) { TrackingUnavailableNotice() }
                    } else {
                        TodayDashboard(rows: friends.mine, today: today, footnote: TodayDashboard.accountFootnote)
                    }
                } else {
                    setupCard
                }
            }
            .padding(.horizontal, Spacing.screen)
            .padding(.vertical, Spacing.lg)
        }
        .screenBackground()
        .refreshable {
            tracking.reload()
            await sync?.syncNow()
            if tracking.status == .unavailable { await friends.refreshMine() }
        }
        .task {
            if tracking.status == .unavailable { await friends.refreshMine() }
        }
        .toolbar(.hidden, for: .navigationBar)
        .sheet(isPresented: $showSetup) {
            TrackingSetupView()
        }
    }

    private var header: some View {
        HStack(alignment: .center) {
            VStack(alignment: .leading, spacing: Spacing.xxs) {
                Text(today.startDate().formatted(.dateTime.weekday(.wide).day().month(.wide)))
                    .font(.rcLabel)
                    .foregroundStyle(Palette.textSecondary)
                Text(greeting).font(.rcLargeTitle).foregroundStyle(Palette.textPrimary)
            }
            Spacer()
            Button(action: onOpenSettings) {
                AvatarView(url: Backend.avatarURL(path: session.profile?.avatarPath), name: session.profile?.shownName ?? "?", size: 44)
            }
            .accessibilityLabel("Your profile")
        }
    }

    private var greeting: String {
        let hour = Calendar.current.component(.hour, from: Date())
        let part = hour < 12 ? "Good morning" : (hour < 18 ? "Good afternoon" : "Good evening")
        if let name = session.profile?.greetingName { return "\(part), \(name)" }
        return part
    }

    @ViewBuilder private var content: some View {
        let _ = tracking.revision
        TodayDashboard(
            rows: tracking.days(today.daysEnding(count: 7)),
            today: today,
            isTrackingOff: tracking.status != .active,
            onTurnOn: { showSetup = true }
        )
    }

    private var setupCard: some View {
        Card(padding: Spacing.xl) {
            VStack(alignment: .leading, spacing: Spacing.lg) {
                Image(systemName: "hourglass.circle.fill")
                    .font(.system(size: 40))
                    .foregroundStyle(Palette.accent)
                Text("Set up tracking").font(.rcTitle)
                Text("Allow Screen Time access and pick your TikTok, Instagram, YouTube and Facebook apps. ReelCounter then estimates how many reels you watch each day.")
                    .font(.rcBody)
                    .foregroundStyle(Palette.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
                Button("Set up tracking") { showSetup = true }
                    .buttonStyle(PrimaryButtonStyle())
            }
        }
    }
}

/// Today's hero, the week chart and today by app, for the given figures (no data loading here).
struct TodayDashboard: View {
    let rows: [PlatformDay]
    let today: DayKey
    var isTrackingOff = false
    var onTurnOn: () -> Void = {}
    var footnote = "Reels are estimated from app time measured by Screen Time, so they're shown with ≈ and a likely range. iPhone can't count videos one by one."

    /// For figures read from the account (the free edition): they come from the user's other devices.
    static let accountFootnote = "Figures from ReelCounter on your other devices, such as Android. This free iPhone version doesn't measure this iPhone. Estimates are shown with ≈ and a likely range."

    var body: some View {
        let week = today.daysEnding(count: 7).map { rows.summarize(date: $0) }
        VStack(alignment: .leading, spacing: Spacing.section) {
            Card(padding: Spacing.xl) {
                VStack(alignment: .leading, spacing: Spacing.md) {
                    Text("Today").font(.rcLabel).foregroundStyle(Palette.textSecondary)
                    HeroFigure(summary: rows.summarize(date: today), comparison: week.dropLast().last)
                }
            }

            if isTrackingOff {
                Banner(tone: .estimate, systemImage: "pause.circle.fill", title: "Tracking is off",
                       message: "Turn it back on to keep measuring app time.")
                    .onTapGesture(perform: onTurnOn)
            }

            VStack(alignment: .leading, spacing: Spacing.md) {
                SectionHeader(title: "Last 7 days")
                Card {
                    VStack(alignment: .leading, spacing: Spacing.md) {
                        DaysChart(days: week, today: today)
                        weekFootnote(week)
                    }
                }
            }

            VStack(alignment: .leading, spacing: Spacing.md) {
                SectionHeader(title: "By app")
                let todayRows = rows.filter { $0.date == today }.sorted { $0.platform.sortIndex < $1.platform.sortIndex }
                if todayRows.isEmpty {
                    Card { EmptyStateView(systemImage: "hourglass", title: "Nothing yet today", message: "Figures appear after a minute or so in a tracked app.") }
                } else {
                    PlatformList(days: todayRows)
                }
            }

            Text(footnote)
                .font(.rcCaption)
                .foregroundStyle(Palette.textSecondary)
        }
    }

    private func weekFootnote(_ week: [DaySummary]) -> some View {
        var parts: [String] = []
        if let average = week.averageCount {
            parts.append("Daily average \(Format.reels(average, isEstimate: week.contains(where: \.isEstimate))) reels")
        }
        parts.append("\(week.daysWithData) of 7 days with data")
        return Text(parts.joined(separator: " · "))
            .font(.rcCaption)
            .foregroundStyle(Palette.textSecondary)
    }
}
