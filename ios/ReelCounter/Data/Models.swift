import Foundation

/// The signed-in user's own profile row (`public.profiles`).
struct Profile: Codable, Equatable {
    let id: String
    var username: String?
    var displayName: String?
    var bio: String?
    var avatarPath: String?

    enum CodingKeys: String, CodingKey {
        case id, username, bio
        case displayName = "display_name"
        case avatarPath = "avatar_path"
    }

    /// The name to greet the user with: their display name's first word, else their username.
    var greetingName: String? {
        if let first = displayName?.trimmingCharacters(in: .whitespaces).split(separator: " ").first {
            return String(first)
        }
        return username
    }

    var shownName: String { displayName ?? username ?? "You" }
}

/// What `profiles` accepts in an update.
struct ProfileUpdate: Encodable {
    let username: String?
    let displayName: String?
    let bio: String?
    let avatarPath: String?

    enum CodingKeys: String, CodingKey {
        case username, bio
        case displayName = "display_name"
        case avatarPath = "avatar_path"
    }

    // Send nulls explicitly so a cleared field is cleared on the server.
    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(username, forKey: .username)
        try c.encode(displayName, forKey: .displayName)
        try c.encode(bio, forKey: .bio)
        try c.encode(avatarPath, forKey: .avatarPath)
    }
}

/// Another user's public card, as search, suggestions and connections return it.
struct PersonCard: Identifiable, Equatable, Hashable {
    let id: String
    let username: String?
    let displayName: String?
    let avatarPath: String?

    var name: String { displayName ?? username.map { "@\($0)" } ?? "ReelCounter user" }
    var handle: String? { username.map { "@\($0)" } }
    var avatarURL: URL? { Backend.avatarURL(path: avatarPath) }
}

enum Relationship: String, Decodable {
    case friend, incoming, outgoing, none
}

/// A row of `search_profiles`.
struct SearchResult: Decodable, Identifiable, Equatable {
    let id: String
    let username: String?
    let displayName: String?
    let avatarPath: String?
    let relationship: Relationship

    enum CodingKeys: String, CodingKey {
        case id, username, relationship
        case displayName = "display_name"
        case avatarPath = "avatar_path"
    }

    var person: PersonCard { PersonCard(id: id, username: username, displayName: displayName, avatarPath: avatarPath) }
}

/// A row of `suggest_friends`.
struct Suggestion: Decodable, Identifiable, Equatable {
    let id: String
    let username: String?
    let displayName: String?
    let avatarPath: String?
    let mutualFriends: Int

    init(id: String, username: String?, displayName: String?, avatarPath: String?, mutualFriends: Int) {
        self.id = id
        self.username = username
        self.displayName = displayName
        self.avatarPath = avatarPath
        self.mutualFriends = mutualFriends
    }

    enum CodingKeys: String, CodingKey {
        case id, username
        case displayName = "display_name"
        case avatarPath = "avatar_path"
        case mutualFriends = "mutual_friends"
    }

    var person: PersonCard { PersonCard(id: id, username: username, displayName: displayName, avatarPath: avatarPath) }
}

/// A row of `my_connections`: a friend or a request either way.
struct Connection: Decodable, Identifiable, Equatable {
    let userId: String
    let username: String?
    let displayName: String?
    let avatarPath: String?
    let status: Relationship

    var id: String { userId }

    init(userId: String, username: String?, displayName: String?, avatarPath: String?, status: Relationship) {
        self.userId = userId
        self.username = username
        self.displayName = displayName
        self.avatarPath = avatarPath
        self.status = status
    }

    enum CodingKeys: String, CodingKey {
        case username, status
        case userId = "user_id"
        case displayName = "display_name"
        case avatarPath = "avatar_path"
    }

    var person: PersonCard { PersonCard(id: userId, username: username, displayName: displayName, avatarPath: avatarPath) }
}

/// A row of `my_blocked_users`.
struct BlockedUser: Decodable, Identifiable, Equatable {
    let userId: String
    let username: String?
    let displayName: String?
    let avatarPath: String?

    var id: String { userId }

    enum CodingKeys: String, CodingKey {
        case username
        case userId = "user_id"
        case displayName = "display_name"
        case avatarPath = "avatar_path"
    }

    var person: PersonCard { PersonCard(id: userId, username: username, displayName: displayName, avatarPath: avatarPath) }
}

enum Visibility: String, Codable, CaseIterable, Identifiable {
    case `public`, friends, `private`

    var id: String { rawValue }

    var title: String {
        switch self {
        case .public: return "Everyone"
        case .friends: return "Friends"
        case .private: return "Only me"
        }
    }
}

/// The user's row of `user_privacy_settings`.
struct PrivacySettings: Codable, Equatable {
    var profileVisibility: Visibility
    var activityVisibility: Visibility
    var discoverable: Bool
    var acceptFriendRequests: Bool
    var sharePresence: Bool
    var stealthMode: Bool

    enum CodingKeys: String, CodingKey {
        case discoverable
        case profileVisibility = "profile_visibility"
        case activityVisibility = "activity_visibility"
        case acceptFriendRequests = "accept_friend_requests"
        case sharePresence = "share_presence"
        case stealthMode = "stealth_mode"
    }
}
