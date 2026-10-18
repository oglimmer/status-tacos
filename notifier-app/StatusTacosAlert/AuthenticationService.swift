//
//  AuthenticationService.swift
//  StatusTacosAlert
//
//  Created by Oliver Zimpasser on 6/26/25.
//

import Foundation
import SwiftUI
import CommonCrypto

struct OIDCDiscoveryDocument: Codable {
    let authorizationEndpoint: String
    let tokenEndpoint: String
    let userinfoEndpoint: String

    enum CodingKeys: String, CodingKey {
        case authorizationEndpoint = "authorization_endpoint"
        case tokenEndpoint = "token_endpoint"
        case userinfoEndpoint = "userinfo_endpoint"
    }
}

struct TokenResponse: Codable {
    let accessToken: String
    let refreshToken: String?
    let idToken: String?
    let tokenType: String
    let expiresIn: Int

    enum CodingKeys: String, CodingKey {
        case accessToken = "access_token"
        case refreshToken = "refresh_token"
        case idToken = "id_token"
        case tokenType = "token_type"
        case expiresIn = "expires_in"
    }
}

struct UserInfo: Codable {
    let sub: String
    let email: String?
    let name: String?
    let preferredUsername: String?

    enum CodingKeys: String, CodingKey {
        case sub
        case email
        case name
        case preferredUsername = "preferred_username"
    }
}

enum AuthError: LocalizedError {
    case notSignedIn
    case sessionExpired
    case invalidEndpoint
    case http(Int)

    var errorDescription: String? {
        switch self {
        case .notSignedIn: return "Not signed in"
        case .sessionExpired: return "Your session has expired. Please sign in again."
        case .invalidEndpoint: return "The sign-in service sent an invalid address"
        case .http(let status): return "The sign-in service answered with HTTP \(status)"
        }
    }
}

@MainActor
class AuthenticationService: ObservableObject {
    static let shared = AuthenticationService()

    @Published private(set) var isAuthenticated = false
    @Published var userInfo: UserInfo?
    @Published var isLoading = false
    @Published var errorMessage: String?

    private let settings = AppSettings.shared
    private let keychainService = KeychainService.shared
    private var cachedDiscovery: (issuer: URL, document: OIDCDiscoveryDocument)?
    private var accessToken: String?
    private var accessTokenExpiry: Date?
    private var refreshToken: String?
    private var pendingLogin: (state: String, codeVerifier: String)?
    private var refreshTask: Task<TokenResponse, Error>?

    private init() {
        restoreSession()
    }

    /// Uses the tokens from the Keychain. They are refreshed on first use, so a network
    /// problem at launch does not sign the user out.
    private func restoreSession() {
        let storedTokens = keychainService.loadStoredTokens()

        guard let accessToken = storedTokens.accessToken else {
            print("📱 No stored access token found")
            return
        }

        if storedTokens.isExpired && storedTokens.refreshToken == nil {
            print("❌ Stored tokens are expired and there is no refresh token, clearing stored tokens")
            _ = keychainService.clearAllTokens()
            return
        }

        print("✅ Stored tokens found, restoring authentication state")
        self.accessToken = accessToken
        self.accessTokenExpiry = storedTokens.expiry
        self.refreshToken = storedTokens.refreshToken
        isAuthenticated = true

        Task { await fetchUserInfo() }
    }

    private func discovery() async throws -> OIDCDiscoveryDocument {
        let issuer = settings.issuerURL
        if let cachedDiscovery, cachedDiscovery.issuer == issuer {
            return cachedDiscovery.document
        }
        let url = issuer.appending(path: ".well-known/openid-configuration")
        let document = try JSONDecoder().decode(OIDCDiscoveryDocument.self, from: try await Self.data(for: URLRequest(url: url)))
        cachedDiscovery = (issuer, document)
        return document
    }

    func login() {
        print("🔐 Starting login flow...")
        isLoading = true
        errorMessage = nil

        Task {
            do {
                let discoveryDocument = try await discovery()

                // State protects the callback against forged requests, the code verifier is for PKCE.
                let state = generateRandomString(length: 32)
                let codeVerifier = generateRandomString(length: 64)
                pendingLogin = (state, codeVerifier)

                guard var components = URLComponents(string: discoveryDocument.authorizationEndpoint) else {
                    throw AuthError.invalidEndpoint
                }
                components.queryItems = [
                    URLQueryItem(name: "client_id", value: settings.clientID),
                    URLQueryItem(name: "redirect_uri", value: AppSettings.redirectURI),
                    URLQueryItem(name: "response_type", value: "code"),
                    URLQueryItem(name: "scope", value: "openid profile email"),
                    URLQueryItem(name: "state", value: state),
                    URLQueryItem(name: "code_challenge", value: generateCodeChallenge(from: codeVerifier)),
                    URLQueryItem(name: "code_challenge_method", value: "S256")
                ]
                guard let authURL = components.url else { throw AuthError.invalidEndpoint }

                print("🌐 Opening browser for sign-in")
                NSWorkspace.shared.open(authURL)
            } catch {
                failLogin("Failed to start sign-in: \(error.localizedDescription)")
            }
        }
    }

    func handleOAuthCallback(url: URL) {
        let queryItems = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems ?? []
        func value(_ name: String) -> String? { queryItems.first { $0.name == name }?.value }

        guard let pendingLogin else {
            failLogin("No sign-in is in progress. Please try again.")
            return
        }
        guard value("state") == pendingLogin.state else {
            failLogin("Invalid sign-in state. Please try again.")
            return
        }
        self.pendingLogin = nil

        if let error = value("error") {
            failLogin("Authorization failed: \(error)")
            return
        }
        guard let code = value("code") else {
            failLogin("No authorization code received")
            return
        }

        print("🔄 Exchanging authorization code for tokens...")
        Task {
            do {
                let tokenResponse = try await tokenRequest([
                    ("grant_type", "authorization_code"),
                    ("client_id", settings.clientID),
                    ("code", code),
                    ("redirect_uri", AppSettings.redirectURI),
                    ("code_verifier", pendingLogin.codeVerifier)
                ])
                print("✅ Token exchange successful")
                store(tokenResponse)
                isLoading = false
                isAuthenticated = true
                await fetchUserInfo()
            } catch {
                failLogin("Token exchange failed: \(error.localizedDescription)")
            }
        }
    }

    private func failLogin(_ message: String) {
        print("❌ \(message)")
        errorMessage = message
        isLoading = false
    }

    /// A valid access token. Refreshes it when it expires within 30 seconds or when forced.
    func validAccessToken(forceRefresh: Bool = false) async throws -> String {
        guard let accessToken else { throw AuthError.notSignedIn }
        if !forceRefresh, let accessTokenExpiry, accessTokenExpiry.timeIntervalSinceNow > 30 {
            return accessToken
        }
        return try await refreshedTokens().accessToken
    }

    private func refreshedTokens() async throws -> TokenResponse {
        // All callers that need a new token wait for the same refresh.
        if let refreshTask {
            return try await refreshTask.value
        }
        guard let refreshToken else {
            endSession(message: AuthError.sessionExpired.errorDescription)
            throw AuthError.sessionExpired
        }

        print("🔄 Refreshing tokens...")
        let task = Task {
            try await self.tokenRequest([
                ("grant_type", "refresh_token"),
                ("client_id", self.settings.clientID),
                ("refresh_token", refreshToken)
            ])
        }
        refreshTask = task
        defer { refreshTask = nil }

        do {
            let tokenResponse = try await task.value
            // The user can sign out while the refresh runs.
            guard self.refreshToken == refreshToken else { throw AuthError.notSignedIn }
            print("✅ Token refresh successful")
            store(tokenResponse)
            return tokenResponse
        } catch AuthError.http(let status) where status == 400 || status == 401 {
            // The refresh token is no longer valid. A network error keeps the session for the next try.
            endSession(message: AuthError.sessionExpired.errorDescription)
            throw AuthError.sessionExpired
        }
    }

    private func store(_ tokenResponse: TokenResponse) {
        if !keychainService.saveTokens(tokenResponse) {
            print("⚠️ Failed to save tokens to keychain")
        }
        accessToken = tokenResponse.accessToken
        accessTokenExpiry = Date().addingTimeInterval(TimeInterval(tokenResponse.expiresIn))
        if let newRefreshToken = tokenResponse.refreshToken {
            refreshToken = newRefreshToken
        }
    }

    private func tokenRequest(_ fields: [(String, String)]) async throws -> TokenResponse {
        guard let url = URL(string: try await discovery().tokenEndpoint) else { throw AuthError.invalidEndpoint }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        request.httpBody = Self.formEncoded(fields)

        return try JSONDecoder().decode(TokenResponse.self, from: try await Self.data(for: request))
    }

    private func fetchUserInfo() async {
        do {
            guard let url = URL(string: try await discovery().userinfoEndpoint) else { throw AuthError.invalidEndpoint }
            var request = URLRequest(url: url)
            request.setValue("Bearer \(try await validAccessToken())", forHTTPHeaderField: "Authorization")
            userInfo = try JSONDecoder().decode(UserInfo.self, from: try await Self.data(for: request))
            print("✅ User info fetched successfully")
        } catch {
            print("❌ Failed to fetch user info: \(error.localizedDescription)")
        }
    }

    func logout() {
        endSession(message: nil)
    }

    /// Clears the tokens and goes back to the sign-in screen.
    func endSession(message: String?) {
        if !keychainService.clearAllTokens() {
            print("⚠️ Failed to clear tokens from keychain")
        }
        accessToken = nil
        accessTokenExpiry = nil
        refreshToken = nil
        userInfo = nil
        isAuthenticated = false
        errorMessage = message
    }

    // MARK: - Helper Methods

    private static func data(for request: URLRequest) async throws -> Data {
        let (data, response) = try await URLSession.shared.data(for: request)
        let status = (response as? HTTPURLResponse)?.statusCode ?? 0
        guard (200..<300).contains(status) else { throw AuthError.http(status) }
        return data
    }

    /// application/x-www-form-urlencoded. Only unreserved characters stay as they are.
    private static func formEncoded(_ fields: [(String, String)]) -> Data {
        let unreserved = CharacterSet(charactersIn: "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~")
        func escape(_ value: String) -> String {
            value.addingPercentEncoding(withAllowedCharacters: unreserved) ?? ""
        }
        return Data(fields.map { "\(escape($0.0))=\(escape($0.1))" }.joined(separator: "&").utf8)
    }

    private func generateRandomString(length: Int) -> String {
        let letters = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return String((0..<length).map { _ in letters.randomElement()! })
    }

    private func generateCodeChallenge(from verifier: String) -> String {
        let data = Data(verifier.utf8)
        let hash = data.withUnsafeBytes { bytes in
            var digest = Data(count: Int(CC_SHA256_DIGEST_LENGTH))
            _ = digest.withUnsafeMutableBytes { digestBytes in
                CC_SHA256(bytes.bindMemory(to: UInt8.self).baseAddress, CC_LONG(data.count), digestBytes.bindMemory(to: UInt8.self).baseAddress)
            }
            return digest
        }
        return hash.base64URLEncodedString()
    }
}


// MARK: - Data Extensions

extension Data {
    func base64URLEncodedString() -> String {
        return base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }
}
