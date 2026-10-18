//
//  Brand.swift
//  StatusTacosAlertWidget
//

import AppKit
import SwiftUI

/// The look of the Status Tacos website and iOS app: colors from the logo and the Bungee sign font.
enum Brand {
    /// The teal of the logo. The same in light and dark mode.
    static let teal = Color(hex: 0x33ADA4)
    /// The tortilla yellow: the shade under the sign.
    static let masa = Color(hex: 0xF2B53C)
    /// Text and outlines on teal and on masa. The same in light and dark mode.
    static let inkOnColor = Color(hex: 0x102E2A)
    /// Text on the salsa-red sign.
    static let paperOnColor = Color(hex: 0xFFF8EC)

    static let paper = Color(light: 0xF8FBF9, dark: 0x173632)
    static let ink = Color(light: 0x102E2A, dark: 0xE4EFEB)
    static let inkSoft = Color(light: 0x3D5A55, dark: 0xA9C2BC)
    static let up = Color(light: 0x2E8B47, dark: 0x4CB86A)
    static let down = Color(light: 0xD2412B, dark: 0xE85A43)

    static func sign(_ size: CGFloat) -> Font {
        .custom("Bungee-Regular", size: size)
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
        self.init(nsColor: NSColor(name: nil) { appearance in
            let isDark = appearance.bestMatch(from: [.aqua, .darkAqua]) == .darkAqua
            return NSColor(Color(hex: isDark ? dark : light))
        })
    }
}
