import Foundation
import SwiftUI

/// What the user needs to see first. The order is the sort order of the list.
enum MonitorHealth: Int, Comparable, CaseIterable, Sendable {
    case down
    /// Checked, but no result yet.
    case pending
    case up
    /// INACTIVE: not checked.
    case paused

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
        case .down: .red
        case .pending: .gray
        case .up: .green
        case .paused: .secondary
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
        case .inactive: "Inactive"
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
        case .active: .green
        case .silent: .orange
        case .inactive: .gray
        case .unknown: .secondary
        }
    }
}

/// One monitor with its current status and its last 7 days, built from 3 API calls.
struct MonitorSummary: Identifiable, Sendable {
    let id: Int
    let name: String
    let url: String
    let tenant: Tenant?
    let state: MonitorState
    let alertingThreshold: Int?
    let status: MonitorStatusDTO?
    let week: UptimeStatsDTO?

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
            alertingThreshold: alertingThreshold, status: status, week: week)
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

    /// Window of the 7-day stats. The backend aligns the start to a full UTC hour.
    var weekWindow: (start: Date, end: Date, checkedFrom: Date?)? {
        guard let start = week?.periodStart, let end = week?.periodEnd, end > start else { return nil }
        let checkedFrom = DowntimeTimeline.checkedFrom(
            firstCheckAt: week?.firstCheckAt, totalChecks: week?.totalChecks, start: start)
        return (start, end, checkedFrom)
    }

    /// Joins the API results by monitor ID and sorts by health, then by name.
    static func merge(
        monitors: [MonitorDTO], statuses: [MonitorStatusDTO], weekStats: [UptimeStatsDTO]
    ) -> [MonitorSummary] {
        let statusByID = Dictionary(statuses.map { ($0.monitorId, $0) }) { first, _ in first }
        let statsByID = Dictionary(weekStats.map { ($0.monitorId, $0) }) { first, _ in first }
        return monitors
            .map { monitor in
                MonitorSummary(
                    id: monitor.id,
                    name: monitor.name,
                    url: monitor.url,
                    tenant: monitor.tenant,
                    state: monitor.state,
                    alertingThreshold: monitor.alertingThreshold,
                    status: statusByID[monitor.id],
                    week: statsByID[monitor.id])
            }
            .sortedForDisplay()
    }
}

extension Array where Element == MonitorSummary {
    func sortedForDisplay() -> [MonitorSummary] {
        sorted { lhs, rhs in
            if lhs.health != rhs.health { return lhs.health < rhs.health }
            let byName = lhs.name.localizedStandardCompare(rhs.name)
            return byName == .orderedSame ? lhs.id < rhs.id : byName == .orderedAscending
        }
    }
}
