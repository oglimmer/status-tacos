#if DEBUG
import Foundation
import UIKit

// Demo mode for screenshots: the real screens and stores, signed in, with canned data.
// No network, no Keychain, no push registration, no permission prompt. Debug builds only.
//
// Launch arguments (they land in the argument domain of UserDefaults):
//   -demo allUp | outage      switches it on
//   -demoDetail <monitorId>   opens the detail of that monitor
//   -demoScroll bottom        scrolls the detail to its end
// See README.md, "Demo mode".

enum DemoScenario: String, Sendable {
    case allUp
    case outage
}

@MainActor
struct DemoMode {
    let settings: AppSettings
    let auth: AuthStore
    let api: APIClient
    let push: PushStore
    let router: NotificationRouter

    /// Nil without `-demo allUp|outage`.
    static func fromLaunchArguments() -> DemoMode? {
        let arguments = UserDefaults.standard
        guard let scenario = arguments.string(forKey: "demo").flatMap(DemoScenario.init(rawValue:)) else { return nil }
        DemoURLProtocol.scenario = scenario

        // Same list and detail every time. Launch arguments like `-monitorDetail.timeframe 7d` still win.
        for key in ["monitorList.tenantFilter", "monitorList.sort", "monitorList.sortAscending", "monitorDetail.timeframe"] {
            arguments.removeObject(forKey: key)
        }

        // Own settings, so the real server settings stay untouched. No server is ever reached.
        let defaults = UserDefaults(suiteName: "de.oglimmer.statustacos.demo")!
        defaults.removePersistentDomain(forName: "de.oglimmer.statustacos.demo")
        let settings = AppSettings(defaults: defaults)
        settings.serverURL = URL(string: "https://demo.status-tacos.invalid")!

        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [DemoURLProtocol.self]
        let session = URLSession(configuration: configuration)

        let tokens = TokenSet(accessToken: "demo", refreshToken: "demo", idToken: nil, expiresAt: .distantFuture)
        let auth = AuthStore(settings: settings, tokenStore: DemoTokenStore(tokens), session: session)
        let api = APIClient(settings: settings, auth: auth, session: session)
        let push = PushStore(api: api, system: PushStore.System(
            permission: { .allowed },
            requestPermission: { true },
            registerForRemoteNotifications: {}))
        let router = NotificationRouter()
        let detailID = arguments.integer(forKey: "demoDetail")
        if detailID > 0 {
            router.path = [detailID]
            if arguments.string(forKey: "demoScroll") == "bottom" {
                scrollToBottomLater()
            }
        }
        return DemoMode(settings: settings, auth: auth, api: api, push: push, router: router)
    }

    /// Scrolls the tallest visible scroll view (the list of the detail screen) to its end, after the
    /// data has loaded. Done from the outside, so the screens need no demo code.
    private static func scrollToBottomLater() {
        Task { @MainActor in
            for _ in 0..<3 {
                try? await Task.sleep(for: .seconds(1.5))
                let windows = UIApplication.shared.connectedScenes
                    .compactMap { $0 as? UIWindowScene }
                    .flatMap(\.windows)
                guard let scrollView = windows.flatMap(scrollViews(in:))
                    .filter({ !$0.isHidden && $0.window != nil })
                    .max(by: { $0.contentSize.height < $1.contentSize.height })
                else { continue }
                let bottom = scrollView.contentSize.height + scrollView.adjustedContentInset.bottom - scrollView.bounds.height
                scrollView.setContentOffset(CGPoint(x: 0, y: max(bottom, -scrollView.adjustedContentInset.top)), animated: false)
            }
        }
    }

    private static func scrollViews(in view: UIView) -> [UIScrollView] {
        let own = (view as? UIScrollView).map { [$0] } ?? []
        return own + view.subviews.flatMap(scrollViews(in:))
    }
}

/// Tokens in memory only.
final class DemoTokenStore: TokenStoring, @unchecked Sendable {
    private let lock = NSLock()
    private var tokens: TokenSet?

    init(_ tokens: TokenSet?) { self.tokens = tokens }

    func load() -> TokenSet? { lock.withLock { tokens } }
    func save(_ tokens: TokenSet) { lock.withLock { self.tokens = tokens } }
    func clear() { lock.withLock { tokens = nil } }
}

// MARK: - Fake server

/// Answers every API call of the app with JSON in the wire format, so the real decoders run.
/// Timestamps are made at the time of the request, so "12 minutes ago" stays 12 minutes.
final class DemoURLProtocol: URLProtocol, @unchecked Sendable {
    nonisolated(unsafe) static var scenario: DemoScenario = .allUp
    /// States set in the app (silence, pause), so they survive the next refresh.
    nonisolated(unsafe) private static var stateOverrides: [Int: String] = [:]
    private static let lock = NSLock()

    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
    override func stopLoading() {}

    override func startLoading() {
        let (status, body) = Self.answer(request)
        let response = HTTPURLResponse(url: request.url!, statusCode: status, httpVersion: nil,
                                       headerFields: ["Content-Type": "application/json"])!
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        if let body { client?.urlProtocol(self, didLoad: body) }
        client?.urlProtocolDidFinishLoading(self)
    }

    private static func answer(_ request: URLRequest) -> (Int, Data?) {
        guard let url = request.url, let range = url.path().range(of: "/api/v1/") else { return (404, nil) }
        let path = String(url.path()[range.upperBound...])
        let parts = path.split(separator: "/").map(String.init)
        // "monitors/7/state" becomes "monitors/{id}/state".
        let id = parts.compactMap { Int($0) }.first
        let route = parts.map { Int($0) == nil ? $0 : "{id}" }.joined(separator: "/")
        let query = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems ?? []
        let method = request.httpMethod ?? "GET"
        let data = DemoData(scenario: scenario, now: .now, states: lock.withLock { stateOverrides })

        switch "\(method) \(route)" {
        case "GET users/me":
            return json(["id": 1, "email": "demo@example.com", "tenants": DemoData.tenants.map(\.json)])
        case "DELETE users/me":
            return json(["loginAccountDeleted": true])
        case "GET monitors":
            return json(data.monitors.map(data.monitorJSON))
        case "PATCH monitors/{id}/state":
            guard let id, let state = query.first(where: { $0.name == "state" })?.value else { return (400, nil) }
            lock.withLock { stateOverrides[id] = state }
            let updated = DemoData(scenario: scenario, now: .now, states: lock.withLock { stateOverrides })
            guard let monitor = updated.monitor(id) else { return (404, nil) }
            return json(updated.monitorJSON(monitor))
        case "GET monitor-statuses":
            return json(data.monitors.filter { data.state(of: $0) != "INACTIVE" }.map(data.statusJSON))
        case "GET monitor-statuses/{id}/response-time-history-24h":
            guard let monitor = id.flatMap(data.monitor) else { return (404, nil) }
            return json(data.historyJSON(monitor))
        case "GET uptime-stats":
            let ninety = query.first(where: { $0.name == "period" })?.value == "ninety_days"
            return json(data.monitors.map { data.statsJSON($0, days: ninety ? 90 : 7, withPoints: false) })
        case "GET uptime-stats/{id}":
            guard let monitor = id.flatMap(data.monitor) else { return (404, nil) }
            return json([data.statsJSON(monitor, days: 7, withPoints: true),
                         data.statsJSON(monitor, days: 90, withPoints: true)])
        case "GET push/status":
            return json(["enabled": true, "devices": 1])
        case "GET push/alerts":
            return json([DemoData.pushAlertJSON(tenant: DemoData.tenants[0], isActive: true, allMonitors: true, monitorIDs: [])])
        case "PUT push/alerts/{id}":
            guard let tenant = DemoData.tenants.first(where: { $0.id == id }) else { return (404, nil) }
            let body = (try? JSONSerialization.jsonObject(with: bodyData(of: request))) as? [String: Any] ?? [:]
            return json(DemoData.pushAlertJSON(
                tenant: tenant, isActive: body["isActive"] as? Bool ?? true,
                allMonitors: body["allMonitors"] as? Bool ?? true, monitorIDs: body["monitorIds"] as? [Int] ?? []))
        case _ where method != "GET":
            return (204, nil)
        default:
            return (404, nil)
        }
    }

    private static func json(_ object: Any) -> (Int, Data?) {
        (200, try? JSONSerialization.data(withJSONObject: object))
    }

    /// URLSession hands the body to a URLProtocol as a stream.
    private static func bodyData(of request: URLRequest) -> Data {
        if let body = request.httpBody { return body }
        guard let stream = request.httpBodyStream else { return Data() }
        stream.open()
        defer { stream.close() }
        var data = Data()
        var buffer = [UInt8](repeating: 0, count: 4096)
        while stream.hasBytesAvailable {
            let count = stream.read(&buffer, maxLength: buffer.count)
            guard count > 0 else { break }
            data.append(buffer, count: count)
        }
        return data
    }
}

// MARK: - Fixtures

/// 3 tenants, 23 monitors, like the design drafts.
struct DemoData {
    struct DemoTenant {
        let id: Int
        let name: String
        var json: [String: Any] { ["id": id, "name": name] }
    }

    struct DemoMonitor {
        let id: Int
        let name: String
        let environment: String?
        let tenant: DemoTenant
        let url: String
        /// 7-day uptime when up.
        let uptime: Double
        let responseMs: Int
        /// Outages in the last 7 days: (days ago, seconds).
        let outages: [(daysAgo: Double, seconds: Double)]
        var state = "ACTIVE"
        var createdDaysAgo: Double = 200
    }

    static let tenants = [
        DemoTenant(id: 1, name: "Modular-Design PROD (Teams-Alert)"),
        DemoTenant(id: 2, name: "Modular-Design PROD (no alert)"),
        DemoTenant(id: 3, name: "Modular-Design DEV/STAGE"),
    ]

    /// Down in the outage scenario.
    static let downIDs: Set<Int> = [7, 8]
    /// Paused in the outage scenario.
    static let pausedInOutageID = 23

    static let baseMonitors: [DemoMonitor] = {
        let (alert, quiet, dev) = (tenants[0], tenants[1], tenants[2])
        return [
            DemoMonitor(id: 1, name: "Anerkennung API", environment: "PROD", tenant: alert,
                        url: "https://anerkennung.aerztekammern.de/api/actuator/health",
                        uptime: 99.99, responseMs: 154, outages: [(4.0, 70)]),
            DemoMonitor(id: 2, name: "Anerkennung API Web", environment: "PROD", tenant: alert,
                        url: "https://anerkennung.aerztekammern.de/web/actuator/health",
                        uptime: 99.99, responseMs: 161, outages: [(6.3, 65), (4.5, 80), (4.0, 70)]),
            DemoMonitor(id: 3, name: "Anerkennung API Admin", environment: "PROD", tenant: alert,
                        url: "https://anerkennung.aerztekammern.de/admin/actuator/health",
                        uptime: 99.99, responseMs: 154, outages: [(6.3, 65), (4.5, 80), (4.0, 70)]),
            DemoMonitor(id: 4, name: "Anerkennung API", environment: "STAGE", tenant: dev,
                        url: "https://anerkennung.stage.modular-design.de/api/actuator/health",
                        uptime: 99.95, responseMs: 162, outages: [(1.9, 150), (0.8, 140)]),
            DemoMonitor(id: 5, name: "Anerkennung API", environment: "DEV", tenant: dev,
                        url: "https://anerkennung.dev.modular-design.de/api/actuator/health",
                        uptime: 100, responseMs: 43, outages: []),
            DemoMonitor(id: 6, name: "Anerkennung Frontend", environment: "PROD", tenant: quiet,
                        url: "https://anerkennung.aerztekammern.de/",
                        uptime: 100, responseMs: 88, outages: []),
            DemoMonitor(id: 7, name: "Meldebogen", environment: "PROD", tenant: alert,
                        url: "https://meldebogen.aerztekammern.de/actuator/health",
                        uptime: 99.99, responseMs: 80, outages: [(4.02, 29)]),
            DemoMonitor(id: 8, name: "Kammerportal Login", environment: "PROD", tenant: alert,
                        url: "https://portal.aerztekammern.de/login",
                        uptime: 99.99, responseMs: 112, outages: [(2.8, 95)]),
            DemoMonitor(id: 9, name: "Kammerportal Admin", environment: "PROD", tenant: quiet,
                        url: "https://portal.aerztekammern.de/admin/health",
                        uptime: 99.99, responseMs: 131, outages: [(5.2, 90)]),
            DemoMonitor(id: 10, name: "Kammerportal Login", environment: "STAGE", tenant: dev,
                        url: "https://portal.stage.modular-design.de/login",
                        uptime: 99.97, responseMs: 118, outages: [(3.3, 200), (0.4, 45)]),
            DemoMonitor(id: 11, name: "Meldebogen", environment: "STAGE", tenant: dev,
                        url: "https://meldebogen.stage.modular-design.de/actuator/health",
                        uptime: 100, responseMs: 76, outages: []),
            DemoMonitor(id: 12, name: "Meldebogen", environment: "DEV", tenant: dev,
                        url: "https://meldebogen.dev.modular-design.de/actuator/health",
                        uptime: 99.98, responseMs: 64, outages: [(6.6, 120)]),
            DemoMonitor(id: 13, name: "Fortbildungspunkte API", environment: "PROD", tenant: alert,
                        url: "https://fobi.aerztekammern.de/api/health",
                        uptime: 100, responseMs: 97, outages: []),
            DemoMonitor(id: 14, name: "Fortbildungspunkte Web", environment: "PROD", tenant: quiet,
                        url: "https://fobi.aerztekammern.de/",
                        uptime: 99.99, responseMs: 103, outages: [(1.2, 75)]),
            DemoMonitor(id: 15, name: "Arztausweis Service", environment: "PROD", tenant: alert,
                        url: "https://arztausweis.aerztekammern.de/health",
                        uptime: 100, responseMs: 121, outages: []),
            DemoMonitor(id: 16, name: "Arztausweis Service", environment: "STAGE", tenant: dev,
                        url: "https://arztausweis.stage.modular-design.de/health",
                        uptime: 99.96, responseMs: 139, outages: [(5.5, 180), (2.1, 30)]),
            DemoMonitor(id: 17, name: "Mitgliederverwaltung", environment: "PROD", tenant: quiet,
                        url: "https://mitglieder.aerztekammern.de/actuator/health",
                        uptime: 100, responseMs: 58, outages: []),
            DemoMonitor(id: 18, name: "Dokumentenablage", environment: "PROD", tenant: quiet,
                        url: "https://dokumente.aerztekammern.de/health",
                        uptime: 99.99, responseMs: 149, outages: [(3.0, 61)], state: "SILENT"),
            DemoMonitor(id: 19, name: "Landing Page", environment: nil, tenant: quiet,
                        url: "https://www.modular-design.de/",
                        uptime: 100, responseMs: 47, outages: []),
            DemoMonitor(id: 20, name: "Status Webhook Relay", environment: nil, tenant: dev,
                        url: "https://hooks.modular-design.de/relay/health",
                        uptime: 100, responseMs: 52, outages: [], createdDaysAgo: 40),
            DemoMonitor(id: 21, name: "Elektronischer Heilberufsausweis Antragsportal Schnittstelle",
                        environment: "PROD", tenant: alert,
                        url: "https://ehba-antrag.aerztekammern.de/api/v2/actuator/health/readiness",
                        uptime: 99.99, responseMs: 164, outages: [(0.3, 50)]),
            DemoMonitor(id: 22, name: "Keycloak Identity Provider", environment: nil, tenant: quiet,
                        url: "https://id.modular-design.de/realms/master",
                        uptime: 100, responseMs: 71, outages: []),
            DemoMonitor(id: 23, name: "Mail Gateway", environment: "STAGE", tenant: dev,
                        url: "https://mail.stage.modular-design.de/health",
                        uptime: 100, responseMs: 66, outages: []),
        ]
    }()

    static let checkSeconds = 15.0
    static let outageMinutes = 12.0

    let scenario: DemoScenario
    let now: Date
    let states: [Int: String]

    var monitors: [DemoMonitor] { Self.baseMonitors }

    func monitor(_ id: Int) -> DemoMonitor? { monitors.first { $0.id == id } }

    func state(of monitor: DemoMonitor) -> String {
        if let state = states[monitor.id] { return state }
        if scenario == .outage, monitor.id == Self.pausedInOutageID { return "INACTIVE" }
        return monitor.state
    }

    func isDown(_ monitor: DemoMonitor) -> Bool {
        scenario == .outage && Self.downIDs.contains(monitor.id)
    }

    private var outageStart: Date { now.addingTimeInterval(-Self.outageMinutes * 60) }

    // MARK: JSON

    func monitorJSON(_ monitor: DemoMonitor) -> [String: Any] {
        var json: [String: Any] = [
            "id": monitor.id, "name": monitor.name, "url": monitor.url,
            "tenantId": monitor.tenant.id, "tenant": monitor.tenant.json,
            "state": state(of: monitor), "alertingThreshold": 15,
            "createdAt": Self.format(now.addingTimeInterval(-monitor.createdDaysAgo * 86_400)),
        ]
        json["environment"] = monitor.environment
        return json
    }

    func statusJSON(_ monitor: DemoMonitor) -> [String: Any] {
        let lastCheck = now.addingTimeInterval(-9)
        var json: [String: Any] = [
            "monitorId": monitor.id, "monitorName": monitor.name,
            "lastCheckedAt": Self.format(lastCheck),
        ]
        if isDown(monitor) {
            json["currentStatus"] = "down"
            json["lastStatusCode"] = 503
            json["consecutiveFailures"] = 18
            json["lastUpAt"] = Self.format(outageStart)
            json["outageStartedAt"] = Self.format(outageStart)
            json["lastDownAt"] = Self.format(lastCheck)
        } else {
            json["currentStatus"] = "up"
            json["lastStatusCode"] = 200
            json["consecutiveFailures"] = 0
            json["lastResponseTimeMs"] = monitor.responseMs
            json["lastUpAt"] = Self.format(lastCheck)
            if let last = downPeriods(monitor, days: 90).map(\.end).max() {
                json["lastDownAt"] = Self.format(last)
            }
        }
        return json
    }

    /// 24 hours, 96 points of 15 minutes.
    func historyJSON(_ monitor: DemoMonitor) -> [String: Any] {
        let periods = downPeriods(monitor, days: 1)
        let total = Int(86_400 / Self.checkSeconds)
        let uptime: Double = isDown(monitor) ? (monitor.id == 7 ? 99.17 : 99.16) : (periods.isEmpty ? 100 : 99.99)
        let times = responseSummary(monitor)
        return [
            "monitorId": monitor.id, "intervalMinutes": 15,
            "uptimePercentage24h": uptime, "totalChecks24h": total,
            "successfulChecks24h": Int((Double(total) * uptime / 100).rounded(.down)),
            "firstCheckAt": Self.format(firstCheck(monitor, windowStart: now.addingTimeInterval(-86_400))),
            "dataPoints": points(monitor, days: 1, intervalMinutes: 15),
            "statusDownPeriods": periods.map(Self.periodJSON),
            "avgResponseTimeMs": times.avg + (isDown(monitor) ? 2 : 0),
            "p99ResponseTimeMs": times.p99 + (isDown(monitor) ? 10 : 0),
            "minResponseTimeMs": times.min, "maxResponseTimeMs": times.max,
        ]
    }

    func statsJSON(_ monitor: DemoMonitor, days: Int, withPoints: Bool) -> [String: Any] {
        let start = now.addingTimeInterval(-Double(days) * 86_400)
        let total = Int(Double(days) * 86_400 / Self.checkSeconds)
        var uptime = monitor.uptime
        if isDown(monitor) { uptime = monitor.id == 8 ? 98.71 : 99.88 }
        if days == 90, uptime < 100 { uptime = max(uptime - 0.02, 0) }
        let interval = days == 7 ? 60 : 720
        let times = responseSummary(monitor)
        var json: [String: Any] = [
            "monitorId": monitor.id, "periodType": days == 7 ? "SEVEN_DAYS" : "NINETY_DAYS",
            "periodStart": Self.format(start), "periodEnd": Self.format(now),
            "intervalMinutes": interval, "totalChecks": total,
            "successfulChecks": Int((Double(total) * uptime / 100).rounded(.down)),
            "uptimePercentage": uptime,
            "minResponseTimeMs": times.min - 3, "maxResponseTimeMs": times.max + (days == 90 ? 900 : 120),
            "avgResponseTimeMs": times.avg - 6, "p99ResponseTimeMs": times.p99 - 40,
            "statusDownPeriods": downPeriods(monitor, days: Double(days)).map(Self.periodJSON),
            "firstCheckAt": Self.format(firstCheck(monitor, windowStart: start)),
        ]
        if withPoints {
            json["responseTimeDataPoints"] = points(monitor, days: Double(days), intervalMinutes: interval)
        }
        return json
    }

    static func pushAlertJSON(tenant: DemoTenant, isActive: Bool, allMonitors: Bool, monitorIDs: [Int]) -> [String: Any] {
        let references = monitorIDs.compactMap { id in baseMonitors.first { $0.id == id } }
            .map { ["id": $0.id, "name": $0.name] as [String: Any] }
        return ["id": tenant.id, "isActive": isActive, "tenant": tenant.json,
                "allMonitors": allMonitors, "monitors": references]
    }

    // MARK: Generated values

    /// Down periods that end in the last `days` days. A down monitor has one that runs to now.
    private func downPeriods(_ monitor: DemoMonitor, days: Double) -> [(start: Date, end: Date)] {
        var periods = monitor.outages.map { outage in
            let start = now.addingTimeInterval(-outage.daysAgo * 86_400)
            return (start: start, end: start.addingTimeInterval(outage.seconds))
        }
        // Older outages for the 90 days.
        if days > 7, monitor.uptime < 100 {
            periods += [20.0, 47.5, 71.0].map { daysAgo in
                let start = now.addingTimeInterval(-(daysAgo + Double(monitor.id) / 10) * 86_400)
                return (start: start, end: start.addingTimeInterval(240))
            }
        }
        if isDown(monitor) {
            periods.append((start: outageStart, end: now))
        }
        let windowStart = now.addingTimeInterval(-days * 86_400)
        return periods.filter { $0.end > windowStart }.sorted { $0.start < $1.start }
    }

    /// Scaled from the drafts of Meldebogen (80 ms): avg 82, P99 140, min 61, max 298.
    private func responseSummary(_ monitor: DemoMonitor) -> (avg: Int, p99: Int, min: Int, max: Int) {
        let scale = Double(monitor.responseMs) / 80
        func scaled(_ value: Double) -> Int { Int((value * scale).rounded()) }
        return (scaled(82), scaled(140), scaled(61), scaled(298))
    }

    /// Small noise around the response time, one spike, nothing during the current outage.
    private func points(_ monitor: DemoMonitor, days: Double, intervalMinutes: Int) -> [[String: Any]] {
        let step = Double(intervalMinutes) * 60
        let count = Int(days * 86_400 / step)
        let start = now.addingTimeInterval(-Double(count) * step)
        var random = SeededRandom(seed: UInt64(monitor.id) * 7919 + UInt64(intervalMinutes))
        let times = responseSummary(monitor)
        let spikeIndex = count * 2 / 3
        return (0..<count).map { index in
            let timestamp = start.addingTimeInterval(Double(index) * step)
            var point: [String: Any] = ["timestamp": Self.format(timestamp)]
            if isDown(monitor), timestamp.addingTimeInterval(step) > outageStart {
                return point
            }
            let base = Double(monitor.responseMs)
            var value = base + random.next(in: -0.08...0.12) * base
            if index % 17 == 5 { value += base * 0.25 }
            if index == spikeIndex { value = Double(times.max) }
            point["maxResponseTimeMs"] = Int(value.rounded())
            return point
        }
    }

    private func firstCheck(_ monitor: DemoMonitor, windowStart: Date) -> Date {
        max(now.addingTimeInterval(-monitor.createdDaysAgo * 86_400), windowStart)
    }

    private static func periodJSON(_ period: (start: Date, end: Date)) -> [String: Any] {
        ["start": format(period.start), "end": format(period.end)]
    }

    /// Backend format: UTC, no zone suffix, microseconds.
    static func format(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(identifier: "UTC")
        formatter.dateFormat = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS"
        return formatter.string(from: date)
    }
}

/// Same noise on every refresh.
private struct SeededRandom {
    var state: UInt64

    init(seed: UInt64) { state = seed &+ 0x9E37_79B9_7F4A_7C15 }

    mutating func next(in range: ClosedRange<Double>) -> Double {
        state = state &* 6_364_136_223_846_793_005 &+ 1_442_695_040_888_963_407
        let unit = Double(state >> 11) / Double(1 << 53)
        return range.lowerBound + unit * (range.upperBound - range.lowerBound)
    }
}
#endif
