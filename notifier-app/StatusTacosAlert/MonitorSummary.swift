//
//  MonitorSummary.swift
//  StatusTacosAlert
//
//  The monitor list logic, copied from the iOS app (ios-app/StatusTacos/Monitors/MonitorSummary.swift)
//  and the filters of the web app (frontend/src/views/DashboardView.vue).
//

import Foundation
import SwiftUI

/// What the user needs to see first. The order is the sort order of the list.
enum MonitorHealth: Int, Comparable, CaseIterable {
    case down
    /// Checked, but no result yet.
    case pending
    case up
    /// INACTIVE: not checked.
    case paused

    static func < (lhs: Self, rhs: Self) -> Bool { lhs.rawValue < rhs.rawValue }

    var title: String {
        switch self {
        case .down: return "Down"
        case .pending: return "No data"
        case .up: return "Up"
        case .paused: return "Paused"
        }
    }

    var color: Color {
        switch self {
        case .down: return Brand.down
        case .pending: return .gray
        case .up: return Brand.up
        case .paused: return Brand.paused
        }
    }
}

/// Time frame of the uptime in the list.
enum Timeframe: String, CaseIterable, Identifiable {
    case day = "24h"
    case week = "7d"
    case quarter = "90d"

    var id: Self { self }

    var title: String {
        switch self {
        case .day: return "24 hours"
        case .week: return "7 days"
        case .quarter: return "90 days"
        }
    }
}

/// The status filter of the web app. "Paused" means silent or inactive.
enum MonitorStatusFilter: String, CaseIterable, Identifiable {
    case all, down, up, paused

    var id: Self { self }

    var title: String {
        switch self {
        case .all: return "All"
        case .down: return "Down"
        case .up: return "Up"
        case .paused: return "Paused"
        }
    }

    func matches(_ monitor: MonitorSummary) -> Bool {
        switch self {
        case .all: return true
        case .down: return monitor.status?.currentStatus == .down
        case .up: return monitor.status?.currentStatus == .up
        case .paused: return monitor.state != .active
        }
    }
}

/// The environment filter of the web app. The raw value is stored in the user defaults.
enum EnvironmentFilter: String, CaseIterable, Identifiable {
    case all, prod, stage, dev, none

    var id: Self { self }

    var title: String {
        switch self {
        case .all: return "All environments"
        case .prod: return "Production"
        case .stage: return "Stage"
        case .dev: return "Development"
        case .none: return "No environment"
        }
    }

    func matches(_ monitor: MonitorSummary) -> Bool {
        switch self {
        case .all: return true
        case .prod: return monitor.environment == .prod
        case .stage: return monitor.environment == .stage
        case .dev: return monitor.environment == .dev
        case .none: return monitor.environment == nil
        }
    }
}

/// One monitor with its current status and its uptime in one time frame.
struct MonitorSummary: Identifiable {
    let id: Int
    let name: String
    let url: String
    let tenant: Tenant?
    let state: MonitorState
    let createdAt: Date?
    let environment: MonitorEnvironment?
    let status: MonitorStatusDTO?
    let uptimePercentage: Double?

    var health: MonitorHealth {
        if state == .inactive { return .paused }
        switch status?.currentStatus {
        case .down: return .down
        case .up: return .up
        default: return .pending
        }
    }

    /// The second line of a row: "154 ms", "HTTP 503 · down 12 min", "No data", "Not checked".
    func detail(now: Date) -> String {
        switch health {
        case .up: return Format.responseTime(status?.lastResponseTimeMs)
        case .down: return status?.downMessage(now: now) ?? "Down"
        case .pending: return "No data"
        case .paused: return "Not checked"
        }
    }

    /// Joins the API results by monitor ID.
    static func merge(monitors: [MonitorDTO], statuses: [MonitorStatusDTO], uptimes: [Int: Double]) -> [MonitorSummary] {
        let statusByID = Dictionary(statuses.map { ($0.monitorId, $0) }) { first, _ in first }
        return monitors.map { monitor in
            MonitorSummary(
                id: monitor.id, name: monitor.name, url: monitor.url, tenant: monitor.tenant,
                state: monitor.state, createdAt: monitor.createdAt, environment: monitor.environment,
                status: statusByID[monitor.id], uptimePercentage: uptimes[monitor.id])
        }
    }
}

/// Sort order of the monitor list. The raw value is stored in the user defaults.
enum MonitorSort: String, CaseIterable {
    /// Grouped by health, down first. In a group: by environment (production first), then by name.
    case status
    case name
    /// The latest failed check.
    case lastDown
    /// Tenant name, then monitor name.
    case tenant
    case created

    var title: String {
        switch self {
        case .status: return "Status"
        case .name: return "Name"
        case .lastDown: return "Last downtime"
        case .tenant: return "Tenant, then name"
        case .created: return "Created"
        }
    }

    /// The direction a sort starts with: dates show the newest first.
    var startsAscending: Bool {
        switch self {
        case .lastDown, .created: return false
        case .status, .name, .tenant: return true
        }
    }
}

extension MonitorSummary {
    /// Monitors without a value for the sort come last: never down, no tenant, or an older server.
    fileprivate func hasValue(for sort: MonitorSort) -> Bool {
        switch sort {
        case .status, .name: return true
        case .lastDown: return status?.lastDownAt != nil
        case .tenant: return tenant != nil
        case .created: return createdAt != nil
        }
    }

    /// prod < stage < dev < none.
    fileprivate var environmentRank: Int { environment?.rank ?? MonitorEnvironment.allCases.count }

    /// Ascending order by the sort alone. Ties go on by name.
    fileprivate func compare(to other: MonitorSummary, by sort: MonitorSort) -> ComparisonResult {
        switch sort {
        case .status:
            let order = comparison(health, other.health)
            return order == .orderedSame ? comparison(environmentRank, other.environmentRank) : order
        case .name: return .orderedSame
        case .lastDown: return comparison(status?.lastDownAt ?? .distantPast, other.status?.lastDownAt ?? .distantPast)
        case .tenant: return (tenant?.name ?? "").localizedStandardCompare(other.tenant?.name ?? "")
        case .created: return comparison(createdAt ?? .distantPast, other.createdAt ?? .distantPast)
        }
    }
}

extension Array where Element == MonitorSummary {
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

/// One group of the monitor list.
struct MonitorListSection: Identifiable {
    let id: String
    let title: String
    let monitors: [MonitorSummary]

    /// The status sort groups by health: down, no data, up, paused (reversed when descending).
    /// Other sorts: one group with all monitors.
    static func sections(_ monitors: [MonitorSummary], sort: MonitorSort, ascending: Bool) -> [MonitorListSection] {
        let sorted = monitors.sorted(by: sort, ascending: ascending)
        guard sort == .status else {
            return sorted.isEmpty ? [] : [MonitorListSection(
                id: "all", title: "All monitors (\(sorted.count))", monitors: sorted)]
        }
        let healths = ascending ? MonitorHealth.allCases : MonitorHealth.allCases.reversed()
        return healths.compactMap { health in
            let rows = sorted.filter { $0.health == health }
            guard !rows.isEmpty else { return nil }
            return MonitorListSection(id: "\(health)", title: "\(health.title) (\(rows.count))", monitors: rows)
        }
    }
}

private func comparison<T: Comparable>(_ lhs: T, _ rhs: T) -> ComparisonResult {
    lhs < rhs ? .orderedAscending : lhs > rhs ? .orderedDescending : .orderedSame
}

extension MonitorStatusDTO {
    /// Why and how long a down monitor is down: "HTTP 503 · down 12 min".
    func downMessage(now: Date) -> String {
        var reason = "Down"
        if let code = lastStatusCode, code > 0 {
            reason = "HTTP \(code)"
        } else if let message = lastErrorMessage?
            .split(whereSeparator: \.isWhitespace).joined(separator: " "), !message.isEmpty {
            reason = message
        }
        guard let since = outageStartedAt ?? lastUpAt else { return reason }
        return "\(reason) · down \(Format.durationWords(now.timeIntervalSince(since)))"
    }
}
