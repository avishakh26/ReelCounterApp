import SwiftUI

// MARK: Surfaces

/// A white (raised in dark mode) card with a hairline edge: the app's main surface.
struct Card<Content: View>: View {
    var padding: CGFloat = Spacing.lg
    @ViewBuilder var content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 0) { content }
            .padding(padding)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Palette.card, in: RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: Radius.card, style: .continuous)
                    .strokeBorder(Palette.hairline, lineWidth: 1)
            )
    }
}

struct SectionHeader: View {
    let title: String
    var actionTitle: String?
    var action: (() -> Void)?

    var body: some View {
        HStack(alignment: .firstTextBaseline) {
            Text(title)
                .font(.rcTitle)
                .foregroundStyle(Palette.textPrimary)
            Spacer()
            if let actionTitle, let action {
                Button(actionTitle, action: action)
                    .font(.rcHeadline)
                    .foregroundStyle(Palette.accent)
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isHeader)
    }
}

/// Thin divider inset to line up with row text.
struct RowDivider: View {
    var inset: CGFloat = 0

    var body: some View {
        Rectangle()
            .fill(Palette.hairline)
            .frame(height: 1)
            .padding(.leading, inset)
    }
}

// MARK: Buttons

struct PrimaryButtonStyle: ButtonStyle {
    var isLoading = false
    @Environment(\.isEnabled) private var isEnabled

    func makeBody(configuration: Configuration) -> some View {
        ZStack {
            configuration.label.opacity(isLoading ? 0 : 1)
            if isLoading { ProgressView().tint(Palette.onAccent) }
        }
        .font(.rcHeadline)
        .foregroundStyle(Palette.onAccent)
        .frame(maxWidth: .infinity, minHeight: Spacing.buttonHeight)
        .background(Palette.accent.opacity(isEnabled ? 1 : 0.4), in: RoundedRectangle(cornerRadius: Radius.button, style: .continuous))
        .opacity(configuration.isPressed ? 0.85 : 1)
    }
}

struct SecondaryButtonStyle: ButtonStyle {
    var isDestructive = false
    @Environment(\.isEnabled) private var isEnabled

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.rcHeadline)
            .foregroundStyle(isDestructive ? Palette.negative : Palette.textPrimary)
            .frame(maxWidth: .infinity, minHeight: Spacing.buttonHeight)
            .background(Palette.card, in: RoundedRectangle(cornerRadius: Radius.button, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: Radius.button, style: .continuous)
                    .strokeBorder(Palette.hairline, lineWidth: 1)
            )
            .opacity(isEnabled ? (configuration.isPressed ? 0.7 : 1) : 0.4)
    }
}

/// A small pill button for list rows ("Add", "Accept").
struct PillButtonStyle: ButtonStyle {
    var filled = true
    @Environment(\.isEnabled) private var isEnabled

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.rcLabel)
            .foregroundStyle(filled ? Palette.onAccent : Palette.accent)
            .padding(.horizontal, Spacing.md)
            .frame(minHeight: 32)
            .background(filled ? Palette.accent : Palette.accentSoft, in: Capsule())
            .opacity(isEnabled ? (configuration.isPressed ? 0.75 : 1) : 0.4)
    }
}

// MARK: Feedback

enum Tone {
    case info, estimate, positive, negative

    var foreground: Color {
        switch self {
        case .info: return Palette.accent
        case .estimate: return Palette.estimate
        case .positive: return Palette.positive
        case .negative: return Palette.negative
        }
    }

    var background: Color {
        switch self {
        case .info: return Palette.accentSoft
        case .estimate: return Palette.estimateSoft
        case .positive: return Palette.positiveSoft
        case .negative: return Palette.negativeSoft
        }
    }
}

struct Banner: View {
    let tone: Tone
    let systemImage: String
    let title: String
    var message: String?

    var body: some View {
        HStack(alignment: .top, spacing: Spacing.md) {
            Image(systemName: systemImage)
                .font(.system(size: 18, weight: .semibold))
                .foregroundStyle(tone.foreground)
            VStack(alignment: .leading, spacing: Spacing.xs) {
                Text(title).font(.rcHeadline).foregroundStyle(Palette.textPrimary)
                if let message {
                    Text(message).font(.rcCallout).foregroundStyle(Palette.textSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            Spacer(minLength: 0)
        }
        .padding(Spacing.lg)
        .background(tone.background, in: RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
        .accessibilityElement(children: .combine)
    }
}

struct StatusPill: View {
    let text: String
    var systemImage: String?
    var tone: Tone = .estimate

    var body: some View {
        HStack(spacing: Spacing.xs) {
            if let systemImage { Image(systemName: systemImage).font(.system(size: 11, weight: .bold)) }
            Text(text).font(.rcLabel)
        }
        .foregroundStyle(tone.foreground)
        .padding(.horizontal, Spacing.sm)
        .padding(.vertical, Spacing.xs)
        .background(tone.background, in: Capsule())
    }
}

/// An inline error message under a form.
struct FormError: View {
    let message: String

    var body: some View {
        Banner(tone: .negative, systemImage: "exclamationmark.circle.fill", title: message)
    }
}

// MARK: States

struct EmptyStateView: View {
    let systemImage: String
    let title: String
    let message: String
    var actionTitle: String?
    var action: (() -> Void)?

    var body: some View {
        VStack(spacing: Spacing.md) {
            Image(systemName: systemImage)
                .font(.system(size: 30, weight: .semibold))
                .foregroundStyle(Palette.accent)
                .frame(width: 64, height: 64)
                .background(Palette.accentSoft, in: Circle())
            Text(title).font(.rcHeadline).foregroundStyle(Palette.textPrimary)
            Text(message)
                .font(.rcCallout)
                .foregroundStyle(Palette.textSecondary)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
            if let actionTitle, let action {
                Button(actionTitle, action: action)
                    .buttonStyle(PillButtonStyle())
                    .padding(.top, Spacing.xs)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, Spacing.xl)
        .padding(.horizontal, Spacing.lg)
    }
}

struct LoadingView: View {
    var body: some View {
        ProgressView()
            .frame(maxWidth: .infinity, minHeight: 160)
    }
}

// MARK: Text fields

struct FieldStyle: ViewModifier {
    func body(content: Content) -> some View {
        content
            .font(.rcBody)
            .padding(.horizontal, Spacing.lg)
            .frame(minHeight: 54)
            .background(Palette.card, in: RoundedRectangle(cornerRadius: Radius.button, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: Radius.button, style: .continuous)
                    .strokeBorder(Palette.hairline, lineWidth: 1)
            )
    }
}

extension View {
    func fieldStyle() -> some View { modifier(FieldStyle()) }

    /// The screen background behind scrolling content.
    func screenBackground() -> some View {
        background(Palette.background.ignoresSafeArea())
    }
}

/// A labeled text field with an optional hint or error below it.
struct LabeledField<Field: View>: View {
    let label: String
    var hint: String?
    var error: String?
    @ViewBuilder var field: Field

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            Text(label).font(.rcLabel).foregroundStyle(Palette.textSecondary)
            field.fieldStyle()
            if let error {
                Text(error).font(.rcCaption).foregroundStyle(Palette.negative)
            } else if let hint {
                Text(hint).font(.rcCaption).foregroundStyle(Palette.textSecondary)
            }
        }
    }
}

// MARK: Avatar

struct AvatarView: View {
    let url: URL?
    let name: String
    var size: CGFloat = 44

    var body: some View {
        Group {
            if let url {
                AsyncImage(url: url) { phase in
                    if let image = phase.image {
                        image.resizable().scaledToFill()
                    } else {
                        initials
                    }
                }
            } else {
                initials
            }
        }
        .frame(width: size, height: size)
        .clipShape(Circle())
        .accessibilityHidden(true)
    }

    private var initials: some View {
        Text(String(name.trimmingCharacters(in: .whitespaces).prefix(1)).uppercased())
            .font(.system(size: size * 0.4, weight: .semibold))
            .foregroundStyle(Palette.accent)
            .frame(width: size, height: size)
            .background(Palette.accentSoft)
    }
}
