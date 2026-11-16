//
//  KeychainService.swift
//  StatusTacosAlert
//
//  Created by Oliver Zimpasser on 6/26/25.
//

import Foundation
import Security

/// Stores the OAuth tokens in the login keychain.
///
/// All tokens live in ONE keychain item. macOS asks for the keychain password
/// once per item, so one item means at most one prompt. The item is updated in
/// place (never deleted and re-added), which keeps the "Always Allow" choice of
/// the user in the item's access list.
class KeychainService {
    static let shared = KeychainService()

    private let serviceName = "Status Tacos Alert"
    private let bundleKey = "tokens"
    /// Keys of the old layout (one item per value), read once for migration.
    private let legacyKeys = ["access_token", "refresh_token", "id_token", "token_expiry"]

    private struct StoredTokens: Codable {
        var accessToken: String?
        var refreshToken: String?
        var idToken: String?
        var expiry: Date?
    }

    /// Cache, so one app run reads the keychain at most once.
    private var cache: StoredTokens?
    private var cacheLoaded = false

    private init() {}

    // MARK: - Generic Keychain Operations

    private func baseQuery(for key: String) -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceName,
            kSecAttrAccount as String: key
        ]
    }

    private func save(key: String, data: Data) -> Bool {
        let query = baseQuery(for: key)
        let status = SecItemUpdate(query as CFDictionary, [kSecValueData as String: data] as CFDictionary)
        if status == errSecItemNotFound {
            var add = query
            add[kSecValueData as String] = data
            add[kSecAttrAccessible as String] = kSecAttrAccessibleWhenUnlockedThisDeviceOnly
            return SecItemAdd(add as CFDictionary, nil) == errSecSuccess
        }
        return status == errSecSuccess
    }

    private func load(key: String, allowUI: Bool = true) -> Data? {
        var query = baseQuery(for: key)
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        if !allowUI {
            query[kSecUseAuthenticationUI as String] = kSecUseAuthenticationUIFail
        }
        var result: AnyObject?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess else { return nil }
        return result as? Data
    }

    private func delete(key: String) -> Bool {
        let status = SecItemDelete(baseQuery(for: key) as CFDictionary)
        return status == errSecSuccess || status == errSecItemNotFound
    }

    // MARK: - Token bundle

    private func readTokens() -> StoredTokens? {
        if cacheLoaded { return cache }
        cacheLoaded = true
        if let data = load(key: bundleKey),
           let tokens = try? JSONDecoder().decode(StoredTokens.self, from: data) {
            cache = tokens
        } else {
            cache = migrateLegacyTokens()
        }
        return cache
    }

    private func writeTokens(_ tokens: StoredTokens) -> Bool {
        guard let data = try? JSONEncoder().encode(tokens) else { return false }
        cache = tokens
        cacheLoaded = true
        return save(key: bundleKey, data: data)
    }

    /// Moves tokens from the old 4-item layout into the single item.
    /// Reads without UI: if macOS would prompt, it is skipped and the user signs in again.
    private func migrateLegacyTokens() -> StoredTokens? {
        func string(_ key: String) -> String? {
            load(key: key, allowUI: false).flatMap { String(data: $0, encoding: .utf8) }
        }
        let tokens = StoredTokens(
            accessToken: string("access_token"),
            refreshToken: string("refresh_token"),
            idToken: string("id_token"),
            expiry: load(key: "token_expiry", allowUI: false).flatMap { try? JSONDecoder().decode(Date.self, from: $0) }
        )
        guard tokens.accessToken != nil || tokens.refreshToken != nil else { return nil }
        if writeTokens(tokens) {
            legacyKeys.forEach { _ = delete(key: $0) }
        }
        return tokens
    }

    // MARK: - Token-specific Operations

    func saveTokens(_ tokenResponse: TokenResponse) -> Bool {
        let previous = readTokens()
        let tokens = StoredTokens(
            accessToken: tokenResponse.accessToken,
            refreshToken: tokenResponse.refreshToken ?? previous?.refreshToken,
            idToken: tokenResponse.idToken ?? previous?.idToken,
            expiry: Date().addingTimeInterval(TimeInterval(tokenResponse.expiresIn))
        )
        return writeTokens(tokens)
    }

    func loadStoredTokens() -> (accessToken: String?, refreshToken: String?, idToken: String?, expiry: Date?, isExpired: Bool) {
        let tokens = readTokens()
        let expiry = tokens?.expiry
        let isExpired = expiry.map { Date() >= $0 } ?? true
        return (tokens?.accessToken, tokens?.refreshToken, tokens?.idToken, expiry, isExpired)
    }

    func clearAllTokens() -> Bool {
        cache = nil
        cacheLoaded = true
        var success = delete(key: bundleKey)
        for key in legacyKeys {
            success = delete(key: key) && success
        }
        return success
    }

    func hasValidTokens() -> Bool {
        let tokens = loadStoredTokens()
        return tokens.accessToken != nil && !tokens.isExpired
    }
}
