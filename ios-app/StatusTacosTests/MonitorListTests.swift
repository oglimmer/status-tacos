import Foundation
import Testing
@testable import StatusTacos

private let now = Date(timeIntervalSince1970: 1_800_000_000)

private func summary(
    _ id: Int, _ name: String, state: MonitorState = .active, status type: StatusType? = .up,
    responseTime: Int? = 154, statusCode: Int? = 200, uptime: Double? = 99.99,
    environment: MonitorEnvironment? = nil
) -> MonitorSummary {
    let status = type.map {
        MonitorStatusDTO(
            monitorId: id, monitorName: name, currentStatus: $0, lastCheckedAt: now, lastUpAt: nil, lastDownAt: nil,
            consecutiveFailures: nil, lastResponseTimeMs: responseTime, lastStatusCode: statusCode,
            outageStartedAt: $0 == .down ? now.addingTimeInterval(-12 * 60) : nil)
    }
    let week = UptimeStatsDTO(
        monitorId: id, periodType: .sevenDays, periodStart: nil, periodEnd: nil, intervalMinutes: nil,
        totalChecks: nil, successfulChecks: nil, uptimePercentage: uptime, minResponseTimeMs: nil,
        maxResponseTimeMs: nil, avgResponseTimeMs: nil, p99ResponseTimeMs: nil, responseTimeDataPoints: nil,
        statusDownPeriods: nil)
    return MonitorSummary(
        id: id, name: name, url: "https://example.com", tenant: nil, state: state, alertingThreshold: nil,
        createdAt: nil, status: status, uptime: MonitorUptime(stats: week), environment: environment)
}

private let mixed = [
    summary(1, "Shop"), summary(2, "Login", status: .down, statusCode: 503),
    summary(3, "New", status: nil), summary(4, "Old", state: .inactive, status: nil),
    summary(5, "API"),
]

struct MonitorListSectionTests {
    @Test func statusSortGroupsByHealthWithCounts() {
        let sections = MonitorListSection.sections(mixed, sort: .status, ascending: true, tenantName: nil)
        #expect(sections.map(\.title) == ["Down (1)", "No data (1)", "Up (2)", "Paused (1)"])
        #expect(sections[2].monitors.map(\.name) == ["API", "Shop"])
    }

    @Test func leavesOutEmptyGroups() {
        let sections = MonitorListSection.sections(
            [summary(1, "Shop"), summary(2, "API")], sort: .status, ascending: true, tenantName: nil)
        #expect(sections.map(\.title) == ["Up (2)"])
    }

    @Test func upGroupCarriesTheTenantName() {
        let sections = MonitorListSection.sections(mixed, sort: .status, ascending: true, tenantName: "Kammer")
        #expect(sections.map(\.title) == ["Down (1)", "No data (1)", "Kammer (2)", "Paused (1)"])
    }

    @Test func descendingReversesTheGroups() {
        let sections = MonitorListSection.sections(mixed, sort: .status, ascending: false, tenantName: nil)
        #expect(sections.map(\.title) == ["Paused (1)", "Up (2)", "No data (1)", "Down (1)"])
    }

    @Test func otherSortsShowOneGroup() {
        let sections = MonitorListSection.sections(mixed, sort: .name, ascending: true, tenantName: "Kammer")
        #expect(sections.map(\.title) == ["All monitors (5)"])
        #expect(sections[0].monitors.map(\.name) == ["API", "Login", "New", "Old", "Shop"])
        #expect(MonitorListSection.sections([], sort: .name, ascending: true, tenantName: nil).isEmpty)
    }
}

struct MonitorCounterStyleTests {
    @Test func downIsRedOnlyWithDownMonitors() {
        #expect(MonitorCounterStyle(health: .down, count: 0).number == .neutral)
        #expect(MonitorCounterStyle(health: .down, count: 0).word == .neutral)
        #expect(MonitorCounterStyle(health: .down, count: 2).number == .down)
        #expect(MonitorCounterStyle(health: .down, count: 2).word == .down)
    }

    @Test func upHasAGreenNumberAndAGreyWord() {
        let style = MonitorCounterStyle(health: .up, count: 23)
        #expect(style.number == .up)
        #expect(style.word == .neutral)
    }

    @Test func pausedIsGrey() {
        let style = MonitorCounterStyle(health: .paused, count: 3)
        #expect(style.number == .neutral)
        #expect(style.word == .neutral)
    }
}

struct MonitorRowTests {
    @Test func detailByHealth() {
        #expect(summary(1, "A").rowDetail(now: now) == .responseTime("154 ms"))
        #expect(summary(1, "A", status: .down, statusCode: 503).rowDetail(now: now)
            == .down("HTTP 503 · down \(Format.durationWords(12 * 60))"))
        #expect(summary(1, "A", status: nil).rowDetail(now: now) == .noData)
        #expect(summary(1, "A", state: .inactive, status: nil).rowDetail(now: now) == .notChecked)
        #expect(MonitorRowDetail.noData.text == "No data")
    }

    @Test func downDurationFollowsTheRefreshTime() {
        let down = summary(1, "A", status: .down, statusCode: 503)
        #expect(down.rowDetail(now: now) != down.rowDetail(now: now.addingTimeInterval(3600)))
    }

    @Test func accessibilityLabelReadsNameEnvironmentStatusAndUptime() {
        let up = summary(1, "Shop", state: .silent, environment: .prod)
        #expect(up.rowAccessibilityLabel(now: now)
            == "Shop, Production, Silent, no alerts, Up, 154 ms, 99.99% uptime in 7 days")
        let paused = summary(2, "Old", state: .inactive, status: nil)
        #expect(paused.rowAccessibilityLabel(now: now) == "Old, Paused")
    }
}
