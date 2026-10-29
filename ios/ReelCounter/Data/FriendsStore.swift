import Foundation
import ReelCounterCore
import Supabase

/**
 Friends, requests, suggestions and friends' daily totals, with live updates.

 Live updates come from the server's private broadcast channel `friendships:<my id>`: event
 `changed` when a friendship or request changes, `activity` when a friend's figures change.
 */
@MainActor
final class FriendsStore: ObservableObject {
    @Published private(set) var connections: [Connection] = []
    @Published private(set) var suggestions: [Suggestion] = []
    @Published private(set) var isLoading = false
    @Published private(set) var loadError: AppError?
    /// Friends' platform days since a week ago, by user id.
    @Published private(set) var activity: [String: [PlatformDay]] = [:]
    /// The user's own figures from their account over the last 14 days, as uploaded by their other
    /// devices (e.g. ReelCounter on Android). Shown by the free edition, which can't measure this iPhone.
    @Published private(set) var mine: [PlatformDay] = []
    /// People whose request button is busy.
    @Published private(set) var pending: Set<String> = []
    @Published var message: String?

    private var channel: RealtimeChannelV2?
    private var listenTasks: [Task<Void, Never>] = []
    private var listeningFor: String?

    var friends: [Connection] { connections.filter { $0.status == .friend } }
    var incoming: [Connection] { connections.filter { $0.status == .incoming } }
    var outgoing: [Connection] { connections.filter { $0.status == .outgoing } }

    private var client: SupabaseClient? { Backend.client }

    // MARK: Loading

    func refresh() async {
        guard let client else { return }
        isLoading = connections.isEmpty
        defer { isLoading = false }
        do {
            let rows: [Connection] = try await client.rpc("my_connections").execute().value
            connections = rows
            loadError = nil
            await refreshActivity()
        } catch {
            loadError = AppError.from(error)
        }
    }

    func refreshSuggestions() async {
        guard let client else { return }
        do {
            suggestions = try await client.rpc("suggest_friends", params: ["max_results": 20]).execute().value
        } catch {
            // Suggestions are optional; keep the last ones.
        }
    }

    /// Friends' daily totals from a week ago until today.
    func refreshActivity() async {
        guard let client else { return }
        let ids = friends.map(\.userId)
        guard !ids.isEmpty else {
            activity = [:]
            return
        }
        let from = DayKey.today().adding(days: -6).rawValue
        do {
            let rows: [DailyTotal] = try await client.from("daily_activity_totals")
                .select()
                .in("user_id", values: ids)
                .gte("activity_date", value: from)
                .execute()
                .value
            var byUser: [String: [PlatformDay]] = [:]
            for row in rows {
                if let day = row.toPlatformDay() { byUser[row.userId, default: []].append(day) }
            }
            activity = byUser
        } catch {
            // Keep the previous figures; the list itself still works.
        }
    }

    func refreshMine() async {
        guard let client, let userId = client.auth.currentSession?.user.id.uuidString.lowercased() else { return }
        let from = DayKey.today().adding(days: -13).rawValue
        do {
            let rows: [DailyTotal] = try await client.from("daily_activity_totals")
                .select()
                .eq("user_id", value: userId)
                .gte("activity_date", value: from)
                .execute()
                .value
            mine = rows.compactMap { $0.toPlatformDay() }
        } catch {
            // Keep the last figures.
        }
    }

    func todaySummary(for userId: String) -> DaySummary? {
        guard let days = activity[userId] else { return nil }
        let summary = days.summarize(date: .today())
        return summary.hasData ? summary : nil
    }

    // MARK: Search

    func search(_ text: String) async -> [SearchResult] {
        let query = text.trimmed.lowercased().trimmingCharacters(in: CharacterSet(charactersIn: "@"))
        guard query.count >= 2, let client else { return [] }
        struct Params: Encodable {
            let prefix: String
            let max_results: Int
        }
        do {
            return try await client.rpc("search_profiles", params: Params(prefix: query, max_results: 20)).execute().value
        } catch {
            return []
        }
    }

    // MARK: Actions

    func sendRequest(to person: PersonCard) async {
        await act(on: person.id, function: "send_friend_request", params: ["target": person.id]) { result in
            switch result {
            case "requested": return "Request sent to \(person.name)."
            case "accepted": return "You and \(person.name) are now friends."
            case "already_friends": return "You're already friends."
            case "already_requested": return "You already sent a request."
            case "not_accepting": return "\(person.name) isn't accepting friend requests."
            default: return "That person couldn't be found."
            }
        }
        suggestions.removeAll { $0.id == person.id }
    }

    func respond(to person: PersonCard, accept: Bool) async {
        struct Params: Encodable {
            let requester: String
            let accept: Bool
        }
        guard let client else { return }
        pending.insert(person.id)
        defer { pending.remove(person.id) }
        do {
            let _: String = try await client.rpc("respond_friend_request", params: Params(requester: person.id, accept: accept)).execute().value
            message = accept ? "You and \(person.name) are now friends." : "Request declined."
            await refresh()
        } catch {
            message = AppError.from(error).message
        }
    }

    func cancelRequest(to person: PersonCard) async {
        await act(on: person.id, function: "cancel_friend_request", params: ["target": person.id]) { _ in "Request cancelled." }
    }

    func removeFriend(_ person: PersonCard) async {
        await act(on: person.id, function: "remove_friend", params: ["target": person.id]) { _ in "\(person.name) was removed from your friends." }
    }

    func block(_ person: PersonCard) async {
        await act(on: person.id, function: "block_user", params: ["target": person.id]) { _ in "\(person.name) is blocked." }
        suggestions.removeAll { $0.id == person.id }
    }

    func unblock(_ person: PersonCard) async {
        await act(on: person.id, function: "unblock_user", params: ["target": person.id]) { _ in "\(person.name) is unblocked." }
    }

    func blockedUsers() async -> [BlockedUser] {
        guard let client else { return [] }
        return (try? await client.rpc("my_blocked_users").execute().value) ?? []
    }

    private func act(on id: String, function: String, params: [String: String], message: (String) -> String) async {
        guard let client else { return }
        pending.insert(id)
        defer { pending.remove(id) }
        do {
            let result: String = try await client.rpc(function, params: params).execute().value
            self.message = message(result)
            await refresh()
        } catch {
            self.message = AppError.from(error).message
        }
    }

    // MARK: Live updates

    func startListening(userId: String) async {
        guard let client, listeningFor != userId else { return }
        await stopListening()
        listeningFor = userId
        let channel = client.channel("friendships:\(userId)") { config in
            config.isPrivate = true
        }
        self.channel = channel
        let changed = channel.broadcastStream(event: "changed")
        let activityChanged = channel.broadcastStream(event: "activity")
        listenTasks = [
            Task { [weak self] in
                for await _ in changed { await self?.refresh() }
            },
            Task { [weak self] in
                for await _ in activityChanged { await self?.refreshActivity() }
            },
        ]
        await channel.subscribe()
    }

    func stopListening() async {
        listenTasks.forEach { $0.cancel() }
        listenTasks = []
        if let channel, let client {
            await client.removeChannel(channel)
        }
        channel = nil
        listeningFor = nil
    }

    #if DEBUG
    /// Screenshot tests only: shows the given people and figures without a backend.
    func seedForScreenshots(connections: [Connection], suggestions: [Suggestion], activity: [String: [PlatformDay]]) {
        self.connections = connections
        self.suggestions = suggestions
        self.activity = activity
    }
    #endif

    func reset() async {
        await stopListening()
        connections = []
        suggestions = []
        activity = [:]
        mine = []
    }
}
