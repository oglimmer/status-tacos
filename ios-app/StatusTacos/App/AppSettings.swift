import Foundation
import Observation
import SwiftUI

/// Server, sign-in and appearance settings. Stored in UserDefaults, so a self-hosted instance can be used.
@MainActor
@Observable
final class AppSettings {
    static let defaultServerURL = URL(string: "https://tacos.oglimmer.com")!
    static let defaultIssuerURL = URL(string: "https://id.oglimmer.de/realms/status-tacos")!
    static let defaultClientID = "status-tacos-ios"
    /// Must be a valid redirect URI of the OIDC client.
    static let redirectURI = URL(string: "de.oglimmer.statustacos:/oauth/callback")!
    /// `Brand.teal`.
    static let defaultAccentColorHex = "33ADA4"

    private enum Keys {
        static let serverURL = "settings.serverURL"
        static let issuerURL = "settings.issuerURL"
        static let clientID = "settings.clientID"
        static let accentColorHex = "settings.accentColorHex"
    }

    @ObservationIgnored private let defaults: UserDefaults

    /// Base URL of the web app. The API is at `<server>/api/v1`.
    var serverURL: URL {
        didSet { defaults.set(serverURL.absoluteString, forKey: Keys.serverURL) }
    }

    var issuerURL: URL {
        didSet { defaults.set(issuerURL.absoluteString, forKey: Keys.issuerURL) }
    }

    var clientID: String {
        didSet { defaults.set(clientID, forKey: Keys.clientID) }
    }

    /// Color of the tenant selector in the monitor list, as `RRGGBB` (uppercase, no `#`).
    /// Use `setAccentColor(hex:)` to set user input: it checks and normalizes it.
    private(set) var accentColorHex: String {
        didSet { defaults.set(accentColorHex, forKey: Keys.accentColorHex) }
    }

    var accentColor: Color { Self.color(hex: accentColorHex) ?? Brand.teal }

    /// Stores the color when `normalizedHex` accepts the text.
    /// - Returns: false for invalid input; the stored color stays.
    @discardableResult
    func setAccentColor(hex text: String) -> Bool {
        guard let normalized = Self.normalizedHex(text) else { return false }
        if normalized != accentColorHex { accentColorHex = normalized }
        return true
    }

    func resetAccentColor() {
        accentColorHex = Self.defaultAccentColorHex
    }

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        serverURL = defaults.string(forKey: Keys.serverURL).flatMap(Self.normalizedURL) ?? Self.defaultServerURL
        issuerURL = defaults.string(forKey: Keys.issuerURL).flatMap(Self.normalizedURL) ?? Self.defaultIssuerURL
        clientID = defaults.string(forKey: Keys.clientID).flatMap(Self.normalizedClientID) ?? Self.defaultClientID
        accentColorHex = defaults.string(forKey: Keys.accentColorHex).flatMap(Self.normalizedHex)
            ?? Self.defaultAccentColorHex
    }

    var apiBaseURL: URL { serverURL.appending(path: "api/v1") }

    var oidcConfiguration: OIDCConfiguration {
        OIDCConfiguration(issuer: issuerURL, clientID: clientID, redirectURI: Self.redirectURI)
    }

    /// Resets the connection. The appearance stays.
    func resetToDefaults() {
        serverURL = Self.defaultServerURL
        issuerURL = Self.defaultIssuerURL
        clientID = Self.defaultClientID
    }

    /// Accepts "https://host[:port][/path]" and "http://…". Removes a trailing slash.
    /// Returns nil for anything else.
    nonisolated static func normalizedURL(_ text: String) -> URL? {
        var trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        while trimmed.hasSuffix("/") { trimmed.removeLast() }
        guard let components = URLComponents(string: trimmed),
              let scheme = components.scheme?.lowercased(), ["http", "https"].contains(scheme),
              let host = components.host, !host.isEmpty,
              components.query == nil, components.fragment == nil
        else { return nil }
        return components.url
    }

    /// Accepts "RRGGBB" and "#RRGGBB" in any case, with spaces around. Returns "RRGGBB" in
    /// uppercase, nil for anything else.
    nonisolated static func normalizedHex(_ text: String) -> String? {
        var trimmed = Substring(text.trimmingCharacters(in: .whitespacesAndNewlines))
        if trimmed.hasPrefix("#") { trimmed = trimmed.dropFirst() }
        guard trimmed.count == 6, trimmed.allSatisfy(\.isHexDigit), trimmed.allSatisfy(\.isASCII) else { return nil }
        return trimmed.uppercased()
    }

    /// The color of a hex value that `normalizedHex` accepts.
    nonisolated static func color(hex text: String) -> Color? {
        guard let hex = normalizedHex(text), let value = UInt32(hex, radix: 16) else { return nil }
        return Color(hex: value)
    }

    nonisolated static func normalizedClientID(_ text: String) -> String? {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }
}
