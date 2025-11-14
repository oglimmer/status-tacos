//
//  KeychainService.swift
//  MDAlert
//
//  Created by Oliver Zimpasser on 6/26/25.
//

import Foundation
import Security

class KeychainService {
    static let shared = KeychainService()

    private let serviceName = "MDAlert"

    private init() {}

    // MARK: - Generic Keychain Operations

    private func keychainQuery(for key: String) -> [String: Any] {
        return [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceName,
            kSecAttrAccount as String: key,
            kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlockedThisDeviceOnly
        ]
    }

    func save(key: String, data: Data) -> Bool {
        var query = keychainQuery(for: key)
        query[kSecValueData as String] = data

        // Delete existing item first
        SecItemDelete(query as CFDictionary)

        // Add new item
        let status = SecItemAdd(query as CFDictionary, nil)
        return status == errSecSuccess
    }

    func load(key: String) -> Data? {
        var query = keychainQuery(for: key)
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne

        var dataTypeRef: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &dataTypeRef)

        if status == errSecSuccess {
            return dataTypeRef as? Data
        }
        return nil
    }

    func delete(key: String) -> Bool {
        let query = keychainQuery(for: key)
        let status = SecItemDelete(query as CFDictionary)
        return status == errSecSuccess || status == errSecItemNotFound
    }

    // MARK: - Token-specific Operations

    private let accessTokenKey = "access_token"
    private let refreshTokenKey = "refresh_token"
    private let idTokenKey = "id_token"
    private let tokenExpiryKey = "token_expiry"

    func saveTokens(_ tokenResponse: TokenResponse) -> Bool {
        var success = true

        // Save access token
        if let accessTokenData = tokenResponse.accessToken.data(using: .utf8) {
            success = success && save(key: accessTokenKey, data: accessTokenData)
        }

        // Save refresh token if available
        if let refreshToken = tokenResponse.refreshToken,
           let refreshTokenData = refreshToken.data(using: .utf8) {
            success = success && save(key: refreshTokenKey, data: refreshTokenData)
        }

        // Save ID token if available
        if let idToken = tokenResponse.idToken,
           let idTokenData = idToken.data(using: .utf8) {
            success = success && save(key: idTokenKey, data: idTokenData)
        }

        // Calculate and save expiry time
        let expiryTime = Date().addingTimeInterval(TimeInterval(tokenResponse.expiresIn))
        let expiryData = try? JSONEncoder().encode(expiryTime)
        if let expiryData = expiryData {
            success = success && save(key: tokenExpiryKey, data: expiryData)
        }

        return success
    }

    func loadStoredTokens() -> (accessToken: String?, refreshToken: String?, idToken: String?, isExpired: Bool) {
        let accessToken = loadToken(key: accessTokenKey)
        let refreshToken = loadToken(key: refreshTokenKey)
        let idToken = loadToken(key: idTokenKey)

        // Check if tokens are expired
        var isExpired = true
        if let expiryData = load(key: tokenExpiryKey),
           let expiryTime = try? JSONDecoder().decode(Date.self, from: expiryData) {
            let now = Date()
            isExpired = now >= expiryTime
            print("🕐 Token expiry time: \(expiryTime)")
            print("🕐 Current time: \(now)")
            print("🕐 Time until expiry: \(expiryTime.timeIntervalSince(now)) seconds")
        } else {
            print("❌ No token expiry data found or failed to decode")
        }

        return (accessToken, refreshToken, idToken, isExpired)
    }

    private func loadToken(key: String) -> String? {
        guard let tokenData = load(key: key) else { return nil }
        return String(data: tokenData, encoding: .utf8)
    }

    func clearAllTokens() -> Bool {
        var success = true
        success = success && delete(key: accessTokenKey)
        success = success && delete(key: refreshTokenKey)
        success = success && delete(key: idTokenKey)
        success = success && delete(key: tokenExpiryKey)
        return success
    }

    func hasValidTokens() -> Bool {
        let tokens = loadStoredTokens()
        return tokens.accessToken != nil && !tokens.isExpired
    }
}
