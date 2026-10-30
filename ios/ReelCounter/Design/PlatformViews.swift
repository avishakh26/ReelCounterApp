import Charts
import ReelCounterCore
import SwiftUI

/**
 The platform's logo: its brand glyph (Simple Icons, CC0) on the brand's tile, as on Android.
 Decorative: the platform's name is always shown next to it.
 */
struct PlatformLogo: View {
    let platform: PlatformID
    var size: CGFloat = 40

    var body: some View {
        ZStack {
            tile
            glyph
        }
        .frame(width: size, height: size)
        .clipShape(RoundedRectangle(cornerRadius: size * 0.25, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: size * 0.25, style: .continuous)
                .strokeBorder(Palette.hairline, lineWidth: 1)
        )
        .accessibilityHidden(true)
    }

    @ViewBuilder private var tile: some View {
        switch platform {
        case .tiktok:
            Color.black
        case .instagram:
            LinearGradient(
                colors: [Color(hex: 0xFFD600), Color(hex: 0xFF7A00), Color(hex: 0xFF0069), Color(hex: 0xD300C5), Color(hex: 0x7638FA)],
                startPoint: .bottomLeading,
                endPoint: .topTrailing
            )
        case .youtube, .facebook:
            Color.white
        }
    }

    @ViewBuilder private var glyph: some View {
        switch platform {
        case .tiktok:
            ZStack {
                logoImage.foregroundStyle(Color(hex: 0x25F4EE)).offset(x: -size * 0.02, y: -size * 0.02)
                logoImage.foregroundStyle(Color(hex: 0xFE2C55)).offset(x: size * 0.02, y: size * 0.02)
                logoImage.foregroundStyle(Color.white)
            }
            .frame(width: size * 0.56, height: size * 0.56)
        case .instagram:
            logoImage.foregroundStyle(Color.white).frame(width: size * 0.6, height: size * 0.6)
        case .youtube:
            logoImage.foregroundStyle(Color(hex: 0xFF0000)).frame(width: size * 0.68, height: size * 0.68)
        case .facebook:
            logoImage.foregroundStyle(Color(hex: 0x0866FF)).frame(width: size * 0.7, height: size * 0.7)
        }
    }

    private var logoImage: some View {
        Image("Logo-\(platform.rawValue)")
            .renderingMode(.template)
            .resizable()
            .scaledToFit()
    }
}

extension Color {
    init(hex: UInt32) {
        self.init(
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255
        )
    }
}

/// One platform's figures for a day: logo, name, app time and the reel count with its category.
struct PlatformRow: View {
    let day: PlatformDay

    var body: some View {
        HStack(spacing: Spacing.md) {
            PlatformLogo(platform: day.platform)
            VStack(alignment: .leading, spacing: Spacing.xxs) {
                Text(day.platform.displayName).font(.rcHeadline).foregroundStyle(Palette.textPrimary)
                if let supporting {
                    Text(supporting).font(.rcCaption).foregroundStyle(Palette.textSecondary)
                }
                if day.metric.isEstimate {
                    StatusPill(text: "Estimated", systemImage: "wand.and.stars", tone: .estimate)
                        .padding(.top, Spacing.xxs)
                }
            }
            Spacer(minLength: Spacing.sm)
            VStack(alignment: .trailing, spacing: 0) {
                Text(headline).font(.rcFigure).foregroundStyle(Palette.textPrimary)
                if let unit {
                    Text(unit).font(.rcCaption).foregroundStyle(Palette.textSecondary)
                }
            }
        }
        .padding(.vertical, Spacing.md)
        .accessibilityElement(children: .combine)
    }

    private var headline: String {
        switch day.metric {
        case .verified(let count, _), .counted(let count, _): return Format.count(count)
        case .estimated(let count, _, _, _): return Format.reels(count, isEstimate: true)
        case .durationOnly(let ms): return Format.compactAppTime(ms: ms)
        case .unavailable: return "—"
        }
    }

    private var unit: String? {
        switch day.metric {
        case .verified(let count, _), .counted(let count, _), .estimated(let count, _, _, _):
            return count == 1 ? "reel" : "reels"
        case .durationOnly: return "app time"
        case .unavailable: return nil
        }
    }

    private var supporting: String? {
        switch day.metric {
        case .verified(_, let ms):
            return ms.map { "\(Format.appTime(ms: $0)) app time" } ?? "Verified"
        case .counted(_, let ms):
            return "\(Format.appTime(ms: ms)) app time"
        case .estimated(_, let low, let high, let ms):
            let time = "\(Format.appTime(ms: ms)) app time"
            if let range = Format.likelyRange(low: low, high: high) { return "\(range) · \(time)" }
            return time
        case .durationOnly:
            return "No reel count for this app"
        case .unavailable:
            return "No data"
        }
    }
}

/// A list of platform rows inside one card, divided by hairlines.
struct PlatformList: View {
    let days: [PlatformDay]

    var body: some View {
        Card(padding: 0) {
            ForEach(Array(days.enumerated()), id: \.element.id) { index, day in
                if index > 0 { RowDivider(inset: Spacing.lg + 40 + Spacing.md) }
                PlatformRow(day: day).padding(.horizontal, Spacing.lg)
            }
        }
    }
}

/// Seven (or more) days of reel counts as bars; today is highlighted, days without data are flat.
struct DaysChart: View {
    let days: [DaySummary]
    let today: DayKey

    var body: some View {
        Chart(days) { day in
            BarMark(
                x: .value("Day", label(day.date)),
                y: .value("Reels", day.count ?? 0)
            )
            .cornerRadius(6)
            .foregroundStyle(day.date == today ? Palette.accent : (day.hasData ? Palette.accent.opacity(0.35) : Palette.chartIdle))
            .annotation(position: .top) {
                if day.date == today, let count = day.count {
                    Text(Format.count(count)).font(.rcLabel).foregroundStyle(Palette.accent)
                }
            }
        }
        .chartYAxis(.hidden)
        .chartXAxis {
            AxisMarks { _ in
                AxisValueLabel().font(.rcCaption)
            }
        }
        .frame(height: 180)
        .accessibilityLabel("Reels per day")
    }

    private func label(_ day: DayKey) -> String {
        let formatter = DateFormatter()
        formatter.setLocalizedDateFormatFromTemplate(days.count > 7 ? "d" : "EEE")
        return formatter.string(from: day.startDate())
    }
}

/// The big number for a day: reels (≈ when estimated), its likely range and app time.
struct HeroFigure: View {
    let summary: DaySummary
    var comparison: DaySummary?

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            if let count = summary.count {
                HStack(alignment: .firstTextBaseline, spacing: Spacing.sm) {
                    Text(Format.reels(count, isEstimate: summary.isEstimate))
                        .font(.rcHero)
                        .foregroundStyle(Palette.textPrimary)
                    Text(count == 1 ? "reel" : "reels")
                        .font(.rcTitle)
                        .foregroundStyle(Palette.textSecondary)
                }
                if summary.isEstimate, let low = summary.countLow, let high = summary.countHigh,
                   let range = Format.likelyRange(low: low, high: high) {
                    Text(range).font(.rcCallout).foregroundStyle(Palette.textSecondary)
                }
            } else {
                Text("No data yet")
                    .font(.rcLargeTitle)
                    .foregroundStyle(Palette.textPrimary)
            }
            HStack(spacing: Spacing.lg) {
                if let ms = summary.appTimeMs {
                    Label(Format.appTime(ms: ms), systemImage: "clock")
                }
                if let comparison, let count = comparison.count {
                    Label("Yesterday \(Format.reels(count, isEstimate: comparison.isEstimate))", systemImage: "calendar")
                }
            }
            .font(.rcCallout)
            .foregroundStyle(Palette.textSecondary)
        }
        .accessibilityElement(children: .combine)
    }
}
