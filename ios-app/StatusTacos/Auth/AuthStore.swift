import AuthenticationServices
import Foundation
import Observation

/// Sign-in state. Gives the API client a valid access token and refreshes it when needed.
@MainActor
@Observable
final class AuthStore {
    enum Phase: Equatable {
        case signedOut
        case signingIn
        case signedIn
    }

    private(set) var phase: Phase
    private(set) var account: Account?
    /// Why the last sign-in failed or why the user was signed out.
    var errorMessage: String?
    /// A neutral message for the sign-in screen, for example after the account was deleted.
    private(set) var noticeMessage: String?

    @ObservationIgnored private let settings: AppSettings
    @ObservationIgnored private let tokenStore: TokenStoring
    @ObservationIgnored private let session: URLSession
    @ObservationIgnored private var tokens: TokenSet?
    @ObservationIgnored private var cachedDiscovery: (configuration: OIDCConfiguration, discovery: OIDCDiscovery)?
    @ObservationIgnored private var refreshTask: Task<TokenSet, Error>?

    init(settings: AppSettings, tokenStore: TokenStoring = KeychainTokenStore(), session: URLSession = .shared) {
        self.settings = settings
        self.tokenStore = tokenStore
        self.session = session
        tokens = tokenStore.load()
        account = tokens?.idToken.flatMap(Account.init(idToken:))
        // An expired access token is fine: the refresh token renews it on the first request.
        phase = tokens == nil ? .signedOut : .signedIn
    }

    private var client: OIDCClient {
        OIDCClient(configuration: settings.oidcConfiguration, session: session)
    }

    /// Runs the authorization code flow.
    /// - Parameter authenticate: Shows the sign-in page and returns the redirect URL.
    func signIn(authenticate: @MainActor (_ url: URL, _ callbackScheme: String) async throws -> URL) async {
        guard phase == .signedOut else { return }
        phase = .signingIn
        errorMessage = nil
        noticeMessage = nil
        let client = self.client
        do {
            let discovery = try await discovery(for: client)
            let pkce = PKCE()
            let state = PKCE.randomString(byteCount: 16)
            let url = client.authorizationURL(discovery: discovery, pkce: pkce, state: state)
            let callbackURL = try await authenticate(url, client.configuration.callbackScheme)
            let code = try client.authorizationCode(from: callbackURL, expectedState: state)
            store(try await client.exchange(code: code, pkce: pkce, discovery: discovery))
            phase = .signedIn
        } catch {
            phase = .signedOut
            if !Self.isCancellation(error) {
                errorMessage = error.localizedDescription
            }
        }
    }

    /// Signs out here and ends the session at the identity provider.
    func signOut() async {
        let refreshToken = tokens?.refreshToken
        let client = self.client
        endLocalSession(message: nil)
        if let refreshToken, let discovery = try? await discovery(for: client) {
            await client.endSession(refreshToken: refreshToken, discovery: discovery)
        }
    }

    /// Signs out without asking the identity provider, for example when the session has expired.
    func endLocalSession(message: String?) {
        refreshTask?.cancel()
        refreshTask = nil
        tokens = nil
        account = nil
        tokenStore.clear()
        phase = .signedOut
        errorMessage = message
    }

    /// After the server deleted the account. No logout at the identity provider: the user and its
    /// sessions are gone there already, or (without admin access on the server) must stay usable.
    func accountWasDeleted(loginAccountDeleted: Bool) {
        endLocalSession(message: nil)
        noticeMessage = loginAccountDeleted
            ? "Your account and all its data are deleted."
            : "Your Status Tacos data is deleted. Your login account at the identity provider still exists."
    }

    /// A valid access token. Refreshes it when it expires within 30 seconds or when forced.
    func accessToken(forceRefresh: Bool = false) async throws -> String {
        guard let tokens else { throw AuthError.notSignedIn }
        if !forceRefresh, !tokens.expires(within: 30) {
            return tokens.accessToken
        }
        return try await refreshedTokens().accessToken
    }

    private func refreshedTokens() async throws -> TokenSet {
        // All requests that need a new token wait for the same refresh.
        if let refreshTask {
            return try await refreshTask.value
        }
        guard let refreshToken = tokens?.refreshToken else {
            endLocalSession(message: AuthError.invalidGrant.errorDescription)
            throw AuthError.invalidGrant
        }
        let client = self.client
        let task = Task {
            let discovery = try await self.discovery(for: client)
            return try await client.refresh(refreshToken, discovery: discovery)
        }
        refreshTask = task
        defer { refreshTask = nil }

        do {
            let refreshed = try await task.value
            // The user can sign out while the refresh runs.
            guard tokens?.refreshToken == refreshToken else { throw AuthError.notSignedIn }
            store(refreshed)
            return refreshed
        } catch let error as AuthError where error.endsSession {
            // The tokens cannot be renewed: go back to the sign-in screen instead of failing forever.
            endLocalSession(message: error.errorDescription)
            throw error
        }
    }

    private func store(_ newTokens: TokenSet) {
        tokens = newTokens
        tokenStore.save(newTokens)
        if let account = newTokens.idToken.flatMap(Account.init(idToken:)) {
            self.account = account
        }
    }

    private func discovery(for client: OIDCClient) async throws -> OIDCDiscovery {
        if let cachedDiscovery, cachedDiscovery.configuration == client.configuration {
            return cachedDiscovery.discovery
        }
        let discovery = try await client.discover()
        cachedDiscovery = (client.configuration, discovery)
        return discovery
    }

    /// The Keychain keeps items when the app is deleted. A new install starts signed out.
    static func clearTokensAfterInstall(_ tokenStore: TokenStoring, defaults: UserDefaults = .standard) {
        let key = "auth.hasLaunchedBefore"
        guard !defaults.bool(forKey: key) else { return }
        tokenStore.clear()
        defaults.set(true, forKey: key)
    }

    private static func isCancellation(_ error: Error) -> Bool {
        if error is CancellationError { return true }
        if let error = error as? ASWebAuthenticationSessionError, error.code == .canceledLogin { return true }
        return false
    }
}

private extension AuthError {
    var endsSession: Bool {
        switch self {
        case .invalidGrant, .tokenRequestRejected: true
        default: false
        }
    }
}
