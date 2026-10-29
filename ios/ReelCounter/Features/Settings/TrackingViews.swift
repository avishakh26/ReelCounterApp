import FamilyControls
import ReelCounterCore
import SwiftUI

/// First-time setup: the privacy notice, Screen Time permission, then picking each platform's app.
struct TrackingSetupView: View {
    @EnvironmentObject private var tracking: TrackingStore
    @Environment(\.dismiss) private var dismiss
    @State private var isAuthorizing = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: Spacing.xl) {
                    if tracking.status == .unavailable {
                        TrackingUnavailableNotice()
                    } else {
                        stepIndicator
                        switch tracking.status {
                        case .needsDisclosure: disclosure
                        case .needsAuthorization: authorization
                        case .needsApps, .active, .unavailable: PickAppsSection(showsDone: true) { dismiss() }
                        }
                    }
                }
                .padding(Spacing.screen)
            }
            .screenBackground()
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Not now") { dismiss() }
                }
            }
        }
    }

    private var stepNumber: Int {
        switch tracking.status {
        case .needsDisclosure: return 1
        case .needsAuthorization: return 2
        case .needsApps, .active, .unavailable: return 3
        }
    }

    private var stepIndicator: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            Text("Step \(stepNumber) of 3").font(.rcLabel).foregroundStyle(Palette.accent)
            HStack(spacing: Spacing.xs) {
                ForEach(1...3, id: \.self) { index in
                    Capsule()
                        .fill(index <= stepNumber ? Palette.accent : Palette.chartIdle)
                        .frame(height: 4)
                }
            }
        }
    }

    private var disclosure: some View {
        VStack(alignment: .leading, spacing: Spacing.lg) {
            Text("How tracking works").font(.rcLargeTitle)
            Text("ReelCounter uses Apple's Screen Time to see how long you use the apps you choose. Here's exactly what that means:")
                .font(.rcBody)
                .foregroundStyle(Palette.textSecondary)
            Card {
                VStack(alignment: .leading, spacing: Spacing.lg) {
                    point("clock", "It learns how many minutes a day you spend in the apps you pick for TikTok, Instagram, YouTube and Facebook.")
                    point("eye.slash", "It never sees what you watch, read or type, and never records your screen. Apple doesn't let it.")
                    point("square.slash", "Apps you don't pick are never measured.")
                    point("arrow.triangle.2.circlepath", "Each day's totals per app (app time and reel estimates) are saved to your ReelCounter account.")
                    point("person.2", "Friends you accept can see your daily totals. You choose who sees what in Privacy.")
                    point("wand.and.stars", "Reel counts are estimates worked out from app time, always shown with ≈ and a likely range.")
                    point("pause.circle", "You can stop tracking in Settings, or turn off Screen Time access in iPhone Settings, at any time.")
                }
            }
            Button("Agree and continue") { tracking.acceptDisclosure() }
                .buttonStyle(PrimaryButtonStyle())
        }
    }

    private var authorization: some View {
        VStack(alignment: .leading, spacing: Spacing.lg) {
            Text("Allow Screen Time access").font(.rcLargeTitle)
            Text("iPhone asks you to confirm with Face ID or your passcode. ReelCounter only gets app time for the apps you pick next.")
                .font(.rcBody)
                .foregroundStyle(Palette.textSecondary)
            if let error = tracking.lastError { FormError(message: error.message) }
            Button("Allow access") {
                isAuthorizing = true
                Task {
                    await tracking.requestAuthorization()
                    isAuthorizing = false
                }
            }
            .buttonStyle(PrimaryButtonStyle(isLoading: isAuthorizing))
        }
    }

    private func point(_ systemImage: String, _ text: String) -> some View {
        HStack(alignment: .top, spacing: Spacing.md) {
            Image(systemName: systemImage)
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(Palette.accent)
                .frame(width: 24)
            Text(text).font(.rcCallout).foregroundStyle(Palette.textPrimary).fixedSize(horizontal: false, vertical: true)
        }
    }
}

/// One row per platform: tap to pick its app in Apple's picker.
struct PickAppsSection: View {
    @EnvironmentObject private var tracking: TrackingStore
    var showsDone = false
    var onDone: (() -> Void)?
    @State private var picking: PlatformID?
    @State private var draft = FamilyActivitySelection()

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.lg) {
            if showsDone {
                Text("Pick your apps").font(.rcLargeTitle)
            }
            Text("iPhone doesn't let apps find other apps by name, so pick each one yourself: tap a row, then choose that app in the list (search helps).")
                .font(.rcBody)
                .foregroundStyle(Palette.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
            if let error = tracking.lastError { FormError(message: error.message) }
            Card(padding: 0) {
                ForEach(Array(PlatformID.ordered.enumerated()), id: \.element) { index, platform in
                    if index > 0 { RowDivider(inset: Spacing.lg + 40 + Spacing.md) }
                    Button {
                        draft = tracking.selections[platform] ?? FamilyActivitySelection()
                        picking = platform
                    } label: {
                        HStack(spacing: Spacing.md) {
                            PlatformLogo(platform: platform)
                            VStack(alignment: .leading, spacing: Spacing.xxs) {
                                Text(platform.displayName).font(.rcHeadline).foregroundStyle(Palette.textPrimary)
                                Text(summary(for: platform)).font(.rcCaption).foregroundStyle(Palette.textSecondary)
                            }
                            Spacer()
                            if tracking.selections[platform] != nil {
                                Image(systemName: "checkmark.circle.fill").foregroundStyle(Palette.positive)
                            } else {
                                Text("Pick").font(.rcLabel).foregroundStyle(Palette.accent)
                            }
                        }
                        .padding(.horizontal, Spacing.lg)
                        .padding(.vertical, Spacing.md)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
            }
            if showsDone {
                Button("Done") { onDone?() }
                    .buttonStyle(PrimaryButtonStyle())
                    .disabled(tracking.trackedPlatforms.isEmpty)
            }
        }
        .familyActivityPicker(
            headerText: picking.map { "Choose the \($0.displayName) app" },
            isPresented: Binding(get: { picking != nil }, set: { if !$0 { finishPicking() } }),
            selection: $draft
        )
    }

    private func summary(for platform: PlatformID) -> String {
        guard let selection = tracking.selections[platform] else { return "Not tracked" }
        let count = selection.itemCount
        return count == 1 ? "1 app picked" : "\(count) items picked"
    }

    private func finishPicking() {
        if let platform = picking {
            tracking.setSelection(draft, for: platform)
        }
        picking = nil
    }
}

/// Settings → Tracking & apps.
struct TrackingSettingsView: View {
    @EnvironmentObject private var tracking: TrackingStore
    @State private var showSetup = false
    @State private var confirmStop = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.section) {
                switch tracking.status {
                case .active:
                    Banner(tone: .positive, systemImage: "checkmark.circle.fill", title: "Tracking is on",
                           message: "Screen Time measures the apps below. Figures update when you open ReelCounter.")
                    PickAppsSection()
                    Button("Stop tracking") { confirmStop = true }
                        .buttonStyle(SecondaryButtonStyle(isDestructive: true))
                case .needsApps:
                    PickAppsSection()
                case .unavailable:
                    TrackingUnavailableNotice()
                default:
                    Banner(tone: .estimate, systemImage: "pause.circle.fill", title: "Tracking is off",
                           message: "Set it up to measure your short-video apps.")
                    Button("Set up tracking") { showSetup = true }
                        .buttonStyle(PrimaryButtonStyle())
                }
            }
            .padding(Spacing.screen)
        }
        .screenBackground()
        .navigationTitle("Tracking & apps")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $showSetup) { TrackingSetupView() }
        .confirmationDialog("Stop tracking?", isPresented: $confirmStop, titleVisibility: .visible) {
            Button("Stop tracking", role: .destructive) { tracking.stopTracking() }
        } message: {
            Text("Screen Time stops measuring your apps. Figures already recorded stay.")
        }
    }
}

/// The free edition: why this iPhone's own app time isn't measured, and what still works.
struct TrackingUnavailableNotice: View {
    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.lg) {
            Image(systemName: "hourglass.badge.plus")
                .font(.system(size: 34, weight: .semibold))
                .foregroundStyle(Palette.accent)
            Text("Free version").font(.rcTitle).foregroundStyle(Palette.textPrimary)
            Text("Measuring app time on iPhone uses Apple's Screen Time, which Apple only allows in apps from paid developer accounts. This free version doesn't measure this iPhone.")
                .font(.rcBody)
                .foregroundStyle(Palette.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
            Card {
                VStack(alignment: .leading, spacing: Spacing.md) {
                    bullet("person.2", "Your friends and their daily figures, live")
                    bullet("person.badge.plus", "Friend requests and suggestions")
                    bullet("lock", "Your profile and privacy settings")
                    bullet("iphone.and.arrow.forward", "Your own figures from ReelCounter on Android")
                }
            }
        }
    }
}

private func bullet(_ systemImage: String, _ text: String) -> some View {
    HStack(alignment: .top, spacing: Spacing.md) {
        Image(systemName: systemImage)
            .font(.system(size: 15, weight: .semibold))
            .foregroundStyle(Palette.accent)
            .frame(width: 24)
        Text(text)
            .font(.rcCallout)
            .foregroundStyle(Palette.textPrimary)
            .fixedSize(horizontal: false, vertical: true)
    }
}

/// Settings → How we count: what each kind of figure means.
struct HowWeCountView: View {
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.lg) {
                Text("ReelCounter is honest about what it knows. Every figure is labeled with how it was measured.")
                    .font(.rcBody)
                    .foregroundStyle(Palette.textSecondary)
                category("wand.and.stars", .estimate, "Estimated",
                         "Worked out from app time: minutes in the app × the share of time spent on short videos ÷ the average seconds per video. Shown with ≈ and a likely range. This is how iPhone figures are made.")
                category("checkmark.seal.fill", .positive, "Counted",
                         "Counted one by one as each new video filled the screen. Only possible on Android, where friends may have turned it on.")
                category("clock", .info, "App time only",
                         "Minutes in the app, with no reel count.")
                category("minus.circle", .negative, "No data",
                         "Nothing was measured that day. It's never shown as 0 and is left out of averages.")
                Text("On iPhone, app time comes from Apple's Screen Time in steps (1, 2, 3… minutes, then every 5, 10, 20 or 30 minutes on long days), so it can be a few minutes under the real figure.")
                    .font(.rcCaption)
                    .foregroundStyle(Palette.textSecondary)
            }
            .padding(Spacing.screen)
        }
        .screenBackground()
        .navigationTitle("How we count")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func category(_ systemImage: String, _ tone: Tone, _ title: String, _ text: String) -> some View {
        Card {
            HStack(alignment: .top, spacing: Spacing.md) {
                Image(systemName: systemImage)
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(tone.foreground)
                    .frame(width: 36, height: 36)
                    .background(tone.background, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                VStack(alignment: .leading, spacing: Spacing.xs) {
                    Text(title).font(.rcHeadline)
                    Text(text).font(.rcCallout).foregroundStyle(Palette.textSecondary).fixedSize(horizontal: false, vertical: true)
                }
            }
        }
    }
}
