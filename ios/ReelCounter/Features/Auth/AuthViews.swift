import SwiftUI
import UIKit

/// Sign in, create an account, confirm the email code, reset a forgotten password.
struct AuthFlowView: View {
    enum Route: Hashable {
        case signUp
        case verifyEmail(String)
        case forgotPassword
    }

    @State private var path: [Route] = []

    var body: some View {
        NavigationStack(path: $path) {
            SignInView(path: $path)
                .navigationDestination(for: Route.self) { route in
                    switch route {
                    case .signUp: SignUpView(path: $path)
                    case .verifyEmail(let email): VerifyEmailView(email: email)
                    case .forgotPassword: ForgotPasswordView()
                    }
                }
        }
    }
}

/// Shared layout: brand mark, title, subtitle, then the form, scrolling with the keyboard.
struct AuthScreen<Content: View>: View {
    let title: String
    let subtitle: String
    var showsMark = false
    @ViewBuilder var content: Content

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.xl) {
                VStack(alignment: .leading, spacing: Spacing.md) {
                    if showsMark { BrandMark(size: 64).padding(.bottom, Spacing.sm) }
                    Text(title).font(.rcLargeTitle).foregroundStyle(Palette.textPrimary)
                    Text(subtitle).font(.rcBody).foregroundStyle(Palette.textSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
                content
            }
            .padding(.horizontal, Spacing.screen)
            .padding(.vertical, Spacing.xl)
        }
        .scrollDismissesKeyboard(.interactively)
        .screenBackground()
    }
}

struct SignInView: View {
    @EnvironmentObject private var session: SessionStore
    @Binding var path: [AuthFlowView.Route]
    @State private var email = ""
    @State private var password = ""
    @State private var showPassword = false
    @State private var isWorking = false
    @State private var error: AppError?

    var body: some View {
        AuthScreen(
            title: "Welcome to ReelCounter",
            subtitle: "Sign in to see your short-video habits and compare with friends.",
            showsMark: true
        ) {
            VStack(spacing: Spacing.md) {
                if let error { FormError(message: error.message) }
                TextField("Email", text: $email)
                    .textContentType(.username)
                    .keyboardType(.emailAddress)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .fieldStyle()
                PasswordField(title: "Password", text: $password, isVisible: $showPassword, contentType: .password)
                HStack {
                    Spacer()
                    Button("Forgot password?") { path.append(.forgotPassword) }
                        .font(.rcHeadline)
                        .foregroundStyle(Palette.accent)
                }
                Button("Sign in", action: signIn)
                    .buttonStyle(PrimaryButtonStyle(isLoading: isWorking))
                    .disabled(email.trimmed.isEmpty || password.isEmpty || isWorking)
                    .padding(.top, Spacing.sm)
                Text("New to ReelCounter?")
                    .font(.rcCallout)
                    .foregroundStyle(Palette.textSecondary)
                    .padding(.top, Spacing.md)
                Button("Create an account") { path.append(.signUp) }
                    .buttonStyle(SecondaryButtonStyle())
            }
        }
        .toolbar(.hidden, for: .navigationBar)
    }

    private func signIn() {
        isWorking = true
        error = nil
        Task {
            do {
                try await session.signIn(email: email, password: password)
            } catch {
                self.error = AppError.from(error)
            }
            isWorking = false
        }
    }
}

struct SignUpView: View {
    @EnvironmentObject private var session: SessionStore
    @Binding var path: [AuthFlowView.Route]
    @State private var email = ""
    @State private var password = ""
    @State private var showPassword = false
    @State private var isOldEnough = false
    @State private var isWorking = false
    @State private var error: AppError?

    private var passwordProblem: String? {
        guard !password.isEmpty else { return nil }
        if password.count < 8 { return "Use at least 8 characters." }
        if password.rangeOfCharacter(from: .letters) == nil || password.rangeOfCharacter(from: .decimalDigits) == nil {
            return "Use both letters and numbers."
        }
        return nil
    }

    private var canSubmit: Bool {
        email.trimmed.contains("@") && password.count >= 8 && passwordProblem == nil && isOldEnough && !isWorking
    }

    var body: some View {
        AuthScreen(title: "Create your account", subtitle: "One account works on iPhone and Android.") {
            VStack(spacing: Spacing.md) {
                if let error { FormError(message: error.message) }
                TextField("Email", text: $email)
                    .textContentType(.username)
                    .keyboardType(.emailAddress)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .fieldStyle()
                VStack(alignment: .leading, spacing: Spacing.xs) {
                    PasswordField(title: "Password", text: $password, isVisible: $showPassword, contentType: .newPassword)
                    Text(passwordProblem ?? "At least 8 characters, with letters and numbers.")
                        .font(.rcCaption)
                        .foregroundStyle(passwordProblem == nil ? Palette.textSecondary : Palette.negative)
                }
                Toggle(isOn: $isOldEnough) {
                    Text("I'm 16 or older").font(.rcBody)
                }
                .tint(Palette.accent)
                .padding(.vertical, Spacing.xs)
                Button("Create account", action: signUp)
                    .buttonStyle(PrimaryButtonStyle(isLoading: isWorking))
                    .disabled(!canSubmit)
                Text("By creating an account you agree that your daily totals are saved to your account and shown to friends you accept, as described in the tracking notice.")
                    .font(.rcCaption)
                    .foregroundStyle(Palette.textSecondary)
            }
        }
        .navigationTitle("")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func signUp() {
        isWorking = true
        error = nil
        Task {
            do {
                let needsCode = try await session.signUp(email: email, password: password)
                if needsCode { path.append(.verifyEmail(email.trimmed)) }
            } catch {
                self.error = AppError.from(error)
            }
            isWorking = false
        }
    }
}

struct VerifyEmailView: View {
    @EnvironmentObject private var session: SessionStore
    let email: String
    @State private var code = ""
    @State private var isWorking = false
    @State private var error: AppError?
    @State private var info: String?

    var body: some View {
        AuthScreen(title: "Check your email", subtitle: "Enter the code we sent to \(email).") {
            VStack(spacing: Spacing.md) {
                if let error { FormError(message: error.message) }
                if let info { Banner(tone: .positive, systemImage: "checkmark.circle.fill", title: info) }
                CodeField(code: $code)
                Button("Confirm") {
                    run { try await session.verifySignUpCode(email: email, code: code) }
                }
                .buttonStyle(PrimaryButtonStyle(isLoading: isWorking))
                .disabled(code.count < 6 || isWorking)
                Button("Send a new code") {
                    run {
                        try await session.resendSignUpCode(email: email)
                        info = "A new code is on its way."
                    }
                }
                .buttonStyle(SecondaryButtonStyle())
                .disabled(isWorking)
            }
        }
        .navigationBarTitleDisplayMode(.inline)
    }

    private func run(_ work: @escaping () async throws -> Void) {
        isWorking = true
        error = nil
        info = nil
        Task {
            do { try await work() } catch { self.error = AppError.from(error) }
            isWorking = false
        }
    }
}

struct ForgotPasswordView: View {
    @EnvironmentObject private var session: SessionStore
    @Environment(\.dismiss) private var dismiss

    enum Step { case email, code, newPassword, done }

    @State private var step = Step.email
    @State private var email = ""
    @State private var code = ""
    @State private var password = ""
    @State private var showPassword = false
    @State private var isWorking = false
    @State private var error: AppError?

    var body: some View {
        AuthScreen(title: title, subtitle: subtitle) {
            VStack(spacing: Spacing.md) {
                if let error { FormError(message: error.message) }
                switch step {
                case .email:
                    TextField("Email", text: $email)
                        .textContentType(.username)
                        .keyboardType(.emailAddress)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .fieldStyle()
                    Button("Send code") {
                        run {
                            try await session.sendPasswordResetCode(email: email)
                            step = .code
                        }
                    }
                    .buttonStyle(PrimaryButtonStyle(isLoading: isWorking))
                    .disabled(!email.contains("@") || isWorking)
                case .code:
                    CodeField(code: $code)
                    Button("Continue") {
                        run {
                            try await session.verifyPasswordResetCode(email: email, code: code)
                            step = .newPassword
                        }
                    }
                    .buttonStyle(PrimaryButtonStyle(isLoading: isWorking))
                    .disabled(code.count < 6 || isWorking)
                case .newPassword:
                    PasswordField(title: "New password", text: $password, isVisible: $showPassword, contentType: .newPassword)
                    Button("Save password") {
                        run {
                            try await session.updatePassword(password)
                            step = .done
                        }
                    }
                    .buttonStyle(PrimaryButtonStyle(isLoading: isWorking))
                    .disabled(password.count < 8 || isWorking)
                case .done:
                    Banner(tone: .positive, systemImage: "checkmark.circle.fill", title: "Password changed", message: "You're signed in with your new password.")
                }
            }
        }
        .navigationBarTitleDisplayMode(.inline)
    }

    private var title: String {
        switch step {
        case .email: return "Reset your password"
        case .code: return "Check your email"
        case .newPassword: return "Choose a new password"
        case .done: return "All set"
        }
    }

    private var subtitle: String {
        switch step {
        case .email: return "We'll email you a code to set a new password."
        case .code: return "Enter the code we sent to \(email.trimmed)."
        case .newPassword: return "At least 8 characters, with letters and numbers."
        case .done: return ""
        }
    }

    private func run(_ work: @escaping () async throws -> Void) {
        isWorking = true
        error = nil
        Task {
            do { try await work() } catch { self.error = AppError.from(error) }
            isWorking = false
        }
    }
}

// MARK: Fields

struct PasswordField: View {
    let title: String
    @Binding var text: String
    @Binding var isVisible: Bool
    var contentType: UITextContentType = .password

    var body: some View {
        HStack {
            Group {
                if isVisible {
                    TextField(title, text: $text)
                } else {
                    SecureField(title, text: $text)
                }
            }
            .textContentType(contentType)
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled()
            Button {
                isVisible.toggle()
            } label: {
                Image(systemName: isVisible ? "eye.slash" : "eye")
                    .foregroundStyle(Palette.textSecondary)
            }
            .accessibilityLabel(isVisible ? "Hide password" : "Show password")
        }
        .fieldStyle()
    }
}

/// The emailed one-time code, in large spaced digits.
struct CodeField: View {
    @Binding var code: String

    var body: some View {
        TextField("Code", text: Binding(
            get: { code },
            set: { code = String($0.filter(\.isNumber).prefix(8)) }
        ))
        .keyboardType(.numberPad)
        .textContentType(.oneTimeCode)
        .font(.system(size: 28, weight: .semibold, design: .rounded).monospacedDigit())
        .kerning(6)
        .multilineTextAlignment(.center)
        .fieldStyle()
    }
}
