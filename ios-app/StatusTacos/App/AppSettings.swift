import Foundation
import Observation

/// Server and sign-in settings. Stored in UserDefaults, so a self-hosted instance can be used.
@MainActor
@Observable
final class AppSettings {
    static let defaultServerURL = URL(string: "https://tacos.oglimmer.com")!
    static let defaultIssuerURL = URL(string: "https://id.oglimmer.de/realms/status-tacos")!
    static let defaultClientID = "status-tacos-ios"
    /// Must be a valid redirect URI of the OIDC client.
    static let redirectURI = URL(string: "de.oglimmer.statustacos:/oauth/callback")!

    private enum Keys {
        static let serverURL = "settings.serverURL"
        static let issuerURL = "settings.issuerURL"
        static let clientID = "settings.clientID"
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

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        serverURL = defaults.string(forKey: Keys.serverURL).flatMap(Self.normalizedURL) ?? Self.defaultServerURL
        issuerURL = defaults.string(forKey: Keys.issuerURL).flatMap(Self.normalizedURL) ?? Self.defaultIssuerURL
        clientID = defaults.string(forKey: Keys.clientID).flatMap(Self.normalizedClientID) ?? Self.defaultClientID
    }

    var apiBaseURL: URL { serverURL.appending(path: "api/v1") }

    var oidcConfiguration: OIDCConfiguration {
        OIDCConfiguration(issuer: issuerURL, clientID: clientID, redirectURI: Self.redirectURI)
    }

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

    nonisolated static func normalizedClientID(_ text: String) -> String? {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }
}
