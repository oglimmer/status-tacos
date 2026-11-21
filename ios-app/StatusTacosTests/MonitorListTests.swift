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
    @Test func groupsByHealthDownNoDataPausedUp() {
        let sections = MonitorListSection.sections(mixed, sort: .name, ascending: true)
        #expect(sections.map(\.health) == [.down, .pending, .paused, .up])
        #expect(sections.map { $0.monitors.map(\.name) } == [["Login"], ["New"], ["Old"], ["API", "Shop"]])
    }

    @Test func leavesOutEmptyGroups() {
        let sections = MonitorListSection.sections(
            [summary(1, "Shop"), summary(2, "Old", state: .inactive, status: nil)], sort: .name, ascending: true)
        #expect(sections.map(\.health) == [.paused, .up])
        #expect(MonitorListSection.sections([], sort: .name, ascending: true).isEmpty)
    }

    @Test func sortsInsideTheGroupsInBothDirections() {
        let monitors = mixed + [summary(6, "Cart", status: .down), summary(7, "Blog")]
        let ascending = MonitorListSection.sections(monitors, sort: .name, ascending: true)
        #expect(ascending.map { $0.monitors.map(\.name) }
            == [["Cart", "Login"], ["New"], ["Old"], ["API", "Blog", "Shop"]])
        let descending = MonitorListSection.sections(monitors, sort: .name, ascending: false)
        #expect(descending.map(\.health) == [.down, .pending, .paused, .up])
        #expect(descending.map { $0.monitors.map(\.name) }
            == [["Login", "Cart"], ["New"], ["Old"], ["Shop", "Blog", "API"]])
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
