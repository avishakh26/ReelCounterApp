import PhotosUI
import SwiftUI

struct SettingsView: View {
    @EnvironmentObject private var session: SessionStore
    @EnvironmentObject private var tracking: TrackingStore
    @State private var confirmSignOut = false
    @State private var confirmDelete = false
    @State private var isDeleting = false
    @State private var deleteError: AppError?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.section) {
                Text("Settings").font(.rcLargeTitle)

                NavigationLink { EditProfileView() } label: { profileCard }
                    .buttonStyle(.plain)

                group("Tracking") {
                    SettingsRow(systemImage: "hourglass", title: "Tracking & apps", value: trackingSummary) { TrackingSettingsView() }
                    RowDivider(inset: 52)
                    SettingsRow(systemImage: "questionmark.circle", title: "How we count") { HowWeCountView() }
                }

                group("Privacy") {
                    SettingsRow(systemImage: "lock", title: "Privacy") { PrivacyView() }
                    RowDivider(inset: 52)
                    SettingsRow(systemImage: "hand.raised", title: "Blocked people") { BlockedUsersView() }
                }

                group("Account") {
                    if let email = session.email {
                        HStack(spacing: Spacing.md) {
                            SettingsIcon(systemImage: "envelope")
                            Text(email).font(.rcBody).foregroundStyle(Palette.textSecondary).lineLimit(1)
                            Spacer()
                        }
                        .padding(.vertical, Spacing.md)
                        RowDivider(inset: 52)
                    }
                    Button { confirmSignOut = true } label: {
                        HStack(spacing: Spacing.md) {
                            SettingsIcon(systemImage: "rectangle.portrait.and.arrow.right")
                            Text("Sign out").font(.rcBody).foregroundStyle(Palette.textPrimary)
                            Spacer()
                        }
                        .padding(.vertical, Spacing.md)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    RowDivider(inset: 52)
                    Button { confirmDelete = true } label: {
                        HStack(spacing: Spacing.md) {
                            SettingsIcon(systemImage: "trash", tint: Palette.negative)
                            Text("Delete account").font(.rcBody).foregroundStyle(Palette.negative)
                            Spacer()
                            if isDeleting { ProgressView() }
                        }
                        .padding(.vertical, Spacing.md)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .disabled(isDeleting)
                }

                if let deleteError { FormError(message: deleteError.message) }

                Text("ReelCounter \(Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "")")
                    .font(.rcCaption)
                    .foregroundStyle(Palette.textSecondary)
                    .frame(maxWidth: .infinity)
            }
            .padding(.horizontal, Spacing.screen)
            .padding(.vertical, Spacing.lg)
        }
        .screenBackground()
        .toolbar(.hidden, for: .navigationBar)
        .confirmationDialog("Sign out of ReelCounter?", isPresented: $confirmSignOut, titleVisibility: .visible) {
            Button("Sign out", role: .destructive) { Task { await session.signOut() } }
        } message: {
            Text("Tracking stops on this iPhone and its recorded app time is removed. Figures already saved to your account stay.")
        }
        .confirmationDialog("Delete your account?", isPresented: $confirmDelete, titleVisibility: .visible) {
            Button("Delete account", role: .destructive, action: deleteAccount)
        } message: {
            Text("Your profile, friends and all saved figures are permanently deleted, on iPhone and Android. This can't be undone.")
        }
    }

    private var trackingSummary: String {
        switch tracking.status {
        case .active: return "\(tracking.trackedPlatforms.count) apps"
        case .unavailable: return "Free version"
        default: return "Off"
        }
    }

    private var profileCard: some View {
        Card {
            HStack(spacing: Spacing.lg) {
                AvatarView(url: Backend.avatarURL(path: session.profile?.avatarPath), name: session.profile?.shownName ?? "?", size: 56)
                VStack(alignment: .leading, spacing: Spacing.xxs) {
                    Text(session.profile?.shownName ?? "").font(.rcHeadline).foregroundStyle(Palette.textPrimary)
                    if let username = session.profile?.username {
                        Text("@\(username)").font(.rcCallout).foregroundStyle(Palette.textSecondary)
                    }
                    Text("Edit profile").font(.rcLabel).foregroundStyle(Palette.accent).padding(.top, Spacing.xxs)
                }
                Spacer()
                Image(systemName: "chevron.right").foregroundStyle(Palette.textSecondary)
            }
        }
    }

    private func group<Content: View>(_ title: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            Text(title.uppercased()).font(.rcLabel).foregroundStyle(Palette.textSecondary).padding(.leading, Spacing.xs)
            Card(padding: 0) {
                VStack(spacing: 0) { content() }.padding(.horizontal, Spacing.lg)
            }
        }
    }

    private func deleteAccount() {
        isDeleting = true
        deleteError = nil
        Task {
            do {
                try await session.deleteAccount()
            } catch {
                deleteError = AppError.from(error)
            }
            isDeleting = false
        }
    }
}

struct SettingsIcon: View {
    let systemImage: String
    var tint: Color = Palette.accent

    var body: some View {
        Image(systemName: systemImage)
            .font(.system(size: 15, weight: .semibold))
            .foregroundStyle(tint)
            .frame(width: 32, height: 32)
            .background(tint.opacity(0.12), in: RoundedRectangle(cornerRadius: 9, style: .continuous))
    }
}

struct SettingsRow<Destination: View>: View {
    let systemImage: String
    let title: String
    var value: String?
    @ViewBuilder var destination: () -> Destination

    var body: some View {
        NavigationLink(destination: destination) {
            HStack(spacing: Spacing.md) {
                SettingsIcon(systemImage: systemImage)
                Text(title).font(.rcBody).foregroundStyle(Palette.textPrimary)
                Spacer()
                if let value { Text(value).font(.rcCallout).foregroundStyle(Palette.textSecondary) }
                Image(systemName: "chevron.right").font(.system(size: 13, weight: .semibold)).foregroundStyle(Palette.textSecondary)
            }
            .padding(.vertical, Spacing.md)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

// MARK: Edit profile

struct EditProfileView: View {
    @EnvironmentObject private var session: SessionStore
    @Environment(\.dismiss) private var dismiss
    @State private var photo: PhotosPickerItem?
    @State private var isUploading = false
    @State private var photoError: AppError?

    var body: some View {
        ScrollView {
            VStack(spacing: Spacing.xl) {
                PhotosPicker(selection: $photo, matching: .images) {
                    ZStack(alignment: .bottomTrailing) {
                        AvatarView(url: Backend.avatarURL(path: session.profile?.avatarPath), name: session.profile?.shownName ?? "?", size: 96)
                        Image(systemName: "camera.fill")
                            .font(.system(size: 13, weight: .bold))
                            .foregroundStyle(Palette.onAccent)
                            .frame(width: 30, height: 30)
                            .background(Palette.accent, in: Circle())
                            .overlay(Circle().strokeBorder(Palette.background, lineWidth: 2))
                        if isUploading {
                            ProgressView().frame(width: 96, height: 96).background(.ultraThinMaterial, in: Circle())
                        }
                    }
                }
                .accessibilityLabel("Change profile photo")
                if let photoError { FormError(message: photoError.message) }
                ProfileForm(
                    initialUsername: session.profile?.username ?? "",
                    initialDisplayName: session.profile?.displayName ?? "",
                    initialBio: session.profile?.bio ?? "",
                    saveTitle: "Save",
                    onSaved: { dismiss() }
                )
            }
            .padding(Spacing.screen)
        }
        .scrollDismissesKeyboard(.interactively)
        .screenBackground()
        .navigationTitle("Edit profile")
        .navigationBarTitleDisplayMode(.inline)
        .onChange(of: photo) { item in
            guard let item else { return }
            isUploading = true
            photoError = nil
            Task {
                do {
                    guard let data = try await item.loadTransferable(type: Data.self) else { throw AppError(message: "That photo can't be used. Try another one.") }
                    try await session.setAvatar(imageData: data)
                } catch {
                    photoError = AppError.from(error)
                }
                isUploading = false
                photo = nil
            }
        }
    }
}

// MARK: Privacy

struct PrivacyView: View {
    @State private var settings: PrivacySettings?
    @State private var error: AppError?
    @State private var isSaving = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.section) {
                if let error { FormError(message: error.message) }
                if let binding = Binding($settings) {
                    form(binding)
                } else if error == nil {
                    LoadingView()
                }
            }
            .padding(Spacing.screen)
        }
        .screenBackground()
        .navigationTitle("Privacy")
        .navigationBarTitleDisplayMode(.inline)
        .task { await load() }
    }

    private func form(_ settings: Binding<PrivacySettings>) -> some View {
        VStack(alignment: .leading, spacing: Spacing.section) {
            VStack(alignment: .leading, spacing: Spacing.sm) {
                Text("WHO CAN SEE").font(.rcLabel).foregroundStyle(Palette.textSecondary)
                Card {
                    VStack(spacing: Spacing.md) {
                        visibilityPicker("Your profile", selection: settings.profileVisibility)
                        RowDivider()
                        visibilityPicker("Your daily figures", selection: settings.activityVisibility)
                    }
                }
            }
            VStack(alignment: .leading, spacing: Spacing.sm) {
                Text("FRIENDS").font(.rcLabel).foregroundStyle(Palette.textSecondary)
                Card {
                    VStack(spacing: Spacing.md) {
                        toggle("Appear in search and suggestions", isOn: settings.discoverable)
                        RowDivider()
                        toggle("Accept friend requests", isOn: settings.acceptFriendRequests)
                        RowDivider()
                        toggle("Show friends when I'm online", isOn: settings.sharePresence)
                        RowDivider()
                        toggle("Stealth mode (hide that I'm online from everyone)", isOn: settings.stealthMode)
                    }
                }
            }
            Text("Changes save automatically and apply on iPhone and Android.")
                .font(.rcCaption)
                .foregroundStyle(Palette.textSecondary)
        }
        .onChange(of: settings.wrappedValue) { value in
            Task { await save(value) }
        }
    }

    private func visibilityPicker(_ title: String, selection: Binding<Visibility>) -> some View {
        HStack {
            Text(title).font(.rcBody)
            Spacer()
            Picker(title, selection: selection) {
                ForEach(Visibility.allCases) { Text($0.title).tag($0) }
            }
            .pickerStyle(.menu)
        }
    }

    private func toggle(_ title: String, isOn: Binding<Bool>) -> some View {
        Toggle(isOn: isOn) { Text(title).font(.rcBody) }.tint(Palette.accent)
    }

    private func load() async {
        guard let client = Backend.client, let userId = client.auth.currentSession?.user.id.uuidString.lowercased() else { return }
        do {
            settings = try await client.from("user_privacy_settings")
                .select("profile_visibility,activity_visibility,discoverable,accept_friend_requests,share_presence,stealth_mode")
                .eq("user_id", value: userId)
                .single()
                .execute()
                .value
        } catch {
            self.error = AppError.from(error)
        }
    }

    private func save(_ value: PrivacySettings) async {
        guard let client = Backend.client, let userId = client.auth.currentSession?.user.id.uuidString.lowercased() else { return }
        do {
            try await client.from("user_privacy_settings").update(value).eq("user_id", value: userId).execute()
            error = nil
        } catch {
            self.error = AppError.from(error)
        }
    }
}

// MARK: Blocked people

struct BlockedUsersView: View {
    @EnvironmentObject private var friends: FriendsStore
    @State private var blocked: [BlockedUser]?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.lg) {
                if let blocked {
                    if blocked.isEmpty {
                        Card { EmptyStateView(systemImage: "hand.raised", title: "No one blocked", message: "People you block can't find you, send requests or see your figures.") }
                    } else {
                        PeopleCard(items: blocked.map(\.person)) { person in
                            Button("Unblock") {
                                Task {
                                    await friends.unblock(person)
                                    self.blocked = await friends.blockedUsers()
                                }
                            }
                            .buttonStyle(PillButtonStyle(filled: false))
                        }
                    }
                } else {
                    LoadingView()
                }
            }
            .padding(Spacing.screen)
        }
        .screenBackground()
        .navigationTitle("Blocked people")
        .navigationBarTitleDisplayMode(.inline)
        .task { blocked = await friends.blockedUsers() }
    }
}
