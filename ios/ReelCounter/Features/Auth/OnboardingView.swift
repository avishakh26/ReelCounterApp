import SwiftUI

/// After sign-up: choose a username (required, so friends can find you) and a display name.
struct OnboardingView: View {
    @EnvironmentObject private var session: SessionStore

    var body: some View {
        NavigationStack {
            AuthScreen(title: "Set up your profile", subtitle: "Friends find you by your username.") {
                ProfileForm(
                    initialUsername: session.profile?.username ?? "",
                    initialDisplayName: session.profile?.displayName ?? "",
                    initialBio: session.profile?.bio ?? "",
                    saveTitle: "Continue"
                )
                Button("Sign out") { Task { await session.signOut() } }
                    .buttonStyle(SecondaryButtonStyle())
            }
        }
    }
}

/// Username (checked live), display name and bio, with save. Used by onboarding and Edit profile.
struct ProfileForm: View {
    @EnvironmentObject private var session: SessionStore
    @State private var username: String
    @State private var displayName: String
    @State private var bio: String
    let saveTitle: String
    var onSaved: (() -> Void)?

    @State private var usernameStatus: SessionStore.UsernameStatus?
    @State private var isChecking = false
    @State private var isSaving = false
    @State private var error: AppError?

    init(initialUsername: String, initialDisplayName: String, initialBio: String, saveTitle: String, onSaved: (() -> Void)? = nil) {
        _username = State(initialValue: initialUsername)
        _displayName = State(initialValue: initialDisplayName)
        _bio = State(initialValue: initialBio)
        self.saveTitle = saveTitle
        self.onSaved = onSaved
    }

    private var normalized: String { username.trimmed.lowercased() }

    /// Same rules as the server: 3–20 of a–z, 0–9, "_" and "."; starts and ends with a letter or digit; no "..".
    private var formatProblem: String? {
        guard !normalized.isEmpty else { return nil }
        let pattern = "^[a-z0-9][a-z0-9_.]{1,18}[a-z0-9]$"
        if normalized.range(of: pattern, options: .regularExpression) == nil || normalized.contains("..") {
            return "3–20 letters, numbers, _ or . (start and end with a letter or number)."
        }
        return nil
    }

    private var usernameMessage: (text: String, isError: Bool)? {
        if let formatProblem { return (formatProblem, true) }
        switch usernameStatus {
        case .available: return ("Available", false)
        case .taken: return ("That username is taken.", true)
        case .reserved: return ("That username isn't available.", true)
        case .invalid: return ("That username can't be used.", true)
        case nil: return isChecking ? ("Checking…", false) : nil
        }
    }

    private var canSave: Bool {
        !normalized.isEmpty && formatProblem == nil && usernameStatus == .available
            && displayName.trimmed.count <= 50 && bio.count <= 160 && !isSaving
    }

    var body: some View {
        VStack(spacing: Spacing.lg) {
            if let error { FormError(message: error.message) }
            LabeledField(label: "Username", hint: usernameMessage?.isError == false ? usernameMessage?.text : nil,
                         error: usernameMessage?.isError == true ? usernameMessage?.text : nil) {
                HStack(spacing: Spacing.xxs) {
                    Text("@").foregroundStyle(Palette.textSecondary)
                    TextField("username", text: $username)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .textContentType(.nickname)
                }
            }
            LabeledField(label: "Display name", hint: "Optional. Shown to friends instead of your username.") {
                TextField("Your name", text: $displayName)
                    .textContentType(.name)
            }
            LabeledField(label: "Bio", hint: "\(bio.count)/160") {
                TextField("A line about you", text: $bio, axis: .vertical)
                    .lineLimit(1...3)
                    .padding(.vertical, Spacing.md)
            }
            Button(saveTitle, action: save)
                .buttonStyle(PrimaryButtonStyle(isLoading: isSaving))
                .disabled(!canSave)
        }
        .task(id: normalized) {
            usernameStatus = nil
            guard !normalized.isEmpty, formatProblem == nil else { return }
            isChecking = true
            // Wait for typing to pause before asking the server.
            try? await Task.sleep(nanoseconds: 400_000_000)
            guard !Task.isCancelled else { return }
            usernameStatus = await session.checkUsername(normalized)
            isChecking = false
        }
    }

    private func save() {
        isSaving = true
        error = nil
        Task {
            do {
                try await session.saveProfile(username: normalized, displayName: displayName, bio: bio)
                onSaved?()
            } catch {
                self.error = AppError.from(error)
            }
            isSaving = false
        }
    }
}
