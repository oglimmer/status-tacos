import Foundation
import Testing
@testable import StatusTacos

private let discovery = OIDCDiscovery(
    authorizationEndpoint: URL(string: "https://id.example.com/realms/r/protocol/openid-connect/auth")!,
    tokenEndpoint: URL(string: "https://id.example.com/realms/r/protocol/openid-connect/token")!,
    endSessionEndpoint: nil)

private let configuration = OIDCConfiguration(
    issuer: URL(string: "https://id.example.com/realms/r")!,
    clientID: "status-tacos-ios",
    redirectURI: URL(string: "de.oglimmer.statustacos:/oauth/callback")!)

struct PKCETests {
    @Test func challengeMatchesRFC7636Example() {
        // RFC 7636, appendix B.
        let pkce = PKCE(verifier: "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk")
        #expect(pkce.challenge == "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM")
    }

    @Test func randomVerifiersAreLongEnoughAndURLSafe() {
        let verifier = PKCE().verifier
        #expect(verifier.count == 43)
        #expect(verifier.allSatisfy { $0.isLetter || $0.isNumber || $0 == "-" || $0 == "_" })
        #expect(PKCE().verifier != verifier)
    }
}

struct OIDCClientTests {
    private let client = OIDCClient(configuration: configuration)

    @Test func authorizationURLHasAllParameters() throws {
        let pkce = PKCE(verifier: "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk")
        let url = client.authorizationURL(discovery: discovery, pkce: pkce, state: "xyz")
        let components = try #require(URLComponents(url: url, resolvingAgainstBaseURL: false))
        let items = Dictionary(uniqueKeysWithValues: (components.queryItems ?? []).map { ($0.name, $0.value ?? "") })
        #expect(components.host == "id.example.com")
        #expect(items == [
            "client_id": "status-tacos-ios",
            "redirect_uri": "de.oglimmer.statustacos:/oauth/callback",
            "response_type": "code",
            "scope": "openid profile email offline_access",
            "state": "xyz",
            "code_challenge": "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            "code_challenge_method": "S256",
        ])
    }

    @Test func readsTheCodeOfTheCallback() throws {
        let url = URL(string: "de.oglimmer.statustacos:/oauth/callback?state=xyz&session_state=s&code=abc.def")!
        #expect(try client.authorizationCode(from: url, expectedState: "xyz") == "abc.def")
    }

    @Test func rejectsAnotherState() {
        let url = URL(string: "de.oglimmer.statustacos:/oauth/callback?state=other&code=abc")!
        #expect(throws: AuthError.stateMismatch) {
            try client.authorizationCode(from: url, expectedState: "xyz")
        }
    }

    @Test func rejectsACallbackWithoutCode() {
        let url = URL(string: "de.oglimmer.statustacos:/oauth/callback?state=xyz")!
        #expect(throws: AuthError.missingCode) {
            try client.authorizationCode(from: url, expectedState: "xyz")
        }
    }

    @Test func reportsErrorsOfTheIdentityProvider() {
        let url = URL(string: "de.oglimmer.statustacos:/oauth/callback?error=invalid_request&error_description=Invalid+parameter%3A+redirect_uri&state=xyz")!
        #expect(throws: AuthError.authorizationDenied("Invalid parameter: redirect_uri")) {
            try client.authorizationCode(from: url, expectedState: "xyz")
        }
    }
}

struct EncodingTests {
    @Test func formEncodingEscapesReservedCharacters() {
        let data = FormEncoding.encode([("a", "1+2=3&4"), ("redirect_uri", "x:/y?z"), ("s p", "ä")])
        #expect(String(decoding: data, as: UTF8.self) == "a=1%2B2%3D3%264&redirect_uri=x%3A%2Fy%3Fz&s%20p=%C3%A4")
    }

    @Test func base64URLRoundTrip() {
        let bytes = Data([0xFB, 0xFF, 0xBF, 0x00, 0x01])
        let text = bytes.base64URLEncodedString()
        #expect(text == "-_-_AAE")
        #expect(Data(base64URLEncoded: text) == bytes)
    }

    @Test func accountFromIDToken() throws {
        let payload = Data(#"{"sub":"1","name":"Ada Lovelace","email":"ada@example.com"}"#.utf8).base64URLEncodedString()
        let account = try #require(Account(idToken: "eyJhbGciOiJSUzI1NiJ9.\(payload).c2ln"))
        #expect(account == Account(name: "Ada Lovelace", email: "ada@example.com"))
    }

    @Test func accountFallsBackToUsername() throws {
        let payload = Data(#"{"sub":"1","preferred_username":"ada"}"#.utf8).base64URLEncodedString()
        let account = try #require(Account(idToken: "h.\(payload).s"))
        #expect(account.name == "ada")
        #expect(account.email == nil)
    }

    @Test func accountRejectsMalformedTokens() {
        #expect(Account(idToken: "not-a-jwt") == nil)
        #expect(Account(idToken: "a.!!!.c") == nil)
    }

    @Test func tokenExpiry() {
        let now = Date(timeIntervalSince1970: 1000)
        let tokens = TokenSet(accessToken: "a", refreshToken: nil, idToken: nil, expiresAt: now.addingTimeInterval(20))
        #expect(tokens.expires(within: 30, now: now))
        #expect(!tokens.expires(within: 10, now: now))
    }
}

struct AppSettingsTests {
    @Test(arguments: [
        ("https://tacos.oglimmer.com", "https://tacos.oglimmer.com"),
        ("  https://tacos.oglimmer.com/  ", "https://tacos.oglimmer.com"),
        ("http://localhost:8080", "http://localhost:8080"),
        ("https://id.oglimmer.de/realms/status-tacos/", "https://id.oglimmer.de/realms/status-tacos"),
        ("HTTPS://Example.com", "HTTPS://Example.com"),
    ])
    func acceptsWebURLs(_ input: String, _ expected: String) {
        #expect(AppSettings.normalizedURL(input)?.absoluteString == expected)
    }

    @Test(arguments: ["", "tacos.oglimmer.com", "ftp://example.com", "https://", "https://x.com?a=1", "https://x.com#f"])
    func rejectsOtherInput(_ input: String) {
        #expect(AppSettings.normalizedURL(input) == nil)
    }

    @Test func clientID() {
        #expect(AppSettings.normalizedClientID("  ios ") == "ios")
        #expect(AppSettings.normalizedClientID("   ") == nil)
    }

    @MainActor
    @Test func storesValuesAndBuildsTheAPIURL() throws {
        let suite = "test.\(UUID().uuidString)"
        let defaults = try #require(UserDefaults(suiteName: suite))
        defer { defaults.removePersistentDomain(forName: suite) }

        let settings = AppSettings(defaults: defaults)
        #expect(settings.serverURL == AppSettings.defaultServerURL)
        #expect(settings.apiBaseURL.absoluteString == "https://tacos.oglimmer.com/api/v1")

        settings.serverURL = URL(string: "http://localhost:8080")!
        settings.clientID = "other"
        let reloaded = AppSettings(defaults: defaults)
        #expect(reloaded.apiBaseURL.absoluteString == "http://localhost:8080/api/v1")
        #expect(reloaded.oidcConfiguration.clientID == "other")
        #expect(reloaded.oidcConfiguration.callbackScheme == "de.oglimmer.statustacos")

        reloaded.resetToDefaults()
        #expect(AppSettings(defaults: defaults).serverURL == AppSettings.defaultServerURL)
    }
}
