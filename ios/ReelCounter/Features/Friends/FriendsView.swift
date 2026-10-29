import ReelCounterCore
import SwiftUI

/// Search by username (suggests as you type), then Friends | Requests | Suggestions.
struct FriendsView: View {
    @EnvironmentObject private var friends: FriendsStore

    enum Section: String, CaseIterable, Identifiable {
        case friends = "Friends"
        case requests = "Requests"
        case suggestions = "Suggestions"
        var id: String { rawValue }
    }

    @State private var section = Section.friends
    @State private var query = ""
    @State private var results: [SearchResult] = []
    @State private var isSearching = false

    private var isSearchActive: Bool { query.trimmed.count >= 2 }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.xl) {
                Text("Friends").font(.rcLargeTitle)
                searchField
                if isSearchActive {
                    searchResults
                } else {
                    Picker("Section", selection: $section) {
                        ForEach(Section.allCases) { item in
                            Text(item == .requests && !friends.incoming.isEmpty ? "Requests (\(friends.incoming.count))" : item.rawValue)
                                .tag(item)
                        }
                    }
                    .pickerStyle(.segmented)
                    switch section {
                    case .friends: friendsList
                    case .requests: requestsList
                    case .suggestions: suggestionsList
                    }
                }
            }
            .padding(.horizontal, Spacing.screen)
            .padding(.vertical, Spacing.lg)
        }
        .scrollDismissesKeyboard(.interactively)
        .screenBackground()
        .refreshable {
            await friends.refresh()
            await friends.refreshSuggestions()
        }
        .toolbar(.hidden, for: .navigationBar)
        .navigationDestination(for: PersonCard.self) { person in
            FriendDetailView(person: person)
        }
        .task(id: query) { await runSearch() }
        .task(id: section) {
            if section == .suggestions && friends.suggestions.isEmpty { await friends.refreshSuggestions() }
        }
        .alert(friends.message ?? "", isPresented: Binding(
            get: { friends.message != nil },
            set: { if !$0 { friends.message = nil } }
        )) {
            Button("OK", role: .cancel) {}
        }
    }

    private var searchField: some View {
        HStack(spacing: Spacing.sm) {
            Image(systemName: "magnifyingglass").foregroundStyle(Palette.textSecondary)
            TextField("Search by username", text: $query)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .submitLabel(.search)
            if !query.isEmpty {
                Button { query = "" } label: {
                    Image(systemName: "xmark.circle.fill").foregroundStyle(Palette.textSecondary)
                }
                .accessibilityLabel("Clear search")
            }
        }
        .fieldStyle()
    }

    private func runSearch() async {
        guard isSearchActive else {
            results = []
            return
        }
        isSearching = true
        // Suggest as the user types, once typing pauses.
        try? await Task.sleep(nanoseconds: 300_000_000)
        guard !Task.isCancelled else { return }
        results = await friends.search(query)
        isSearching = false
    }

    // MARK: Lists

    @ViewBuilder private var searchResults: some View {
        if isSearching && results.isEmpty {
            LoadingView()
        } else if results.isEmpty {
            Card { EmptyStateView(systemImage: "person.crop.circle.badge.questionmark", title: "No one found",
                                  message: "Check the spelling. People who turned off discovery don't appear.") }
        } else {
            PeopleCard(items: results.map(\.person)) { person in
                switch results.first(where: { $0.id == person.id })?.relationship ?? .none {
                case .friend: StatusPill(text: "Friends", tone: .positive)
                case .outgoing: requestedButton(person)
                case .incoming: acceptButtons(person)
                case .none: addButton(person)
                }
            }
        }
    }

    @ViewBuilder private var friendsList: some View {
        if friends.isLoading {
            LoadingView()
        } else if let error = friends.loadError, friends.connections.isEmpty {
            Card { EmptyStateView(systemImage: "wifi.exclamationmark", title: "Couldn't load friends", message: error.message,
                                  actionTitle: "Try again") { Task { await friends.refresh() } } }
        } else if friends.friends.isEmpty {
            Card { EmptyStateView(systemImage: "person.2", title: "No friends yet",
                                  message: "Search for someone's username, or look at Suggestions.",
                                  actionTitle: "See suggestions") { section = .suggestions } }
        } else {
            PeopleCard(items: friends.friends.map(\.person), isNavigable: true) { person in
                TodayFigure(summary: friends.todaySummary(for: person.id))
            }
        }
    }

    @ViewBuilder private var requestsList: some View {
        if friends.incoming.isEmpty && friends.outgoing.isEmpty {
            Card { EmptyStateView(systemImage: "tray", title: "No requests", message: "Friend requests you send or receive show up here.") }
        }
        if !friends.incoming.isEmpty {
            VStack(alignment: .leading, spacing: Spacing.md) {
                Text("Received").font(.rcHeadline)
                PeopleCard(items: friends.incoming.map(\.person)) { acceptButtons($0) }
            }
        }
        if !friends.outgoing.isEmpty {
            VStack(alignment: .leading, spacing: Spacing.md) {
                Text("Sent").font(.rcHeadline)
                PeopleCard(items: friends.outgoing.map(\.person)) { requestedButton($0) }
            }
        }
    }

    @ViewBuilder private var suggestionsList: some View {
        if friends.suggestions.isEmpty {
            Card { EmptyStateView(systemImage: "sparkles", title: "No suggestions right now",
                                  message: "As you and your friends add people, you'll see people you may know here.") }
        } else {
            PeopleCard(items: friends.suggestions.map(\.person), subtitle: { person in
                let mutual = friends.suggestions.first { $0.id == person.id }?.mutualFriends ?? 0
                return mutual > 0 ? "\(mutual) mutual friend\(mutual == 1 ? "" : "s")" : person.handle
            }) { addButton($0) }
        }
    }

    // MARK: Buttons

    private func addButton(_ person: PersonCard) -> some View {
        Button("Add") { Task { await friends.sendRequest(to: person) } }
            .buttonStyle(PillButtonStyle())
            .disabled(friends.pending.contains(person.id))
            .accessibilityLabel("Add \(person.name)")
    }

    private func requestedButton(_ person: PersonCard) -> some View {
        Button("Cancel") { Task { await friends.cancelRequest(to: person) } }
            .buttonStyle(PillButtonStyle(filled: false))
            .disabled(friends.pending.contains(person.id))
            .accessibilityLabel("Cancel request to \(person.name)")
    }

    private func acceptButtons(_ person: PersonCard) -> some View {
        HStack(spacing: Spacing.sm) {
            Button("Decline") { Task { await friends.respond(to: person, accept: false) } }
                .buttonStyle(PillButtonStyle(filled: false))
            Button("Accept") { Task { await friends.respond(to: person, accept: true) } }
                .buttonStyle(PillButtonStyle())
        }
        .disabled(friends.pending.contains(person.id))
    }
}

/// People in one card: avatar, name, handle, and a trailing control.
struct PeopleCard<Trailing: View>: View {
    let items: [PersonCard]
    var isNavigable = false
    var subtitle: ((PersonCard) -> String?)?
    @ViewBuilder var trailing: (PersonCard) -> Trailing

    var body: some View {
        Card(padding: 0) {
            ForEach(Array(items.enumerated()), id: \.element.id) { index, person in
                if index > 0 { RowDivider(inset: Spacing.lg + 44 + Spacing.md) }
                if isNavigable {
                    NavigationLink(value: person) { row(person, chevron: true) }
                        .buttonStyle(.plain)
                } else {
                    row(person, chevron: false)
                }
            }
        }
    }

    private func row(_ person: PersonCard, chevron: Bool) -> some View {
        HStack(spacing: Spacing.md) {
            AvatarView(url: person.avatarURL, name: person.name)
            VStack(alignment: .leading, spacing: Spacing.xxs) {
                Text(person.name).font(.rcHeadline).foregroundStyle(Palette.textPrimary).lineLimit(1)
                if let text = subtitle?(person) ?? person.handle, text != person.name {
                    Text(text).font(.rcCaption).foregroundStyle(Palette.textSecondary).lineLimit(1)
                }
            }
            Spacer(minLength: Spacing.sm)
            trailing(person)
            if chevron {
                Image(systemName: "chevron.right").font(.system(size: 13, weight: .semibold)).foregroundStyle(Palette.textSecondary)
            }
        }
        .padding(.horizontal, Spacing.lg)
        .padding(.vertical, Spacing.md)
        .contentShape(Rectangle())
    }
}

/// A friend's reels today, or "No data" when they've shared nothing today.
struct TodayFigure: View {
    let summary: DaySummary?

    var body: some View {
        VStack(alignment: .trailing, spacing: 0) {
            if let summary, let count = summary.count {
                Text(Format.reels(count, isEstimate: summary.isEstimate)).font(.rcHeadline).monospacedDigit()
                Text("today").font(.rcCaption).foregroundStyle(Palette.textSecondary)
            } else if let ms = summary?.appTimeMs {
                Text(Format.compactAppTime(ms: ms)).font(.rcHeadline).monospacedDigit()
                Text("today").font(.rcCaption).foregroundStyle(Palette.textSecondary)
            } else {
                Text("No data").font(.rcCaption).foregroundStyle(Palette.textSecondary)
            }
        }
    }
}
