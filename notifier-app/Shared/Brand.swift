//
//  Brand.swift
//  StatusTacosAlert
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
    /// Silent monitors (checked, no alerts) and warnings.
    static let silent = Color(light: 0xD9821E, dark: 0xF0A04B)
    /// Paused monitors (not checked).
    static let paused = Color(light: 0x8E8E93, dark: 0x98989D)

    /// Environment badges. The dark variants are lighter so they read on a dark background.
    static let environmentProd = Color(light: 0x1F6FD1, dark: 0x5A9CF0)
    static let environmentStage = Color(light: 0x8E44C9, dark: 0xB57EE5)
    static let environmentDev = Color(light: 0x6E6E73, dark: 0xA1A1A6)

    /// The sign font. Only the widget bundles it.
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
