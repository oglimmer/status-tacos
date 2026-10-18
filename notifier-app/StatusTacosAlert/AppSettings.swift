//
//  AppSettings.swift
//  StatusTacosAlert
//

import Foundation
import Combine

/// Server and sign-in settings. Stored in UserDefaults, so a self-hosted instance can be used.
final class AppSettings: ObservableObject {
    static let shared = AppSettings()

    static let defaultServerURL = URL(string: "https://tacos.oglimmer.com")!
    static let defaultIssuerURL = URL(string: "https://id.oglimmer.de/realms/status-tacos")!
    static let defaultClientID = "notifier-app"
    /// Must be a valid redirect URI of the OIDC client. The scheme is registered in Info.plist.
    static let redirectURI = "de.oglimmer.statustacos.alert:/oauth/callback"
    static let redirectScheme = "de.oglimmer.statustacos.alert"

    private enum Keys {
        static let serverURL = "settings.serverURL"
        static let issuerURL = "settings.issuerURL"
        static let clientID = "settings.clientID"
    }

    private let defaults: UserDefaults

    /// Base URL of the web app. The API is at `<server>/api/v1`.
    @Published var serverURL: URL {
        didSet { defaults.set(serverURL.absoluteString, forKey: Keys.serverURL) }
    }

    @Published var issuerURL: URL {
        didSet { defaults.set(issuerURL.absoluteString, forKey: Keys.issuerURL) }
    }

    @Published var clientID: String {
        didSet { defaults.set(clientID, forKey: Keys.clientID) }
    }

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        serverURL = defaults.string(forKey: Keys.serverURL).flatMap(Self.normalizedURL) ?? Self.defaultServerURL
        issuerURL = defaults.string(forKey: Keys.issuerURL).flatMap(Self.normalizedURL) ?? Self.defaultIssuerURL
        clientID = defaults.string(forKey: Keys.clientID).flatMap(Self.normalizedClientID) ?? Self.defaultClientID
    }

    var apiBaseURL: URL { serverURL.appending(path: "api/v1") }

    var monitorsPageURL: URL { serverURL.appending(path: "monitors") }

    /// Accepts "https://host[:port][/path]" and "http://…". Removes a trailing slash.
    /// Returns nil for anything else.
    static func normalizedURL(_ text: String) -> URL? {
        var trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        while trimmed.hasSuffix("/") { trimmed.removeLast() }
        guard let components = URLComponents(string: trimmed),
              let scheme = components.scheme?.lowercased(), ["http", "https"].contains(scheme),
              let host = components.host, !host.isEmpty,
              components.query == nil, components.fragment == nil
        else { return nil }
        return components.url
    }

    static func normalizedClientID(_ text: String) -> String? {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }
}
