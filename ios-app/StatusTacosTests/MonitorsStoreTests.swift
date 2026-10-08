import Foundation
import Testing
@testable import StatusTacos

/// Paths of the requests, in order.
final class RequestLog: @unchecked Sendable {
    private let lock = NSLock()
    private var paths: [String] = []

    func append(_ path: String) { lock.withLock { paths.append(path) } }
    var all: [String] { lock.withLock { paths } }
}

private let monitorsJSON = """
[{"id":1,"name":"Web","url":"https://web","tenant":{"id":10,"name":"Prod"},"state":"ACTIVE"}]
"""

// An extension of SessionTests: the suites share the stub, so they must not run in parallel.
extension SessionTests {
    private func makeStore(log: RequestLog, user: @escaping @Sendable () -> (Int, String)) -> MonitorsStore {
        StubURLProtocol.handler = { request in
            let path = request.url!.path()
            log.append(path)
            if path.hasSuffix("/users/me") { return user() }
            if path.hasSuffix("/monitors") { return (200, monitorsJSON) }
            return (200, "[]")
        }
        let settings = AppSettings(defaults: UserDefaults(suiteName: "test.\(UUID().uuidString)")!)
        let tokens = TokenSet(accessToken: "a", refreshToken: "r", idToken: nil, expiresAt: .distantFuture)
        let session = StubURLProtocol.session()
        let auth = AuthStore(settings: settings, tokenStore: MemoryTokenStore(tokens), session: session)
        return MonitorsStore(api: APIClient(settings: settings, auth: auth, session: session))
    }

    @Test func firstLoadCreatesTheUserBeforeLoadingMonitors() async {
        let log = RequestLog()
        let store = makeStore(log: log) {
            (200, #"{"id":5,"tenants":[{"id":20,"name":"Staging"},{"id":10,"name":"Prod"}]}"#)
        }

        await store.refresh()

        #expect(log.all.first == "/api/v1/users/me")
        #expect(log.all.filter { $0.hasSuffix("/users/me") }.count == 1)
        // Tenants come from the user, also those without monitors, sorted by name.
        #expect(store.tenants.map(\.name) == ["Prod", "Staging"])
        #expect(store.monitors.map(\.name) == ["Web"])
        #expect(store.errorMessage == nil)
    }

    @Test func laterRefreshesReloadTheTenants() async {
        let log = RequestLog()
        let calls = RequestLog()
        let store = makeStore(log: log) {
            calls.append("me")
            return calls.all.count == 1
                ? (200, #"{"tenants":[{"id":10,"name":"Prod"}]}"#)
                : (200, #"{"tenants":[{"id":10,"name":"Prod"},{"id":30,"name":"New"}]}"#)
        }

        await store.refresh()
        #expect(store.tenants.map(\.name) == ["Prod"])
        await store.refresh()
        #expect(store.tenants.map(\.name) == ["New", "Prod"])
    }

    @Test func tenantsFallBackToTheMonitorsWhenUserFails() async {
        let store = makeStore(log: RequestLog()) { (500, "") }

        await store.refresh()

        #expect(store.tenants.map(\.name) == ["Prod"])
        #expect(store.monitors.count == 1)
        #expect(store.errorMessage == nil)
    }

    // MARK: - Monitor state

    private func makeStateStore(log: RequestLog, patch: @escaping @Sendable (URLRequest) -> (Int, String)) -> MonitorsStore {
        StubURLProtocol.handler = { request in
            let url = request.url!
            log.append("\(request.httpMethod ?? "?") \(url.path())\(url.query().map { "?" + $0 } ?? "")")
            if request.httpMethod == "PATCH" { return patch(request) }
            if url.path().hasSuffix("/users/me") { return (200, #"{"tenants":[{"id":10,"name":"Prod"}]}"#) }
            if url.path().hasSuffix("/monitors") { return (200, monitorsJSON) }
            if url.path().hasSuffix("/monitor-statuses") { return (200, #"[{"monitorId":1,"currentStatus":"up"}]"#) }
            return (200, "[]")
        }
        let settings = AppSettings(defaults: UserDefaults(suiteName: "test.\(UUID().uuidString)")!)
        let tokens = TokenSet(accessToken: "a", refreshToken: "r", idToken: nil, expiresAt: .distantFuture)
        let session = StubURLProtocol.session()
        let auth = AuthStore(settings: settings, tokenStore: MemoryTokenStore(tokens), session: session)
        return MonitorsStore(api: APIClient(settings: settings, auth: auth, session: session))
    }

    @Test func silencingSendsTheStateAndUpdatesTheList() async throws {
        let log = RequestLog()
        let store = makeStateStore(log: log) { _ in
            (200, #"{"id":1,"name":"Web","url":"https://web","state":"SILENT"}"#)
        }
        await store.refresh()
        #expect(store.monitors.first?.state == .active)

        try await store.setState(.silent, forMonitor: 1)

        #expect(log.all.contains("PATCH /api/v1/monitors/1/state?state=SILENT"))
        #expect(store.monitors.first?.state == .silent)
        #expect(store.monitors.first?.health == .up)
        #expect(store.updatingStateIDs.isEmpty)
    }

    @Test func disablingPausesTheMonitor() async throws {
        let store = makeStateStore(log: RequestLog()) { _ in
            (200, #"{"id":1,"name":"Web","url":"https://web","state":"INACTIVE"}"#)
        }
        await store.refresh()

        try await store.setState(.inactive, forMonitor: 1)

        #expect(store.monitors.first?.state == .inactive)
        #expect(store.monitors.first?.health == .paused)
    }

    @Test func refusedChangeKeepsTheOldState() async {
        let store = makeStateStore(log: RequestLog()) { _ in (403, "") }
        await store.refresh()

        await #expect(throws: APIError.forbidden) {
            try await store.setState(.inactive, forMonitor: 1)
        }
        #expect(store.monitors.first?.state == .active)
        #expect(store.updatingStateIDs.isEmpty)
    }

    @Test func selectableStatesMatchTheWebApp() {
        #expect(MonitorState.selectable.map(\.rawValue) == ["ACTIVE", "SILENT", "INACTIVE"])
    }

    @Test func aRefreshDuringALoadLoadsAgain() async {
        // The first load is slow. A state change asks for a refresh while it runs.
        let calls = RequestLog()
        let store = makeStateStore(log: calls) { _ in (200, "{}") }
        let firstLoad = Task { await store.refresh() }
        await Task.yield()
        await store.refresh()
        await firstLoad.value

        let monitorLoads = calls.all.filter { $0.hasSuffix("/monitors") }.count
        #expect(monitorLoads == 2)
    }
}
