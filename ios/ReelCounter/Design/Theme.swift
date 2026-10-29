import SwiftUI
import UIKit

/// Colors shared with the Android design system (core/designsystem Color.kt), light and dark.
enum Palette {
    static let background = dynamic(light: 0xF6F6F8, dark: 0x0F0F12)
    static let card = dynamic(light: 0xFFFFFF, dark: 0x1B1B20)
    static let cardRaised = dynamic(light: 0xF1F1F4, dark: 0x24242A)
    static let hairline = dynamic(light: 0xE6E6EB, dark: 0x2C2C33)
    static let accent = dynamic(light: 0x5B4CDB, dark: 0xA99FFF)
    static let onAccent = dynamic(light: 0xFFFFFF, dark: 0x1A1440)
    static let accentSoft = dynamic(light: 0xECE9FD, dark: 0x2A2550)
    static let textPrimary = dynamic(light: 0x16161A, dark: 0xF2F2F5)
    static let textSecondary = dynamic(light: 0x5F5F6B, dark: 0xA9A9B4)
    static let estimate = dynamic(light: 0x7A4B00, dark: 0xFFCB6B)
    static let estimateSoft = dynamic(light: 0xFFF1D6, dark: 0x3A2B0B)
    static let positive = dynamic(light: 0x1E7A4C, dark: 0x6FD6A0)
    static let positiveSoft = dynamic(light: 0xE2F5EA, dark: 0x153626)
    static let negative = dynamic(light: 0xB3261E, dark: 0xFFB4AB)
    static let negativeSoft = dynamic(light: 0xFCE7E5, dark: 0x3D1714)
    static let chartIdle = dynamic(light: 0xE4E4EA, dark: 0x2E2E36)

    private static func dynamic(light: UInt32, dark: UInt32) -> Color {
        Color(UIColor { traits in
            UIColor(hex: traits.userInterfaceStyle == .dark ? dark : light)
        })
    }
}

private extension UIColor {
    convenience init(hex: UInt32) {
        self.init(
            red: CGFloat((hex >> 16) & 0xFF) / 255,
            green: CGFloat((hex >> 8) & 0xFF) / 255,
            blue: CGFloat(hex & 0xFF) / 255,
            alpha: 1
        )
    }
}

/// The 4-point spacing grid (Android Spacing.kt).
enum Spacing {
    static let xxs: CGFloat = 2
    static let xs: CGFloat = 4
    static let sm: CGFloat = 8
    static let md: CGFloat = 12
    static let lg: CGFloat = 16
    static let xl: CGFloat = 24
    static let xxl: CGFloat = 32
    static let screen: CGFloat = 20
    static let section: CGFloat = 28
    static let buttonHeight: CGFloat = 52
}

enum Radius {
    static let small: CGFloat = 10
    static let button: CGFloat = 12
    static let card: CGFloat = 18
    static let sheet: CGFloat = 24
}

extension Font {
    static let rcHero = Font.system(size: 52, weight: .bold, design: .rounded).monospacedDigit()
    static let rcLargeTitle = Font.system(size: 30, weight: .bold)
    static let rcTitle = Font.system(size: 22, weight: .semibold)
    static let rcHeadline = Font.system(size: 17, weight: .semibold)
    static let rcBody = Font.system(size: 16)
    static let rcCallout = Font.system(size: 15)
    static let rcCaption = Font.system(size: 13)
    static let rcLabel = Font.system(size: 13, weight: .semibold)
    static let rcFigure = Font.system(size: 22, weight: .semibold, design: .rounded).monospacedDigit()
}
