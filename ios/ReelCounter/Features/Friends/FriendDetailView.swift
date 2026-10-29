import ReelCounterCore
import SwiftUI

/// A friend's dashboard: today, their last seven days and today by app. Updates live.
struct FriendDetailView: View {
    @EnvironmentObject private var friends: FriendsStore
    @Environment(\.dismiss) private var dismiss
    let person: PersonCard
    @State private var confirmRemove = false
    @State private var confirmBlock = false

    private var today: DayKey { .today() }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.section) {
                HStack(spacing: Spacing.lg) {
                    AvatarView(url: person.avatarURL, name: person.name, size: 64)
                    VStack(alignment: .leading, spacing: Spacing.xxs) {
                        Text(person.name).font(.rcTitle)
                        if let handle = person.handle, handle != person.name {
                            Text(handle).font(.rcCallout).foregroundStyle(Palette.textSecondary)
                        }
                    }
                }

                let rows = friends.activity[person.id] ?? []
                let weekKeys = today.daysEnding(count: 7)
                let week = weekKeys.map { rows.summarize(date: $0) }

                Card(padding: Spacing.xl) {
                    VStack(alignment: .leading, spacing: Spacing.md) {
                        Text("Today").font(.rcLabel).foregroundStyle(Palette.textSecondary)
                        HeroFigure(summary: rows.summarize(date: today), comparison: week.dropLast().last)
                    }
                }

                VStack(alignment: .leading, spacing: Spacing.md) {
                    SectionHeader(title: "Last 7 days")
                    Card { DaysChart(days: week, today: today) }
                }

                VStack(alignment: .leading, spacing: Spacing.md) {
                    SectionHeader(title: "By app today")
                    let todayRows = rows.filter { $0.date == today }.sorted { $0.platform.sortIndex < $1.platform.sortIndex }
                    if todayRows.isEmpty {
                        Card { EmptyStateView(systemImage: "eye.slash", title: "Nothing shared today",
                                              message: "\(person.name) hasn't shared any figures today, or keeps them private.") }
                    } else {
                        PlatformList(days: todayRows)
                    }
                }

                VStack(spacing: Spacing.md) {
                    Button("Remove friend") { confirmRemove = true }
                        .buttonStyle(SecondaryButtonStyle(isDestructive: true))
                    Button("Block") { confirmBlock = true }
                        .buttonStyle(SecondaryButtonStyle(isDestructive: true))
                }
            }
            .padding(.horizontal, Spacing.screen)
            .padding(.vertical, Spacing.lg)
        }
        .screenBackground()
        .navigationTitle(person.name)
        .navigationBarTitleDisplayMode(.inline)
        .refreshable { await friends.refreshActivity() }
        .task { await friends.refreshActivity() }
        .confirmationDialog("Remove \(person.name) from your friends?", isPresented: $confirmRemove, titleVisibility: .visible) {
            Button("Remove", role: .destructive) {
                Task {
                    await friends.removeFriend(person)
                    dismiss()
                }
            }
        }
        .confirmationDialog("Block \(person.name)?", isPresented: $confirmBlock, titleVisibility: .visible) {
            Button("Block", role: .destructive) {
                Task {
                    await friends.block(person)
                    dismiss()
                }
            }
        } message: {
            Text("They won't be able to find you, send requests or see your figures. You can unblock them in Settings.")
        }
    }
}

extension PlatformID {
    var sortIndex: Int { PlatformID.ordered.firstIndex(of: self) ?? 0 }
}
