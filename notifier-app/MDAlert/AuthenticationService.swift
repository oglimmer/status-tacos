//
//  AuthenticationService.swift
//  MDAlert
//
//  Created by Oliver Zimpasser on 6/26/25.
//

import Foundation
import SwiftUI
import CommonCrypto

struct OIDCConfiguration {
    let issuerURL = "https://id.oglimmer.de/realms/status-tacos"
    let clientID = "notifier-app"
    let clientSecret = "xxx" // TODO: Replace with actual client secret
    let wellKnownURL = "https://id.oglimmer.de/realms/status-tacos/.well-known/openid-configuration"
    let redirectURI = "mdalert://oauth/callback"
    let fallbackRedirectURI = "http://localhost:3000/callback"  // Fallback for testing
}

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

class AuthenticationService: ObservableObject {
    static let shared = AuthenticationService()

    @Published var isAuthenticated = false
    @Published var userInfo: UserInfo?
    @Published var isLoading = false
    @Published var errorMessage: String?

    private let config = OIDCConfiguration()
    private var discoveryDocument: OIDCDiscoveryDocument?
    private var accessToken: String?
    private var refreshToken: String?
    private var pendingCodeVerifier: String?
    private let keychainService = KeychainService.shared

    private init() {
        print("🏗️ AuthenticationService singleton instance \(ObjectIdentifier(self)) created")
        loadDiscoveryDocument()
    }

    private func loadDiscoveryDocument() {
        guard let url = URL(string: config.wellKnownURL) else { return }

        URLSession.shared.dataTask(with: url) { [weak self] data, response, error in
            DispatchQueue.main.async {
                if let error = error {
                    self?.errorMessage = "Failed to load OIDC configuration: \(error.localizedDescription)"
                    return
                }

                guard let data = data else {
                    self?.errorMessage = "No data received from OIDC configuration"
                    return
                }

                do {
                    self?.discoveryDocument = try JSONDecoder().decode(OIDCDiscoveryDocument.self, from: data)
                    print("✅ Discovery document loaded for instance \(ObjectIdentifier(self!))")
                    // Now that discovery document is loaded, check for stored tokens
                    self?.loadStoredTokensAndValidate()
                } catch {
                    self?.errorMessage = "Failed to parse OIDC configuration: \(error.localizedDescription)"
                }
            }
        }.resume()
    }

    private func loadStoredTokensAndValidate() {
        let storedTokens = keychainService.loadStoredTokens()

        guard let accessToken = storedTokens.accessToken else {
            print("📱 No stored access token found")
            return
        }

        print("🔍 Access token found: \(accessToken.prefix(20))...")
        print("🔍 Refresh token found: \(storedTokens.refreshToken?.prefix(20) ?? "none")...")
        print("🔍 Tokens expired: \(storedTokens.isExpired)")

        if storedTokens.isExpired {
            print("⏰ Stored tokens are expired")
            if let refreshToken = storedTokens.refreshToken {
                print("🔄 Attempting to refresh tokens")
                self.refreshToken = refreshToken
                refreshTokens()
            } else {
                print("❌ No refresh token available, clearing stored tokens")
                _ = keychainService.clearAllTokens()
            }
            return
        }

        print("✅ Valid stored tokens found, restoring authentication state")
        self.accessToken = accessToken
        self.refreshToken = storedTokens.refreshToken

        // Fetch user info to complete authentication
        fetchUserInfo()
    }

    func login() {
        guard let discoveryDocument = discoveryDocument else {
            DispatchQueue.main.async {
                self.errorMessage = "OIDC configuration not loaded"
            }
            return
        }

        print("🔐 Starting login flow...")
        DispatchQueue.main.async {
            self.isLoading = true
            self.errorMessage = nil
        }

        // Generate state and code verifier for PKCE
        let state = generateRandomString(length: 32)
        let codeVerifier = generateRandomString(length: 43)
        let codeChallenge = generateCodeChallenge(from: codeVerifier)

        // Store code verifier for later use
        pendingCodeVerifier = codeVerifier
        print("🔑 Code verifier stored for instance \(ObjectIdentifier(self)): \(codeVerifier.prefix(10))...")


        // Build authorization URL
        var components = URLComponents(string: discoveryDocument.authorizationEndpoint)!
        components.queryItems = [
            URLQueryItem(name: "client_id", value: config.clientID),
            URLQueryItem(name: "redirect_uri", value: config.redirectURI),
            URLQueryItem(name: "response_type", value: "code"),
            URLQueryItem(name: "scope", value: "openid profile email"),
            URLQueryItem(name: "state", value: state),
            URLQueryItem(name: "code_challenge", value: codeChallenge),
            URLQueryItem(name: "code_challenge_method", value: "S256")
        ]

        // Open browser
        if let authURL = components.url {
            print("🌐 Opening browser with URL: \(authURL)")
            NSWorkspace.shared.open(authURL)
        } else {
            print("❌ Failed to create authorization URL")
            DispatchQueue.main.async {
                self.errorMessage = "Failed to create authorization URL"
                self.isLoading = false
            }
        }
    }

    func handleOAuthCallback(url: URL) {
        print("📱 Received callback URL: \(url)")

        guard let components = URLComponents(url: url, resolvingAgainstBaseURL: false),
              let queryItems = components.queryItems else {
            print("❌ Failed to parse callback URL components")
            DispatchQueue.main.async {
                self.errorMessage = "Invalid callback URL"
                self.isLoading = false
            }
            return
        }

        print("🔍 Query items: \(queryItems)")

        // Extract authorization code
        guard let code = queryItems.first(where: { $0.name == "code" })?.value else {
            // Check for error
            if let error = queryItems.first(where: { $0.name == "error" })?.value {
                print("❌ Authorization error: \(error)")
                DispatchQueue.main.async {
                    self.errorMessage = "Authorization failed: \(error)"
                    self.isLoading = false
                }
            } else {
                print("❌ No authorization code found in callback")
                DispatchQueue.main.async {
                    self.errorMessage = "No authorization code received"
                    self.isLoading = false
                }
            }
            return
        }

        print("✅ Authorization code received: \(code.prefix(10))...")

        guard let discoveryDocument = discoveryDocument,
              let codeVerifier = pendingCodeVerifier else {
            print("❌ Missing discovery document or code verifier for instance \(ObjectIdentifier(self))")
            print("❌ Discovery document available: \(discoveryDocument != nil)")
            print("❌ Code verifier available: \(pendingCodeVerifier != nil)")
            DispatchQueue.main.async {
                self.errorMessage = "Invalid authorization state"
                self.isLoading = false
            }
            return
        }

        print("🔄 Exchanging authorization code for tokens...")

        // Clean up pending code verifier
        pendingCodeVerifier = nil

        // Exchange authorization code for tokens
        exchangeCodeForTokens(code: code, codeVerifier: codeVerifier, tokenEndpoint: discoveryDocument.tokenEndpoint)
    }

    private func exchangeCodeForTokens(code: String, codeVerifier: String, tokenEndpoint: String) {
        guard let url = URL(string: tokenEndpoint) else {
            print("❌ Invalid token endpoint URL: \(tokenEndpoint)")
            return
        }

        print("🔗 Token endpoint: \(tokenEndpoint)")

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")

        let body = [
            "grant_type": "authorization_code",
            "client_id": config.clientID,
            "client_secret": config.clientSecret,
            "code": code,
            "redirect_uri": config.redirectURI,
            "code_verifier": codeVerifier
        ]

        request.httpBody = body.compactMap { key, value in
            "\(key)=\(value.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? "")"
        }.joined(separator: "&").data(using: .utf8)

        URLSession.shared.dataTask(with: request) { [weak self] data, response, error in
            DispatchQueue.main.async {
                self?.isLoading = false

                if let error = error {
                    print("❌ Token exchange error: \(error.localizedDescription)")
                    self?.errorMessage = "Token exchange failed: \(error.localizedDescription)"
                    return
                }

                if let httpResponse = response as? HTTPURLResponse {
                    print("📡 Token response status: \(httpResponse.statusCode)")
                }

                guard let data = data else {
                    print("❌ No token data received")
                    self?.errorMessage = "No token data received"
                    return
                }

//                if let responseString = String(data: data, encoding: .utf8) {
//                    print("📄 Token response: \(responseString)")
//                }

                do {
                    let tokenResponse = try JSONDecoder().decode(TokenResponse.self, from: data)
                    print("✅ Token exchange successful")

                    // Store tokens securely
                    if self?.keychainService.saveTokens(tokenResponse) == true {
                        print("✅ Tokens saved to keychain")
                    } else {
                        print("⚠️ Failed to save tokens to keychain")
                    }

                    self?.accessToken = tokenResponse.accessToken
                    self?.refreshToken = tokenResponse.refreshToken
                    self?.fetchUserInfo()
                } catch {
                    print("❌ Failed to parse token response: \(error)")
                    self?.errorMessage = "Failed to parse token response: \(error.localizedDescription)"
                }
            }
        }.resume()
    }

    private func fetchUserInfo() {
        guard let discoveryDocument = discoveryDocument,
              let accessToken = accessToken,
              let url = URL(string: discoveryDocument.userinfoEndpoint) else {
            print("❌ Missing required data for user info fetch")
            return
        }

        print("📋 Fetching user info from: \(discoveryDocument.userinfoEndpoint)")

        var request = URLRequest(url: url)
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")

        URLSession.shared.dataTask(with: request) { [weak self] data, response, error in
            DispatchQueue.main.async {
                if let error = error {
                    print("❌ User info fetch error: \(error.localizedDescription)")
                    self?.errorMessage = "Failed to fetch user info: \(error.localizedDescription)"
                    return
                }

                if let httpResponse = response as? HTTPURLResponse {
                    print("📡 User info response status: \(httpResponse.statusCode)")
                }

                guard let data = data else {
                    print("❌ No user info data received")
                    self?.errorMessage = "No user info data received"
                    return
                }

                do {
                    self?.userInfo = try JSONDecoder().decode(UserInfo.self, from: data)
                    print("✅ User info fetched successfully, setting isAuthenticated to true")
                    self?.isAuthenticated = true
                    print("✅ isAuthenticated is now: \(self?.isAuthenticated ?? false)")
                } catch {
                    print("❌ Failed to parse user info: \(error)")
                    self?.errorMessage = "Failed to parse user info: \(error.localizedDescription)"
                }
            }
        }.resume()
    }

    func logout() {
        // Clear tokens from keychain
        if keychainService.clearAllTokens() {
            print("✅ Tokens cleared from keychain")
        } else {
            print("⚠️ Failed to clear tokens from keychain")
        }

        DispatchQueue.main.async {
            self.isAuthenticated = false
            self.userInfo = nil
            self.accessToken = nil
            self.refreshToken = nil
            self.errorMessage = nil
        }
    }

    func getAccessToken() -> String? {
        return accessToken
    }

    // Temporary method for testing - manually paste callback URL
    func handleManualCallback(_ callbackURL: String) {
        guard let url = URL(string: callbackURL) else {
            print("❌ Invalid callback URL string")
            return
        }
        handleOAuthCallback(url: url)
    }

    private func refreshTokens() {
        guard let discoveryDocument = discoveryDocument,
              let refreshToken = refreshToken,
              let url = URL(string: discoveryDocument.tokenEndpoint) else {
            print("❌ Missing required data for token refresh")
            DispatchQueue.main.async {
                self.errorMessage = "Unable to refresh tokens"
            }
            return
        }

        print("🔄 Refreshing tokens...")

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")

        let body = [
            "grant_type": "refresh_token",
            "client_id": config.clientID,
            "client_secret": config.clientSecret,
            "refresh_token": refreshToken
        ]

        request.httpBody = body.compactMap { key, value in
            "\(key)=\(value.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? "")"
        }.joined(separator: "&").data(using: .utf8)

        URLSession.shared.dataTask(with: request) { [weak self] data, response, error in
            DispatchQueue.main.async {
                if let error = error {
                    print("❌ Token refresh error: \(error.localizedDescription)")
                    self?.handleTokenRefreshFailure()
                    return
                }

                guard let data = data else {
                    print("❌ No data received from token refresh")
                    self?.handleTokenRefreshFailure()
                    return
                }

                do {
                    let tokenResponse = try JSONDecoder().decode(TokenResponse.self, from: data)
                    print("✅ Token refresh successful")

                    // Store refreshed tokens securely
                    if self?.keychainService.saveTokens(tokenResponse) == true {
                        print("✅ Refreshed tokens saved to keychain")
                    } else {
                        print("⚠️ Failed to save refreshed tokens to keychain")
                    }

                    self?.accessToken = tokenResponse.accessToken
                    self?.refreshToken = tokenResponse.refreshToken
                    self?.fetchUserInfo()
                } catch {
                    print("❌ Failed to parse token refresh response: \(error)")
                    self?.handleTokenRefreshFailure()
                }
            }
        }.resume()
    }

    private func handleTokenRefreshFailure() {
        print("❌ Token refresh failed, clearing stored tokens")
        _ = keychainService.clearAllTokens()
        DispatchQueue.main.async {
            self.isAuthenticated = false
            self.userInfo = nil
            self.accessToken = nil
            self.refreshToken = nil
            self.errorMessage = "Authentication expired. Please sign in again."
        }
    }

    // MARK: - Helper Methods

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
