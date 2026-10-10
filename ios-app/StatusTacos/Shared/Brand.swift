import CoreText
import SwiftUI
import UIKit

/// The look of the Status Tacos website: colors from the logo and the Bungee sign font.
enum Brand {
    /// The teal of the logo. The same in light and dark mode.
    static let teal = Color(hex: 0x33ADA4)
    /// The tortilla yellow: buttons and the shade under the sign font.
    static let masa = Color(hex: 0xF2B53C)
    /// Text on teal and on masa. The same in light and dark mode.
    static let inkOnColor = Color(hex: 0x102E2A)

    static let background = Color(light: 0xE9F2EE, dark: 0x0F2623)
    static let paper = Color(light: 0xF8FBF9, dark: 0x173632)
    static let ink = Color(light: 0x102E2A, dark: 0xE4EFEB)
    static let inkSoft = Color(light: 0x3D5A55, dark: 0xA9C2BC)
    static let line = Color(light: 0x102E2A, dark: 0x6F8F89)
    static let up = Color(light: 0x2E8B47, dark: 0x4CB86A)
    static let down = Color(light: 0xD2412B, dark: 0xE85A43)

    /// The sign font. It grows with Dynamic Type like the text style it is relative to.
    static func sign(_ size: CGFloat, relativeTo style: Font.TextStyle) -> Font {
        .custom("Bungee-Regular", size: size, relativeTo: style)
    }

    /// Registers the bundled fonts. Call once at app start.
    static func registerFonts() {
        guard let url = Bundle.main.url(forResource: "Bungee-Regular", withExtension: "ttf") else { return }
        CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
    }
}

extension Color {
    init(hex: UInt32) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255
        )
    }

    init(light: UInt32, dark: UInt32) {
        self.init(uiColor: UIColor { traits in
            UIColor(Color(hex: traits.userInterfaceStyle == .dark ? dark : light))
        })
    }
}

/// The main button of the website: a masa capsule with an ink outline.
struct MasaButtonStyle: ButtonStyle {
    @Environment(\.isEnabled) private var isEnabled

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.headline)
            .foregroundStyle(Brand.inkOnColor)
            .padding(.vertical, 15)
            .padding(.horizontal, 24)
            .background(Brand.masa.opacity(configuration.isPressed ? 0.8 : 1), in: .capsule)
            .overlay(Capsule().strokeBorder(Brand.inkOnColor, lineWidth: 2))
            .scaleEffect(configuration.isPressed ? 0.98 : 1)
            .opacity(isEnabled ? 1 : 0.6)
            .animation(.easeOut(duration: 0.12), value: configuration.isPressed)
    }
}
