import Foundation
import Supabase

/// The Supabase project the app talks to, read from Info.plist (filled in from Config/*.xcconfig).
struct BackendConfig {
    let url: URL
    let publishableKey: String

    /// nil while the committed placeholders are in use: the app then explains how to configure it.
    static let current: BackendConfig? = {
        let info = Bundle.main.infoDictionary ?? [:]
        guard let urlText = info["SUPABASE_URL"] as? String,
              let key = info["SUPABASE_PUBLISHABLE_KEY"] as? String,
              let url = URL(string: urlText),
              url.host != "unconfigured.invalid",
              key != "unconfigured", !key.isEmpty,
              // Secret keys bypass row level security and must never ship in an app.
              !key.hasPrefix("sb_secret_")
        else { return nil }
        return BackendConfig(url: url, publishableKey: key)
    }()
}

/// The one Supabase client, created only when the backend is configured.
enum Backend {
    static let client: SupabaseClient? = BackendConfig.current.map {
        SupabaseClient(supabaseURL: $0.url, supabaseKey: $0.publishableKey)
    }

    static func avatarURL(path: String?) -> URL? {
        guard let path, !path.isEmpty, let client else { return nil }
        return try? client.storage.from("avatars").getPublicURL(path: path)
    }
}

/// A failure shown to the user in plain words.
struct AppError: LocalizedError, Equatable {
    let message: String

    var errorDescription: String? { message }

    static let offline = AppError(message: "Can't reach ReelCounter. Check your connection and try again.")
    static let generic = AppError(message: "Something went wrong. Please try again.")

    /// Maps network, auth and database errors to a message.
    static func from(_ error: Error) -> AppError {
        if let error = error as? AppError { return error }
        if let urlError = error as? URLError {
            switch urlError.code {
            case .notConnectedToInternet, .networkConnectionLost, .timedOut, .cannotConnectToHost, .cannotFindHost:
                return .offline
            default:
                return .generic
            }
        }
        let text = String(describing: error).lowercased()
        if text.contains("invalid login credentials") {
            return AppError(message: "That email and password don't match. Check them and try again.")
        }
        if text.contains("user already registered") || text.contains("already been registered") {
            return AppError(message: "There's already an account with this email. Sign in instead.")
        }
        if text.contains("email not confirmed") {
            return AppError(message: "Confirm your email first: enter the code we sent you.")
        }
        if text.contains("token has expired") || text.contains("otp") && text.contains("invalid") {
            return AppError(message: "That code is wrong or has expired. Check it or ask for a new one.")
        }
        if text.contains("rate limit") || text.contains("too many") {
            return AppError(message: "Too many tries. Wait a minute and try again.")
        }
        if text.contains("password") && (text.contains("weak") || text.contains("at least")) {
            return AppError(message: "Choose a stronger password: at least 8 characters with letters and numbers.")
        }
        return .generic
    }
}
