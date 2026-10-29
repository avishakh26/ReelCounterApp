import ReelCounterCore
import SwiftUI

/// The last 14 days: totals, daily average, busiest day, and each app's share.
struct InsightsView: View {
    @EnvironmentObject private var tracking: TrackingStore

    private let dayCount = 14

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.section) {
                Text("Insights").font(.rcLargeTitle)
                let _ = tracking.revision
                let today = DayKey.today()
                let keys = today.daysEnding(count: dayCount)
                let rows = tracking.days(keys)
                let days = keys.map { rows.summarize(date: $0) }

                if days.daysWithData == 0 {
                    Card {
                        EmptyStateView(systemImage: "chart.bar.xaxis", title: "No data yet",
                                       message: "Insights fill in as Screen Time measures your tracked apps.")
                    }
                } else {
                    summaryCards(days)
                    VStack(alignment: .leading, spacing: Spacing.md) {
                        SectionHeader(title: "Last 14 days")
                        Card { DaysChart(days: days, today: today) }
                    }
                    byApp(rows)
                    Text("Days without data are left out of averages. Estimates come from app time measured by Screen Time.")
                        .font(.rcCaption)
                        .foregroundStyle(Palette.textSecondary)
                }
            }
            .padding(.horizontal, Spacing.screen)
            .padding(.vertical, Spacing.lg)
        }
        .screenBackground()
        .refreshable { tracking.reload() }
        .toolbar(.hidden, for: .navigationBar)
    }

    private func summaryCards(_ days: [DaySummary]) -> some View {
        let isEstimate = days.contains(where: \.isEstimate)
        let total = days.compactMap(\.count).reduce(0, +)
        let appTime = days.compactMap(\.appTimeMs).reduce(0, +)
        let busiest = days.filter { $0.count != nil }.max { ($0.count ?? 0) < ($1.count ?? 0) }
        return LazyVGrid(columns: [GridItem(.flexible(), spacing: Spacing.md), GridItem(.flexible())], spacing: Spacing.md) {
            StatCard(title: "Daily average", value: days.averageCount.map { Format.reels($0, isEstimate: isEstimate) } ?? "—", unit: "reels")
            StatCard(title: "Total", value: Format.reels(total, isEstimate: isEstimate), unit: "reels in \(days.daysWithData) days")
            StatCard(title: "App time", value: Format.compactAppTime(ms: appTime), unit: "in total")
            StatCard(title: "Busiest day",
                     value: busiest?.count.map { Format.reels($0, isEstimate: isEstimate) } ?? "—",
                     unit: busiest.map { $0.date.startDate().formatted(.dateTime.weekday(.wide)) } ?? "")
        }
    }

    private func byApp(_ rows: [PlatformDay]) -> some View {
        let totals: [AppTotal] = PlatformID.ordered.compactMap { platform in
            let mine = rows.filter { $0.platform == platform }
            guard !mine.isEmpty else { return nil }
            return AppTotal(
                platform: platform,
                count: mine.compactMap(\.metric.reelCount).reduce(0, +),
                appTimeMs: mine.compactMap(\.metric.appTimeMs).reduce(0, +)
            )
        }
        let maxCount = max(totals.map(\.count).max() ?? 1, 1)
        return VStack(alignment: .leading, spacing: Spacing.md) {
            SectionHeader(title: "By app")
            Card {
                VStack(spacing: Spacing.lg) {
                    ForEach(totals) { total in
                        let platform = total.platform
                        let count = total.count
                        let ms = total.appTimeMs
                        HStack(spacing: Spacing.md) {
                            PlatformLogo(platform: platform, size: 36)
                            VStack(alignment: .leading, spacing: Spacing.xs) {
                                HStack {
                                    Text(platform.displayName).font(.rcHeadline)
                                    Spacer()
                                    Text(Format.reels(count, isEstimate: true)).font(.rcHeadline).monospacedDigit()
                                }
                                ProgressView(value: Double(count), total: Double(maxCount))
                                    .tint(Palette.accent)
                                Text("\(Format.appTime(ms: ms)) app time").font(.rcCaption).foregroundStyle(Palette.textSecondary)
                            }
                        }
                        .accessibilityElement(children: .combine)
                    }
                }
            }
        }
    }
}

private struct AppTotal: Identifiable {
    let platform: PlatformID
    let count: Int
    let appTimeMs: Int64

    var id: String { platform.rawValue }
}

struct StatCard: View {
    let title: String
    let value: String
    let unit: String

    var body: some View {
        Card {
            VStack(alignment: .leading, spacing: Spacing.xs) {
                Text(title).font(.rcLabel).foregroundStyle(Palette.textSecondary)
                Text(value).font(.rcFigure).foregroundStyle(Palette.textPrimary).lineLimit(1).minimumScaleFactor(0.6)
                Text(unit).font(.rcCaption).foregroundStyle(Palette.textSecondary).lineLimit(1)
            }
        }
        .accessibilityElement(children: .combine)
    }
}
