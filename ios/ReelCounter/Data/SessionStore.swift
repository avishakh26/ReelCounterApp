import Foundation
import Supabase
import UIKit

/**
 Who is signed in and their profile. Sign-in, sign-up, password reset and the profile itself all go
 through here, against the same Supabase project as the Android app, so one account works on both.
 */
@MainActor
final class SessionStore: ObservableObject {
    enum State: Equatable {
        case notConfigured
        case loading
        case signedOut
        /// Signed in, but the profile couldn't be loaded (usually offline).
        case failed(AppError)
        /// Signed in without a username yet: onboarding comes first.
        case needsProfile(Profile)
        case signedIn(Profile)
    }

    @Published private(set) var state: State
    @Published private(set) var email: String?
    private(set) var userId: String?

    /// Called after sign-out or account deletion so other stores can forget this user's data.
    var onSignedOut: (@MainActor () -> Void)?

    private var authTask: Task<Void, Never>?

    init() {
        guard let client = Backend.client else {
            state = .notConfigured
            return
        }
        state = .loading
        authTask = Task { [weak self] in
            for await change in client.auth.authStateChanges {
                await self?.apply(session: change.session)
            }
        }
    }

    deinit { authTask?.cancel() }

    var profile: Profile? {
        switch state {
        case .signedIn(let profile), .needsProfile(let profile): return profile
        default: return nil
        }
    }

    private var client: SupabaseClient {
        guard let client = Backend.client else { fatalError("Backend not configured") }
        return client
    }

    // MARK: Session

    private func apply(session: Session?) async {
        guard let session else {
            if userId != nil { onSignedOut?() }
            userId = nil
            email = nil
            state = .signedOut
            return
        }
        let id = session.user.id.uuidString.lowercased()
        email = session.user.email
        // Token refreshes arrive as new sessions for the same user: keep the loaded profile.
        if id == userId, profile != nil { return }
        userId = id
        await loadProfile()
    }

    func loadProfile() async {
        guard let userId else { return }
        if profile == nil { state = .loading }
        do {
            let profile: Profile = try await client.from("profiles")
                .select("id,username,display_name,bio,avatar_path")
                .eq("id", value: userId)
                .single()
                .execute()
                .value
            state = profile.username == nil ? .needsProfile(profile) : .signedIn(profile)
        } catch {
            state = .failed(AppError.from(error))
        }
    }

    // MARK: Sign-in and sign-up

    func signIn(email: String, password: String) async throws {
        do {
            _ = try await client.auth.signIn(email: email.trimmed, password: password)
        } catch {
            throw AppError.from(error)
        }
    }

    /// Returns true when the project asks for an emailed code before the account can be used.
    func signUp(email: String, password: String) async throws -> Bool {
        do {
            // Records that sign-up went through the 16+ age gate, as on Android.
            let response = try await client.auth.signUp(
                email: email.trimmed,
                password: password,
                data: ["age_gate": .string("16+")]
            )
            return response.session == nil
        } catch {
            throw AppError.from(error)
        }
    }

    func verifySignUpCode(email: String, code: String) async throws {
        do {
            _ = try await client.auth.verifyOTP(email: email.trimmed, token: code.trimmed, type: .email)
        } catch {
            throw AppError.from(error)
        }
    }

    func resendSignUpCode(email: String) async throws {
        do {
            try await client.auth.resend(email: email.trimmed, type: .signup)
        } catch {
            throw AppError.from(error)
        }
    }

    // MARK: Password reset

    func sendPasswordResetCode(email: String) async throws {
        do {
            try await client.auth.resetPasswordForEmail(email.trimmed)
        } catch {
            throw AppError.from(error)
        }
    }

    /// Signs in with the emailed recovery code, so the new password can be set.
    func verifyPasswordResetCode(email: String, code: String) async throws {
        do {
            _ = try await client.auth.verifyOTP(email: email.trimmed, token: code.trimmed, type: .recovery)
        } catch {
            throw AppError.from(error)
        }
    }

    func updatePassword(_ password: String) async throws {
        do {
            _ = try await client.auth.update(user: UserAttributes(password: password))
        } catch {
            throw AppError.from(error)
        }
    }

    func signOut() async {
        // Signing out must work offline too: the local session is dropped either way.
        try? await client.auth.signOut()
        if userId != nil {
            onSignedOut?()
            userId = nil
            state = .signedOut
        }
    }

    // MARK: Profile

    enum UsernameStatus: String {
        case available, taken, reserved, invalid
    }

    func checkUsername(_ candidate: String) async -> UsernameStatus? {
        do {
            let result: String = try await client.rpc("check_username", params: ["candidate": candidate]).execute().value
            return UsernameStatus(rawValue: result)
        } catch {
            return nil
        }
    }

    func saveProfile(username: String?, displayName: String?, bio: String?) async throws {
        guard let current = profile, let userId else { return }
        let update = ProfileUpdate(
            username: username?.trimmed.lowercased().nilIfEmpty,
            displayName: displayName?.trimmed.nilIfEmpty,
            bio: bio?.trimmed.nilIfEmpty,
            avatarPath: current.avatarPath
        )
        do {
            let saved: Profile = try await client.from("profiles")
                .update(update)
                .eq("id", value: userId)
                .select("id,username,display_name,bio,avatar_path")
                .single()
                .execute()
                .value
            state = saved.username == nil ? .needsProfile(saved) : .signedIn(saved)
        } catch {
            let text = String(describing: error).lowercased()
            if text.contains("duplicate") || text.contains("23505") {
                throw AppError(message: "That username was just taken. Try another.")
            }
            throw AppError.from(error)
        }
    }

    /// Uploads a new profile photo (resized to 512 px JPEG) and removes the old one.
    func setAvatar(imageData: Data) async throws {
        guard let current = profile, let userId else { return }
        guard let image = UIImage(data: imageData), let jpeg = image.resizedSquare(to: 512).jpegData(compressionQuality: 0.82) else {
            throw AppError(message: "That photo can't be used. Try another one.")
        }
        let path = "\(userId)/\(UUID().uuidString.lowercased()).jpg"
        do {
            _ = try await client.storage.from("avatars").upload(path, data: jpeg, options: FileOptions(contentType: "image/jpeg", upsert: false))
            let update = ProfileUpdate(username: current.username, displayName: current.displayName, bio: current.bio, avatarPath: path)
            let saved: Profile = try await client.from("profiles")
                .update(update)
                .eq("id", value: userId)
                .select("id,username,display_name,bio,avatar_path")
                .single()
                .execute()
                .value
            state = saved.username == nil ? .needsProfile(saved) : .signedIn(saved)
            if let old = current.avatarPath {
                _ = try? await client.storage.from("avatars").remove(paths: [old])
            }
        } catch {
            throw AppError.from(error)
        }
    }

    /// Deletes the account and everything stored with it, then signs out.
    func deleteAccount() async throws {
        if let avatar = profile?.avatarPath {
            _ = try? await client.storage.from("avatars").remove(paths: [avatar])
        }
        do {
            try await client.rpc("delete_my_account").execute()
        } catch {
            throw AppError.from(error)
        }
        await signOut()
    }
}

extension String {
    var trimmed: String { trimmingCharacters(in: .whitespacesAndNewlines) }
    var nilIfEmpty: String? { isEmpty ? nil : self }
}

private extension UIImage {
    /// Center-cropped square of [side] points at scale 1.
    func resizedSquare(to side: CGFloat) -> UIImage {
        let shortest = min(size.width, size.height)
        let scale = side / shortest
        let drawn = CGSize(width: size.width * scale, height: size.height * scale)
        let origin = CGPoint(x: (side - drawn.width) / 2, y: (side - drawn.height) / 2)
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        return UIGraphicsImageRenderer(size: CGSize(width: side, height: side), format: format).image { _ in
            draw(in: CGRect(origin: origin, size: drawn))
        }
    }
}
