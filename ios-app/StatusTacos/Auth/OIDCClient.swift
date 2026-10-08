import CryptoKit
import Foundation

struct OIDCConfiguration: Sendable, Equatable {
    var issuer: URL
    var clientID: String
    var redirectURI: URL
    /// `offline_access` gives a long-lived refresh token, like the web app.
    var scopes = ["openid", "profile", "email", "offline_access"]

    var callbackScheme: String { redirectURI.scheme ?? "" }
}

struct OIDCDiscovery: Decodable, Sendable, Equatable {
    let authorizationEndpoint: URL
    let tokenEndpoint: URL
    let endSessionEndpoint: URL?

    enum CodingKeys: String, CodingKey {
        case authorizationEndpoint = "authorization_endpoint"
        case tokenEndpoint = "token_endpoint"
        case endSessionEndpoint = "end_session_endpoint"
    }
}

struct TokenSet: Codable, Sendable, Equatable {
    var accessToken: String
    var refreshToken: String?
    var idToken: String?
    var expiresAt: Date

    func expires(within interval: TimeInterval, now: Date = .now) -> Bool {
        expiresAt.timeIntervalSince(now) < interval
    }
}

enum AuthError: LocalizedError, Equatable {
    case discoveryFailed(String)
    case stateMismatch
    case missingCode
    case authorizationDenied(String)
    /// The token endpoint could not answer (network, server error). Tokens may still be valid.
    case tokenRequestFailed(String)
    /// The token endpoint refused the request (for example an unknown client). Tokens are not usable.
    case tokenRequestRejected(String)
    /// The refresh token is not valid any more. The user must sign in again.
    case invalidGrant
    case notSignedIn

    var errorDescription: String? {
        switch self {
        case .discoveryFailed(let reason): "Cannot reach the sign-in server. \(reason)"
        case .stateMismatch: "The sign-in answer does not match the request. Try again."
        case .missingCode: "The sign-in server sent no authorization code."
        case .authorizationDenied(let reason): "Sign-in failed: \(reason)"
        case .tokenRequestFailed(let reason): "Sign-in failed: \(reason)"
        case .tokenRequestRejected(let reason): "The sign-in server refused the request: \(reason)"
        case .invalidGrant: "Your session has expired. Sign in again."
        case .notSignedIn: "You are not signed in."
        }
    }
}

/// Proof Key for Code Exchange (RFC 7636), S256 method.
struct PKCE: Sendable {
    let verifier: String
    let challenge: String

    init(verifier: String = PKCE.randomString()) {
        self.verifier = verifier
        challenge = Data(SHA256.hash(data: Data(verifier.utf8))).base64URLEncodedString()
    }

    /// 32 random bytes, base64url encoded (43 characters).
    static func randomString(byteCount: Int = 32) -> String {
        var generator = SystemRandomNumberGenerator()
        let bytes = (0..<byteCount).map { _ in UInt8.random(in: .min ... .max, using: &generator) }
        return Data(bytes).base64URLEncodedString()
    }
}

/// OpenID Connect authorization code flow with PKCE for a public client (no client secret).
struct OIDCClient: Sendable {
    let configuration: OIDCConfiguration
    var session: URLSession = .shared

    func discover() async throws -> OIDCDiscovery {
        let url = configuration.issuer.appending(path: ".well-known/openid-configuration")
        do {
            let (data, response) = try await session.data(from: url)
            guard (response as? HTTPURLResponse)?.statusCode == 200 else {
                throw AuthError.discoveryFailed("Unexpected answer from \(url.host() ?? "server").")
            }
            return try JSONDecoder().decode(OIDCDiscovery.self, from: data)
        } catch let error as AuthError {
            throw error
        } catch {
            throw AuthError.discoveryFailed(error.localizedDescription)
        }
    }

    func authorizationURL(discovery: OIDCDiscovery, pkce: PKCE, state: String) -> URL {
        var components = URLComponents(url: discovery.authorizationEndpoint, resolvingAgainstBaseURL: false)!
        components.queryItems = (components.queryItems ?? []) + [
            URLQueryItem(name: "client_id", value: configuration.clientID),
            URLQueryItem(name: "redirect_uri", value: configuration.redirectURI.absoluteString),
            URLQueryItem(name: "response_type", value: "code"),
            URLQueryItem(name: "scope", value: configuration.scopes.joined(separator: " ")),
            URLQueryItem(name: "state", value: state),
            URLQueryItem(name: "code_challenge", value: pkce.challenge),
            URLQueryItem(name: "code_challenge_method", value: "S256"),
        ]
        return components.url!
    }

    /// Returns the authorization code of the redirect URL.
    func authorizationCode(from callbackURL: URL, expectedState: String) throws -> String {
        let items = URLComponents(url: callbackURL, resolvingAgainstBaseURL: false)?.queryItems ?? []
        func value(_ name: String) -> String? { items.first { $0.name == name }?.value }

        if let error = value("error") {
            // Form-style query: "+" is a space.
            let description = value("error_description")?.replacingOccurrences(of: "+", with: " ")
            throw AuthError.authorizationDenied(description ?? error)
        }
        guard value("state") == expectedState else { throw AuthError.stateMismatch }
        guard let code = value("code"), !code.isEmpty else { throw AuthError.missingCode }
        return code
    }

    func exchange(code: String, pkce: PKCE, discovery: OIDCDiscovery) async throws -> TokenSet {
        try await tokenRequest(discovery.tokenEndpoint, form: [
            ("grant_type", "authorization_code"),
            ("client_id", configuration.clientID),
            ("code", code),
            ("redirect_uri", configuration.redirectURI.absoluteString),
            ("code_verifier", pkce.verifier),
        ], previousRefreshToken: nil)
    }

    func refresh(_ refreshToken: String, discovery: OIDCDiscovery) async throws -> TokenSet {
        try await tokenRequest(discovery.tokenEndpoint, form: [
            ("grant_type", "refresh_token"),
            ("client_id", configuration.clientID),
            ("refresh_token", refreshToken),
        ], previousRefreshToken: refreshToken)
    }

    /// Ends the session at the identity provider (Keycloak accepts the refresh token). Best effort.
    func endSession(refreshToken: String, discovery: OIDCDiscovery) async {
        guard let endpoint = discovery.endSessionEndpoint else { return }
        var request = URLRequest(url: endpoint)
        request.httpMethod = "POST"
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        request.httpBody = FormEncoding.encode([
            ("client_id", configuration.clientID),
            ("refresh_token", refreshToken),
        ])
        _ = try? await session.data(for: request)
    }

    private func tokenRequest(
        _ endpoint: URL, form: [(String, String)], previousRefreshToken: String?
    ) async throws -> TokenSet {
        var request = URLRequest(url: endpoint)
        request.httpMethod = "POST"
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.httpBody = FormEncoding.encode(form)

        let requestedAt = Date.now
        let (data, response) = try await session.data(for: request)
        let status = (response as? HTTPURLResponse)?.statusCode ?? 0
        guard status == 200 else {
            let error = try? JSONDecoder().decode(OAuthErrorResponse.self, from: data)
            if error?.error == "invalid_grant" { throw AuthError.invalidGrant }
            let reason = error?.errorDescription ?? error?.error ?? "HTTP \(status)"
            // 400 and 401 are OAuth errors (RFC 6749, section 5.2): retrying does not help.
            if status == 400 || status == 401 { throw AuthError.tokenRequestRejected(reason) }
            throw AuthError.tokenRequestFailed(reason)
        }
        let token = try JSONDecoder().decode(TokenResponse.self, from: data)
        return TokenSet(
            accessToken: token.accessToken,
            // A refresh response may leave out the refresh token: then the old one stays valid.
            refreshToken: token.refreshToken ?? previousRefreshToken,
            idToken: token.idToken,
            expiresAt: requestedAt.addingTimeInterval(TimeInterval(token.expiresIn)))
    }
}

private struct TokenResponse: Decodable {
    let accessToken: String
    let refreshToken: String?
    let idToken: String?
    let expiresIn: Int

    enum CodingKeys: String, CodingKey {
        case accessToken = "access_token"
        case refreshToken = "refresh_token"
        case idToken = "id_token"
        case expiresIn = "expires_in"
    }
}

private struct OAuthErrorResponse: Decodable {
    let error: String
    let errorDescription: String?

    enum CodingKeys: String, CodingKey {
        case error
        case errorDescription = "error_description"
    }
}

enum FormEncoding {
    /// Unreserved characters of RFC 3986. Everything else is percent-encoded.
    private static let allowed = CharacterSet(
        charactersIn: "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~")

    static func encode(_ fields: [(String, String)]) -> Data {
        fields
            .map { "\(escape($0.0))=\(escape($0.1))" }
            .joined(separator: "&")
            .data(using: .utf8)!
    }

    private static func escape(_ value: String) -> String {
        value.addingPercentEncoding(withAllowedCharacters: allowed) ?? value
    }
}

/// Name and email of the signed-in user, read from the ID token.
struct Account: Sendable, Equatable {
    let name: String?
    let email: String?

    init(name: String?, email: String?) {
        self.name = name
        self.email = email
    }

    /// Reads the claims of a JWT. The signature is not checked: the token comes straight
    /// from the token endpoint over TLS and is only used for display.
    init?(idToken: String) {
        let parts = idToken.split(separator: ".")
        guard parts.count == 3, let payload = Data(base64URLEncoded: String(parts[1])),
              let claims = try? JSONSerialization.jsonObject(with: payload) as? [String: Any]
        else { return nil }
        name = (claims["name"] as? String) ?? (claims["preferred_username"] as? String)
        email = claims["email"] as? String
    }
}

extension Data {
    func base64URLEncodedString() -> String {
        base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }

    init?(base64URLEncoded text: String) {
        var base64 = text
            .replacingOccurrences(of: "-", with: "+")
            .replacingOccurrences(of: "_", with: "/")
        base64 += String(repeating: "=", count: (4 - base64.count % 4) % 4)
        self.init(base64Encoded: base64)
    }
}
