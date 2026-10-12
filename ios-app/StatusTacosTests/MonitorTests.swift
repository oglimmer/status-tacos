import Foundation
import Testing
@testable import StatusTacos

private func monitor(_ id: Int, _ name: String, state: MonitorState = .active, tenant: Tenant? = nil,
                     createdAt: Date? = nil) -> MonitorDTO {
    MonitorDTO(id: id, name: name, url: "https://\(name.lowercased()).example.com/health",
               tenantId: tenant?.id, tenant: tenant, state: state, alertingThreshold: 3, createdAt: createdAt)
}

private func status(_ id: Int, _ type: StatusType, lastDownAt: Date? = nil) -> MonitorStatusDTO {
    MonitorStatusDTO(monitorId: id, monitorName: nil, currentStatus: type, lastCheckedAt: nil, lastUpAt: nil,
                     lastDownAt: lastDownAt, consecutiveFailures: nil, lastResponseTimeMs: 100, lastStatusCode: 200)
}

private func stats(_ id: Int, uptime: Double?) -> UptimeStatsDTO {
    UptimeStatsDTO(monitorId: id, periodType: .sevenDays,
                   periodStart: Date(timeIntervalSince1970: 0), periodEnd: Date(timeIntervalSince1970: 7 * 86_400),
                   intervalMinutes: 60, totalChecks: 10, successfulChecks: 9, uptimePercentage: uptime,
                   minResponseTimeMs: nil, maxResponseTimeMs: nil, avgResponseTimeMs: nil, p99ResponseTimeMs: nil,
                   responseTimeDataPoints: nil, statusDownPeriods: nil)
}

struct MonitorSummaryTests {
    @Test func joinsByMonitorID() throws {
        let merged = MonitorSummary.merge(
            monitors: [monitor(1, "Alpha"), monitor(2, "Beta")],
            statuses: [status(2, .up), status(1, .down)],
            weekStats: [stats(1, uptime: 98.5)])
        let alpha = try #require(merged.first { $0.id == 1 })
        #expect(alpha.health == .down)
        #expect(alpha.week?.uptimePercentage == 98.5)
        let beta = try #require(merged.first { $0.id == 2 })
        #expect(beta.health == .up)
        #expect(beta.week == nil)
    }

    @Test func healthComesFromStateAndStatus() {
        let merged = MonitorSummary.merge(
            monitors: [
                monitor(1, "Down"), monitor(2, "Up"), monitor(3, "New"),
                monitor(4, "Off", state: .inactive), monitor(5, "Quiet", state: .silent),
            ],
            // An inactive monitor can still have an old status: it is paused anyway.
            statuses: [status(1, .down), status(2, .up), status(4, .down), status(5, .down)],
            weekStats: [])
        let health = Dictionary(uniqueKeysWithValues: merged.map { ($0.name, $0.health) })
        #expect(health == ["Down": .down, "Up": .up, "New": .pending, "Off": .paused, "Quiet": .down])
    }

    @Test func sortsByHealthThenByName() {
        let merged = MonitorSummary.merge(
            monitors: [
                monitor(1, "b up"), monitor(2, "a paused", state: .inactive), monitor(3, "Z down"),
                monitor(4, "A up"), monitor(5, "pending"), monitor(6, "a down"), monitor(7, "item 10"),
                monitor(8, "item 9"),
            ],
            statuses: [status(1, .up), status(3, .down), status(4, .up), status(6, .down),
                       status(7, .up), status(8, .up)],
            weekStats: [])
        #expect(merged.map(\.name) == [
            "a down", "Z down", "pending", "A up", "b up", "item 9", "item 10", "a paused",
        ])
    }

    @Test func sortsByName() {
        let merged = MonitorSummary.merge(
            monitors: [monitor(1, "item 10"), monitor(2, "Beta"), monitor(3, "alpha", state: .inactive),
                       monitor(4, "item 9")],
            statuses: [status(1, .down), status(2, .up), status(4, .up)],
            weekStats: [])
        #expect(merged.sorted(by: .name, ascending: true).map(\.name) == ["alpha", "Beta", "item 9", "item 10"])
        #expect(merged.sorted(by: .name, ascending: false).map(\.name) == ["item 10", "item 9", "Beta", "alpha"])
    }

    @Test func sortsByLastDowntimeWithNeverDownLast() {
        let merged = MonitorSummary.merge(
            monitors: [monitor(1, "Never"), monitor(2, "Old"), monitor(3, "Recent"), monitor(4, "Also never")],
            statuses: [status(1, .up), status(2, .up, lastDownAt: Date(timeIntervalSince1970: 100)),
                       status(3, .down, lastDownAt: Date(timeIntervalSince1970: 200))],
            weekStats: [])
        #expect(merged.sorted(by: .lastDown, ascending: false).map(\.name) == ["Recent", "Old", "Never", "Also never"])
        #expect(merged.sorted(by: .lastDown, ascending: true).map(\.name) == ["Old", "Recent", "Also never", "Never"])
    }

    @Test func sortsByTenantThenName() {
        let zeta = Tenant(id: 1, name: "Zeta", code: nil)
        let alpha = Tenant(id: 2, name: "alpha", code: nil)
        let merged = MonitorSummary.merge(
            monitors: [monitor(1, "B", tenant: zeta), monitor(2, "A", tenant: zeta), monitor(3, "C", tenant: alpha),
                       monitor(4, "A", tenant: alpha), monitor(5, "No tenant")],
            statuses: [],
            weekStats: [])
        #expect(merged.sorted(by: .tenant, ascending: true).map(\.id) == [4, 3, 2, 1, 5])
        #expect(merged.sorted(by: .tenant, ascending: false).map(\.id) == [1, 2, 3, 4, 5])
    }

    @Test func sortsByCreationTime() {
        let merged = MonitorSummary.merge(
            monitors: [monitor(1, "Middle", createdAt: Date(timeIntervalSince1970: 200)),
                       monitor(2, "First", createdAt: Date(timeIntervalSince1970: 100)),
                       monitor(3, "Last", createdAt: Date(timeIntervalSince1970: 300)),
                       monitor(4, "Older server")],
            statuses: [],
            weekStats: [])
        #expect(merged.sorted(by: .created, ascending: true).map(\.name) == ["First", "Middle", "Last", "Older server"])
        #expect(merged.sorted(by: .created, ascending: false).map(\.name) == ["Last", "Middle", "First", "Older server"])
    }

    @Test func sortsByStatusInBothDirections() {
        let merged = MonitorSummary.merge(
            monitors: [monitor(1, "Up"), monitor(2, "Paused", state: .inactive), monitor(3, "Down"),
                       monitor(4, "Pending")],
            statuses: [status(1, .up), status(3, .down)],
            weekStats: [])
        #expect(merged.sorted(by: .status, ascending: true).map(\.name) == ["Down", "Pending", "Up", "Paused"])
        #expect(merged.sorted(by: .status, ascending: false).map(\.name) == ["Paused", "Up", "Pending", "Down"])
    }

    @Test func datesStartWithTheNewest() {
        #expect(MonitorSort.allCases.filter { !$0.startsAscending } == [.lastDown, .created])
    }

    @Test func toleratesDuplicateResults() {
        let merged = MonitorSummary.merge(
            monitors: [monitor(1, "One")],
            statuses: [status(1, .down), status(1, .up)],
            weekStats: [stats(1, uptime: 1), stats(1, uptime: 2)])
        #expect(merged.count == 1)
        #expect(merged[0].health == .down)
        #expect(merged[0].week?.uptimePercentage == 1)
    }

    @Test func hostAndWeekWindow() {
        let summary = MonitorSummary.merge(
            monitors: [monitor(1, "Api")], statuses: [], weekStats: [stats(1, uptime: 100)])[0]
        #expect(summary.host == "api.example.com")
        #expect(summary.weekWindow?.end.timeIntervalSince(summary.weekWindow!.start) == 604_800.0)
    }

    @Test(arguments: [
        ("https://api.example.com/health", "https", "api.example.com", "/health"),
        ("HTTP://a.com:8443/v1/x?y=1#z", "http", "a.com:8443", "/v1/x?y=1#z"),
        ("https://a.com?q=1", "https", "a.com", "?q=1"),
        ("https://a.com/", "https", "a.com", nil),
        ("https://a.com", "https", "a.com", nil),
        ("not a url", nil, "not a url", nil),
    ] as [(String, String?, String, String?)])
    func splitsURLIntoSchemeHostAndPath(url: String, scheme: String?, host: String, path: String?) {
        let summary = MonitorSummary(id: 1, name: "X", url: url, tenant: nil, state: .active,
                                     alertingThreshold: nil, createdAt: nil, status: nil, week: nil)
        #expect(summary.urlParts.scheme == scheme)
        #expect(summary.urlParts.host == host)
        #expect(summary.urlParts.path == path)
    }
}

struct PeriodSnapshotTests {
    private let t0 = Date(timeIntervalSince1970: 1_767_225_600)

    @Test func dayWindowEndsWhenFetched() {
        let history = ResponseTimeHistoryDTO(
            monitorId: 1, intervalMinutes: 3, uptimePercentage24h: 99.5, totalChecks24h: 100,
            successfulChecks24h: 99, dataPoints: nil,
            statusDownPeriods: [DownPeriod(start: t0.addingTimeInterval(-3600), end: t0.addingTimeInterval(-3000))])
        let snapshot = PeriodSnapshot(history: history, fetchedAt: t0)
        #expect(snapshot.end == t0)
        #expect(snapshot.start == t0.addingTimeInterval(-86_400))
        #expect(snapshot.responseTimes == nil)
        #expect(snapshot.dataPoints.isEmpty)
        #expect(snapshot.totalDowntime == 600)
    }

    @Test func statsWithoutWindowAreSkipped() {
        let dto = UptimeStatsDTO(
            monitorId: 1, periodType: .sevenDays, periodStart: nil, periodEnd: nil, intervalMinutes: nil,
            totalChecks: nil, successfulChecks: nil, uptimePercentage: nil, minResponseTimeMs: nil,
            maxResponseTimeMs: nil, avgResponseTimeMs: nil, p99ResponseTimeMs: nil,
            responseTimeDataPoints: nil, statusDownPeriods: nil)
        #expect(PeriodSnapshot(stats: dto) == nil)
    }

    @Test func outagesAreMergedAndNewestFirst() throws {
        let dto = UptimeStatsDTO(
            monitorId: 1, periodType: .sevenDays, periodStart: t0, periodEnd: t0.addingTimeInterval(7 * 86_400),
            intervalMinutes: 60, totalChecks: 10, successfulChecks: 8, uptimePercentage: 80,
            minResponseTimeMs: 10, maxResponseTimeMs: 90, avgResponseTimeMs: 20, p99ResponseTimeMs: 80,
            responseTimeDataPoints: [],
            statusDownPeriods: [
                DownPeriod(start: t0.addingTimeInterval(100), end: t0.addingTimeInterval(200)),
                DownPeriod(start: t0.addingTimeInterval(150), end: t0.addingTimeInterval(300)),
                DownPeriod(start: t0.addingTimeInterval(5000), end: t0.addingTimeInterval(5060)),
                // Started before the window: clipped.
                DownPeriod(start: t0.addingTimeInterval(-60), end: t0.addingTimeInterval(30)),
            ])
        let snapshot = try #require(PeriodSnapshot(stats: dto))
        #expect(snapshot.outages.map(\.duration) == [60, 200, 30])
        #expect(snapshot.totalDowntime == 290)
        #expect(snapshot.responseTimes == .init(average: 20, p99: 80, minimum: 10, maximum: 90))
        #expect(snapshot.checkedFrom == t0)
    }

    @Test func newMonitorIsCheckedFromItsFirstCheck() throws {
        let firstCheck = t0.addingTimeInterval(7 * 86_400 - 600)
        let dto = UptimeStatsDTO(
            monitorId: 1, periodType: .sevenDays, periodStart: t0, periodEnd: t0.addingTimeInterval(7 * 86_400),
            intervalMinutes: 60, totalChecks: 40, successfulChecks: 0, uptimePercentage: 0,
            minResponseTimeMs: nil, maxResponseTimeMs: nil, avgResponseTimeMs: nil, p99ResponseTimeMs: nil,
            responseTimeDataPoints: [], statusDownPeriods: [], firstCheckAt: firstCheck)
        let snapshot = try #require(PeriodSnapshot(stats: dto))
        #expect(snapshot.checkedFrom == firstCheck)
    }
}
