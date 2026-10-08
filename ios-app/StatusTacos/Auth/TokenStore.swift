import Foundation
import Security

/// Keeps the tokens in the Keychain.
protocol TokenStoring: Sendable {
    func load() -> TokenSet?
    func save(_ tokens: TokenSet)
    func clear()
}

struct KeychainTokenStore: TokenStoring {
    var service = "de.oglimmer.statustacos"
    var account = "oidc-tokens"

    private var query: [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
    }

    func load() -> TokenSet? {
        var request = query
        request[kSecReturnData as String] = true
        request[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: AnyObject?
        guard SecItemCopyMatching(request as CFDictionary, &result) == errSecSuccess,
              let data = result as? Data
        else { return nil }
        return try? JSONDecoder().decode(TokenSet.self, from: data)
    }

    func save(_ tokens: TokenSet) {
        guard let data = try? JSONEncoder().encode(tokens) else { return }
        let attributes: [String: Any] = [
            kSecValueData as String: data,
            // Background refreshes can read the tokens after the first unlock.
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
        ]
        let status = SecItemUpdate(query as CFDictionary, attributes as CFDictionary)
        if status == errSecItemNotFound {
            SecItemAdd(query.merging(attributes) { $1 } as CFDictionary, nil)
        }
    }

    func clear() {
        SecItemDelete(query as CFDictionary)
    }
}
