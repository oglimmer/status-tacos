import Foundation
import SwiftUI

/// What the user needs to see first. The order is the order of the groups in the list:
/// down, no data, paused, up.
enum MonitorHealth: Int, Comparable, CaseIterable, Sendable {
    case down
    /// Checked, but no result yet.
    case pending
    /// INACTIVE: not checked.
    case paused
    case up

    static func < (lhs: Self, rhs: Self) -> Bool { lhs.rawValue < rhs.rawValue }

    var title: String {
        switch self {
        case .down: "Down"
        case .pending: "No data"
        case .up: "Up"
        case .paused: "Paused"
        }
    }

    var color: Color {
        switch self {
        case .down: Brand.down
        case .pending: .gray
        case .up: Brand.up
        case .paused: Brand.paused
        }
    }

    var symbol: String {
        switch self {
        case .down: "xmark.circle.fill"
        case .pending: "questionmark.circle.fill"
        case .up: "checkmark.circle.fill"
        case .paused: "pause.circle.fill"
        }
    }
}

extension MonitorState {
    /// The states a user can pick, in the order of the web app.
    static let selectable: [MonitorState] = [.active, .silent, .inactive]

    var title: String {
        switch self {
        case .active: "Active"
        case .silent: "Silent"
        case .inactive: "Paused"
        case .unknown: "Unknown"
        }
    }

    var explanation: String {
        switch self {
        case .active: "Checks the URL and sends alerts."
        case .silent: "Checks the URL. Sends no alerts."
        case .inactive: "Does not check the URL."
        case .unknown: ""
        }
    }

    var symbol: String {
        switch self {
        case .active: "bell.fill"
        case .silent: "bell.slash.fill"
        case .inactive: "pause.circle.fill"
        case .unknown: "questionmark.circle"
        }
    }

    var color: Color {
        switch self {
        case .active: Brand.up
        case .silent: Brand.silent
        case .inactive: Brand.paused
        case .unknown: .secondary
        }
    }
}

/// Uptime and downtime of one monitor in the time frame of the list.
struct MonitorUptime: Sendable {
    let monitorId: Int
    let uptimePercentage: Double?
    let downPeriods: [DownPeriod]
    /// Nil when the server sends no window.
    let start: Date?
    let end: Date?
    let firstCheckAt: Date?
    let totalChecks: Int?

    /// 7 and 90 days.
    init(stats: UptimeStatsDTO) {
        monitorId = stats.monitorId
        uptimePercentage = stats.uptimePercentage
        downPeriods = stats.statusDownPeriods ?? []
        start = stats.periodStart
        end = stats.periodEnd
        firstCheckAt = stats.firstCheckAt
        totalChecks = stats.totalChecks
    }

    /// The 24h history has no window: it ends at the time of the request.
    init(history: ResponseTimeHistoryDTO, fetchedAt: Date) {
        monitorId = history.monitorId
        uptimePercentage = history.uptimePercentage24h
        downPeriods = history.statusDownPeriods ?? []
        start = fetchedAt.addingTimeInterval(-24 * 60 * 60)
        end = fetchedAt
        firstCheckAt = history.firstCheckAt
        totalChecks = history.totalChecks24h
    }
}

/// One monitor with its current status and its uptime in one time frame, built from 3 API calls.
struct MonitorSummary: Identifiable, Sendable {
    let id: Int
    let name: String
    let url: String
    let tenant: Tenant?
    let state: MonitorState
    let alertingThreshold: Int?
    let createdAt: Date?
    let status: MonitorStatusDTO?
    let uptime: MonitorUptime?
    /// Nil when not set, and from older servers.
    var environment: MonitorEnvironment? = nil
    /// The time frame of `uptime`.
    var timeframe: Timeframe = .week

    var health: MonitorHealth {
        if state == .inactive { return .paused }
        switch status?.currentStatus {
        case .down: return .down
        case .up: return .up
        default: return .pending
        }
    }

    func with(state: MonitorState) -> MonitorSummary {
        MonitorSummary(
            id: id, name: name, url: url, tenant: tenant, state: state,
            alertingThreshold: alertingThreshold, createdAt: createdAt, status: status, uptime: uptime,
            environment: environment, timeframe: timeframe)
    }

    var host: String { URL(string: url)?.host() ?? url }

    /// Splits the URL: "https://a.com/b?c" gives "https", "a.com" and "/b?c".
    /// The path is nil when there is nothing after the host (or only "/").
    var urlParts: (scheme: String?, host: String, path: String?) {
        guard let schemeEnd = url.range(of: "://") else { return (nil, url, nil) }
        let scheme = url[..<schemeEnd.lowerBound].lowercased()
        let afterScheme = url[schemeEnd.upperBound...]
        guard let pathStart = afterScheme.firstIndex(where: { "/?#".contains($0) }) else {
            return (scheme, String(afterScheme), nil)
        }
        let path = String(afterScheme[pathStart...])
        return (scheme, String(afterScheme[..<pathStart]), path == "/" ? nil : path)
    }

    /// Window of the uptime. For 7 and 90 days the backend aligns the start to a full UTC hour.
    var uptimeWindow: (start: Date, end: Date, checkedFrom: Date?)? {
        guard let start = uptime?.start, let end = uptime?.end, end > start else { return nil }
        let checkedFrom = DowntimeTimeline.checkedFrom(
            firstCheckAt: uptime?.firstCheckAt, totalChecks: uptime?.totalChecks, start: start)
        return (start, end, checkedFrom)
    }

    /// Down time in the time frame of the list. Nil without uptime data.
    var totalDowntime: TimeInterval? {
        guard let window = uptimeWindow, let periods = uptime?.downPeriods else { return nil }
        return DowntimeTimeline.totalDowntime(periods: periods, start: window.start, end: window.end)
    }

    /// Joins the API results by monitor ID and sorts by health (see `sortedForDisplay`).
    static func merge(
        monitors: [MonitorDTO], statuses: [MonitorStatusDTO], uptimes: [MonitorUptime],
        timeframe: Timeframe = .week
    ) -> [MonitorSummary] {
        let statusByID = Dictionary(statuses.map { ($0.monitorId, $0) }) { first, _ in first }
        let statsByID = Dictionary(uptimes.map { ($0.monitorId, $0) }) { first, _ in first }
        return monitors
            .map { monitor in
                MonitorSummary(
                    id: monitor.id,
                    name: monitor.name,
                    url: monitor.url,
                    tenant: monitor.tenant,
                    state: monitor.state,
                    alertingThreshold: monitor.alertingThreshold,
                    createdAt: monitor.createdAt,
                    status: statusByID[monitor.id],
                    uptime: statsByID[monitor.id],
                    environment: monitor.environment,
                    timeframe: timeframe)
            }
            .sortedForDisplay()
    }
}

/// Sort order inside the health groups of the monitor list. The raw value is stored in the user
/// defaults; an unknown value (like the old "status") gives the default.
enum MonitorSort: String, CaseIterable, Sendable {
    case name
    /// The latest failed check.
    case lastDown
    /// Down time in the time frame of the list.
    case totalDowntime
    /// Tenant name, then monitor name.
    case tenant
    case created

    var title: String {
        switch self {
        case .name: "Name"
        case .lastDown: "Last downtime"
        case .totalDowntime: "Total downtime"
        case .tenant: "Tenant, then name"
        case .created: "Created"
        }
    }

    var symbol: String {
        switch self {
        case .name: "textformat"
        case .lastDown: "exclamationmark.triangle"
        case .totalDowntime: "hourglass"
        case .tenant: "building.2"
        case .created: "calendar"
        }
    }

    /// The direction a sort starts with: dates show the newest first, downtime the most first.
    var startsAscending: Bool {
        switch self {
        case .lastDown, .totalDowntime, .created: false
        case .name, .tenant: true
        }
    }
}

extension MonitorSummary {
    /// Monitors without a value for the sort come last: never down, no uptime data, no tenant, or an
    /// older server.
    fileprivate func hasValue(for sort: MonitorSort) -> Bool {
        switch sort {
        case .name: true
        // Inactive monitors have no status.
        case .lastDown: status?.lastDownAt != nil
        case .totalDowntime: totalDowntime != nil
        case .tenant: tenant != nil
        case .created: createdAt != nil
        }
    }

    /// prod < stage < dev < none.
    fileprivate var environmentRank: Int { environment?.rank ?? MonitorEnvironment.allCases.count }

    /// Ascending order by the sort alone. Ties go on by name.
    fileprivate func compare(to other: MonitorSummary, by sort: MonitorSort) -> ComparisonResult {
        switch sort {
        case .name: .orderedSame
        case .lastDown: comparison(status?.lastDownAt ?? .distantPast, other.status?.lastDownAt ?? .distantPast)
        case .totalDowntime: comparison(totalDowntime ?? 0, other.totalDowntime ?? 0)
        case .tenant: (tenant?.name ?? "").localizedStandardCompare(other.tenant?.name ?? "")
        case .created: comparison(createdAt ?? .distantPast, other.createdAt ?? .distantPast)
        }
    }
}

extension Array where Element == MonitorSummary {
    /// By health (down first), then by environment (production first, none last), then by name and ID.
    func sortedForDisplay() -> [MonitorSummary] {
        sorted { lhs, rhs in
            var order = comparison(lhs.health, rhs.health)
            if order == .orderedSame { order = comparison(lhs.environmentRank, rhs.environmentRank) }
            if order == .orderedSame { order = lhs.name.localizedStandardCompare(rhs.name) }
            if order == .orderedSame { order = comparison(lhs.id, rhs.id) }
            return order == .orderedAscending
        }
    }

    /// Sorts by the key, then by name, then by ID. Descending reverses all of it. Monitors without a
    /// value for the key come last in both directions.
    func sorted(by sort: MonitorSort, ascending: Bool) -> [MonitorSummary] {
        sorted { lhs, rhs in
            let lhsHasValue = lhs.hasValue(for: sort)
            if lhsHasValue != rhs.hasValue(for: sort) { return lhsHasValue }
            var order = lhs.compare(to: rhs, by: sort)
            if order == .orderedSame { order = lhs.name.localizedStandardCompare(rhs.name) }
            if order == .orderedSame { order = comparison(lhs.id, rhs.id) }
            return order == (ascending ? .orderedAscending : .orderedDescending)
        }
    }
}

private func comparison<T: Comparable>(_ lhs: T, _ rhs: T) -> ComparisonResult {
    lhs < rhs ? .orderedAscending : lhs > rhs ? .orderedDescending : .orderedSame
}
