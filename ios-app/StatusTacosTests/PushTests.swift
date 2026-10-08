import Foundation
import Testing
@testable import StatusTacos

private func alert(all: Bool, active: Bool = true, monitors: [Int] = []) -> PushAlertDTO {
    PushAlertDTO(
        id: 1, isActive: active, tenant: Tenant(id: 10, name: "Prod", code: nil), allMonitors: all,
        monitors: monitors.map { PushAlertDTO.MonitorReference(id: $0, name: "M\($0)") })
}

struct PushCoverageTests {
    @Test func coverageOfAMonitor() {
        #expect(PushCoverage(alert: nil, monitorID: 5) == .off)
        #expect(PushCoverage(alert: alert(all: true), monitorID: 5) == .allMonitors)
        #expect(PushCoverage(alert: alert(all: true, active: false), monitorID: 5) == .paused)
        #expect(PushCoverage(alert: alert(all: false, monitors: [5, 6]), monitorID: 5) == .selected)
        #expect(PushCoverage(alert: alert(all: false, monitors: [6]), monitorID: 5) == .off)
        #expect(PushCoverage(alert: alert(all: false, active: false, monitors: [5]), monitorID: 5) == .paused)
    }

    @Test func turningOnWithoutAlertCreatesOneForThisMonitor() {
        #expect(PushCoverage.change(existing: nil, monitorID: 5, enabled: true)
                == .save(PushAlertRequest(isActive: true, allMonitors: false, monitorIds: [5])))
        #expect(PushCoverage.change(existing: nil, monitorID: 5, enabled: false) == .none)
    }

    @Test func selectedMonitorsAreAddedAndRemoved() {
        #expect(PushCoverage.change(existing: alert(all: false, monitors: [7]), monitorID: 5, enabled: true)
                == .save(PushAlertRequest(isActive: true, allMonitors: false, monitorIds: [5, 7])))
        #expect(PushCoverage.change(existing: alert(all: false, monitors: [5, 7]), monitorID: 5, enabled: false)
                == .save(PushAlertRequest(isActive: true, allMonitors: false, monitorIds: [7])))
        // The backend needs at least one monitor: the last one deletes the alert.
        #expect(PushCoverage.change(existing: alert(all: false, monitors: [5]), monitorID: 5, enabled: false)
                == .delete)
    }

    @Test func anAlertForAllMonitorsIsNotChangedPerMonitor() {
        #expect(PushCoverage.change(existing: alert(all: true), monitorID: 5, enabled: false) == .none)
        #expect(PushCoverage.change(existing: alert(all: true), monitorID: 5, enabled: true) == .none)
        // A paused alert for all monitors is turned on again.
        #expect(PushCoverage.change(existing: alert(all: true, active: false), monitorID: 5, enabled: true)
                == .save(PushAlertRequest(isActive: true, allMonitors: true, monitorIds: [])))
    }

    @Test func monitorIDsFromThePayload() {
        #expect(PushPayload.monitorID(from: ["monitorId": 7]) == 7)
        #expect(PushPayload.monitorID(from: ["monitorId": NSNumber(value: 8)]) == 8)
        #expect(PushPayload.monitorID(from: ["monitorId": "9"]) == 9)
        #expect(PushPayload.monitorID(from: ["kind": "test"]) == nil)
    }

    @Test func requestsUseTheBackendNames() throws {
        let alertJSON = try JSONEncoder().encode(PushAlertRequest(isActive: false, allMonitors: false, monitorIds: [3]))
        let alertObject = try JSONSerialization.jsonObject(with: alertJSON) as? [String: Any]
        #expect(alertObject?["isActive"] as? Bool == false)
        #expect(alertObject?["allMonitors"] as? Bool == false)
        #expect(alertObject?["monitorIds"] as? [Int] == [3])

        let deviceJSON = try JSONEncoder().encode(
            PushDeviceRequest(token: "ab", environment: .sandbox, deviceName: "iPhone"))
        let deviceObject = try JSONSerialization.jsonObject(with: deviceJSON) as? [String: Any]
        #expect(deviceObject?["environment"] as? String == "SANDBOX")
        #expect(deviceObject?["token"] as? String == "ab")
    }

    @Test func decodesPushAlerts() throws {
        let alerts = try JSONDecoder.backend().decode([PushAlertDTO].self, from: Data("""
        [{"id":4,"type":"IOS_PUSH","value":"user:5","isActive":true,"allMonitors":false,
          "tenant":{"id":10,"name":"Prod","code":"PRD"},"monitors":[{"id":7,"name":"Shop"}],
          "owner":{"id":5,"email":"ada@example.com"},"createdAt":"2026-10-08T10:00:00"}]
        """.utf8))
        #expect(alerts.first?.monitorIDs == [7])
        #expect(alerts.first?.tenant?.id == 10)
        #expect(alerts.first?.allMonitors == false)
    }
}

private final class PermissionBox: @unchecked Sendable {
    var value: PushStore.Permission
    init(_ value: PushStore.Permission) { self.value = value }
}

/// Records method, path and body of every request.
private final class PushLog: @unchecked Sendable {
    struct Entry { let method: String; let path: String; let body: [String: Any]? }
    private let lock = NSLock()
    private var entries: [Entry] = []

    func append(_ request: URLRequest) {
        var data = request.httpBody
        if data == nil, let stream = request.httpBodyStream {
            stream.open()
            var buffer = Data()
            var chunk = [UInt8](repeating: 0, count: 4096)
            while stream.hasBytesAvailable {
                let count = stream.read(&chunk, maxLength: chunk.count)
                if count <= 0 { break }
                buffer.append(chunk, count: count)
            }
            stream.close()
            data = buffer
        }
        let body = data.flatMap { try? JSONSerialization.jsonObject(with: $0) as? [String: Any] }
        let entry = Entry(method: request.httpMethod ?? "?", path: request.url!.path(), body: body)
        lock.withLock { entries.append(entry) }
    }

    var all: [Entry] { lock.withLock { entries } }
}

// Shares the URL stub with SessionTests, so it must not run in parallel with it.
extension SessionTests {
    private func makePush(
        log: PushLog, permission: PushStore.Permission = .allowed, registered: RequestLog = RequestLog(),
        respond: @escaping @Sendable (URLRequest) -> (Int, String)? = { _ in nil }
    ) -> PushStore {
        StubURLProtocol.handler = { request in
            log.append(request)
            if let answer = respond(request) { return answer }
            let path = request.url!.path()
            if path.hasSuffix("/push/status") { return (200, #"{"enabled":true,"devices":1}"#) }
            if path.hasSuffix("/push/alerts") {
                return (200, #"[{"id":1,"isActive":true,"allMonitors":false,"tenant":{"id":10,"name":"Prod"},"monitors":[{"id":7,"name":"Shop"}]}]"#)
            }
            if request.httpMethod == "PUT", path.contains("/push/alerts/") {
                return (200, #"{"id":1,"isActive":true,"allMonitors":false,"tenant":{"id":10,"name":"Prod"},"monitors":[{"id":5,"name":"Web"}]}"#)
            }
            return (204, "")
        }
        let settings = AppSettings(defaults: UserDefaults(suiteName: "test.\(UUID().uuidString)")!)
        let tokens = TokenSet(accessToken: "a", refreshToken: "r", idToken: nil, expiresAt: .distantFuture)
        let session = StubURLProtocol.session()
        let auth = AuthStore(settings: settings, tokenStore: MemoryTokenStore(tokens), session: session)
        let api = APIClient(settings: settings, auth: auth, session: session)
        let current = PermissionBox(permission)
        let system = PushStore.System(
            permission: { current.value },
            requestPermission: {
                registered.append("asked")
                current.value = .allowed
                return true
            },
            registerForRemoteNotifications: { registered.append("registered") })
        return PushStore(api: api, system: system, environment: .sandbox)
    }

    @Test func startRegistersAndLoadsTheAlerts() async {
        let registered = RequestLog()
        let push = makePush(log: PushLog(), registered: registered)

        await push.start()

        #expect(registered.all == ["registered"])
        #expect(push.permission == .allowed)
        #expect(push.serverEnabled == true)
        #expect(push.alert(forTenant: 10)?.monitorIDs == [7])
        #expect(push.coverage(monitorID: 7, tenantID: 10) == .selected)
    }

    @Test func startDoesNotRegisterWithoutPermission() async {
        let registered = RequestLog()
        let push = makePush(log: PushLog(), permission: .denied, registered: registered)

        await push.start()

        #expect(registered.all.isEmpty)
    }

    @Test func theDeviceTokenIsSentAsHex() async throws {
        let log = PushLog()
        let push = makePush(log: log)

        await push.didRegister(deviceToken: Data([0x0a, 0xff, 0x10]))

        let put = try #require(log.all.first { $0.method == "PUT" && $0.path.hasSuffix("/push/devices") })
        #expect(put.body?["token"] as? String == "0aff10")
        #expect(put.body?["environment"] as? String == "SANDBOX")
        #expect(push.deviceToken == "0aff10")
        #expect(push.deviceCount == 1)
        #expect(push.registrationError == nil)
    }

    @Test func turningOnAMonitorAsksForPermissionFirst() async throws {
        let log = PushLog()
        let registered = RequestLog()
        let push = makePush(log: log, permission: .notDetermined, registered: registered)

        await push.setMonitor(5, tenantID: 10, enabled: true)

        #expect(registered.all == ["asked", "registered"])
        let put = try #require(log.all.first { $0.method == "PUT" && $0.path.hasSuffix("/push/alerts/10") })
        #expect(put.body?["allMonitors"] as? Bool == false)
        #expect(put.body?["monitorIds"] as? [Int] == [5])
        #expect(push.coverage(monitorID: 5, tenantID: 10) == .selected)
    }

    @Test func turningOffTheLastMonitorDeletesTheAlert() async {
        let log = PushLog()
        let push = makePush(log: log)
        await push.load()

        await push.setMonitor(7, tenantID: 10, enabled: false)

        #expect(log.all.contains { $0.method == "DELETE" && $0.path.hasSuffix("/push/alerts/10") })
        #expect(push.alert(forTenant: 10) == nil)
    }

    @Test func serverMessagesAreShown() async {
        let push = makePush(log: PushLog()) { request in
            request.url!.path().hasSuffix("/test") ? (409, #"{"message":"No iOS device got the notification."}"#) : nil
        }

        await push.sendTest(tenantID: 10)

        #expect(push.errorMessage == "No iOS device got the notification.")
        #expect(push.infoMessage == nil)
    }

    @Test func signOutUnregistersThisDevice() async {
        let log = PushLog()
        let push = makePush(log: log)
        await push.didRegister(deviceToken: Data([0xab, 0xcd]))
        await push.load()

        await push.unregisterDevice()

        #expect(log.all.contains { $0.method == "DELETE" && $0.path.hasSuffix("/push/devices/abcd") })
        #expect(push.deviceToken == nil)
        #expect(push.alertsByTenant.isEmpty)
    }
}
