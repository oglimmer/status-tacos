import Foundation
import Testing
@testable import StatusTacos

/// Answers requests of a test URLSession with a handler.
final class StubURLProtocol: URLProtocol {
    typealias Handler = @Sendable (URLRequest) throws -> (Int, String)
    nonisolated(unsafe) static var handler: Handler?

    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        do {
            guard let handler = Self.handler else { throw URLError(.notConnectedToInternet) }
            let (status, body) = try handler(request)
            let response = HTTPURLResponse(url: request.url!, statusCode: status, httpVersion: nil, headerFields: nil)!
            client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
            client?.urlProtocol(self, didLoad: Data(body.utf8))
            client?.urlProtocolDidFinishLoading(self)
        } catch {
            client?.urlProtocol(self, didFailWithError: error)
        }
    }

    override func stopLoading() {}

    static func session() -> URLSession {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [StubURLProtocol.self]
        return URLSession(configuration: configuration)
    }
}

final class MemoryTokenStore: TokenStoring, @unchecked Sendable {
    private let lock = NSLock()
    private var tokens: TokenSet?

    init(_ tokens: TokenSet?) { self.tokens = tokens }

    func load() -> TokenSet? { lock.withLock { tokens } }
    func save(_ tokens: TokenSet) { lock.withLock { self.tokens = tokens } }
    func clear() { lock.withLock { tokens = nil } }
}

private let discoveryJSON = """
{"authorization_endpoint":"https://id.example.com/auth","token_endpoint":"https://id.example.com/token"}
"""

private func expiredTokens() -> TokenSet {
    TokenSet(accessToken: "old", refreshToken: "refresh", idToken: nil, expiresAt: .distantPast)
}

/// Routes the requests of the identity provider and the API.
private func route(token: @escaping @Sendable () -> (Int, String),
                   api: @escaping @Sendable (URLRequest) -> (Int, String) = { _ in (200, "[]") }) -> StubURLProtocol.Handler {
    { request in
        let path = request.url!.path()
        if path.hasSuffix("openid-configuration") { return (200, discoveryJSON) }
        if path == "/token" { return token() }
        return api(request)
    }
}

@MainActor
@Suite(.serialized)
struct SessionTests {
    private let settings: AppSettings
    private let session = StubURLProtocol.session()

    init() {
        settings = AppSettings(defaults: UserDefaults(suiteName: "test.\(UUID().uuidString)")!)
        settings.issuerURL = URL(string: "https://id.example.com/realms/r")!
    }

    @Test func rejectedRefreshSignsOut() async {
        // The case of a client that does not exist in Keycloak.
        StubURLProtocol.handler = route(token: { (401, #"{"error":"unauthorized_client","error_description":"Invalid client or Invalid client credentials"}"#) })
        let store = MemoryTokenStore(expiredTokens())
        let auth = AuthStore(settings: settings, tokenStore: store, session: session)
        #expect(auth.phase == .signedIn)

        await #expect(throws: AuthError.tokenRequestRejected("Invalid client or Invalid client credentials")) {
            try await auth.accessToken()
        }
        #expect(auth.phase == .signedOut)
        #expect(auth.errorMessage?.contains("Invalid client") == true)
        #expect(store.load() == nil)
    }

    @Test func expiredRefreshTokenSignsOut() async {
        StubURLProtocol.handler = route(token: { (400, #"{"error":"invalid_grant","error_description":"Token is not active"}"#) })
        let auth = AuthStore(settings: settings, tokenStore: MemoryTokenStore(expiredTokens()), session: session)

        await #expect(throws: AuthError.invalidGrant) { try await auth.accessToken() }
        #expect(auth.phase == .signedOut)
    }

    @Test func serverErrorKeepsTheSession() async {
        // A short outage of the identity provider must not sign the user out.
        StubURLProtocol.handler = route(token: { (503, "") })
        let store = MemoryTokenStore(expiredTokens())
        let auth = AuthStore(settings: settings, tokenStore: store, session: session)

        await #expect(throws: AuthError.tokenRequestFailed("HTTP 503")) { try await auth.accessToken() }
        #expect(auth.phase == .signedIn)
        #expect(store.load() != nil)
    }

    @Test func refreshStoresTheNewTokens() async throws {
        StubURLProtocol.handler = route(token: { (200, #"{"access_token":"new","expires_in":300,"token_type":"Bearer"}"#) })
        let store = MemoryTokenStore(expiredTokens())
        let auth = AuthStore(settings: settings, tokenStore: store, session: session)

        #expect(try await auth.accessToken() == "new")
        // Keycloak may leave out the refresh token: the old one stays.
        #expect(store.load()?.refreshToken == "refresh")
        #expect(store.load()?.expires(within: 60) == false)
    }

    @Test func apiRetriesOnceWithAFreshToken() async throws {
        StubURLProtocol.handler = route(
            token: { (200, #"{"access_token":"fresh","expires_in":300}"#) },
            api: { request in
                request.value(forHTTPHeaderField: "Authorization") == "Bearer fresh"
                    ? (200, #"[{"id":1,"name":"A","url":"https://a","state":"ACTIVE"}]"#)
                    : (401, "")
            })
        let valid = TokenSet(accessToken: "revoked", refreshToken: "refresh", idToken: nil, expiresAt: .distantFuture)
        let auth = AuthStore(settings: settings, tokenStore: MemoryTokenStore(valid), session: session)
        let api = APIClient(settings: settings, auth: auth, session: session)

        let monitors = try await api.monitors()
        #expect(monitors.map(\.name) == ["A"])
        #expect(auth.phase == .signedIn)
    }

    @Test func apiSignsOutWhenTheTokenIsNeverAccepted() async {
        StubURLProtocol.handler = route(token: { (200, #"{"access_token":"fresh","expires_in":300}"#) },
                                        api: { _ in (401, "") })
        let valid = TokenSet(accessToken: "a", refreshToken: "refresh", idToken: nil, expiresAt: .distantFuture)
        let auth = AuthStore(settings: settings, tokenStore: MemoryTokenStore(valid), session: session)
        let api = APIClient(settings: settings, auth: auth, session: session)

        await #expect(throws: APIError.unauthorized) { _ = try await api.monitors() }
        #expect(auth.phase == .signedOut)
    }

    @Test func newInstallStartsSignedOut() throws {
        let suite = "test.\(UUID().uuidString)"
        let defaults = try #require(UserDefaults(suiteName: suite))
        defer { defaults.removePersistentDomain(forName: suite) }
        let store = MemoryTokenStore(expiredTokens())

        AuthStore.clearTokensAfterInstall(store, defaults: defaults)
        #expect(store.load() == nil)

        // Later launches keep the tokens.
        store.save(expiredTokens())
        AuthStore.clearTokensAfterInstall(store, defaults: defaults)
        #expect(store.load() != nil)
    }
}
