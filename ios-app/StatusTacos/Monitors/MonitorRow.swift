import SwiftUI

/// The second line of a row: what the monitor does right now.
enum MonitorRowDetail: Equatable {
    /// Up: the last response time, "154 ms".
    case responseTime(String)
    /// Down: "HTTP 503 · down 12 min".
    case down(String)
    /// Checked, no result yet.
    case noData
    /// Paused: not checked.
    case notChecked

    var text: String {
        switch self {
        case let .responseTime(text), let .down(text): text
        case .noData: "No data"
        case .notChecked: "Not checked"
        }
    }
}

extension MonitorSummary {
    func rowDetail(now: Date) -> MonitorRowDetail {
        switch health {
        case .up: .responseTime(Format.responseTime(status?.lastResponseTimeMs))
        case .down: .down(status?.downMessage(now: now) ?? "Down")
        case .pending: .noData
        case .paused: .notChecked
        }
    }

    /// Color of the uptime: red while down, else by the level.
    var rowUptimeColor: Color {
        health == .down ? Brand.down : UptimeLevel(uptime?.uptimePercentage).color
    }

    /// What VoiceOver reads for a row: name, environment, state, status, uptime.
    func rowAccessibilityLabel(now: Date, isUpdating: Bool = false) -> String {
        var parts = [name]
        if let environment { parts.append(environment.accessibilityName) }
        if isUpdating { parts.append("Updating") }
        if state == .silent { parts.append("Silent, no alerts") }
        switch health {
        case .up:
            parts.append("Up, \(rowDetail(now: now).text)")
        case .down:
            parts.append(rowDetail(now: now).text)
        case .pending:
            parts.append("No data")
        case .paused:
            parts.append("Paused")
        }
        if health != .paused {
            parts.append("\(Format.uptime(uptime?.uptimePercentage)) uptime in \(timeframe.title)")
        }
        return parts.joined(separator: ", ")
    }
}

struct MonitorRow: View {
    let monitor: MonitorSummary
    var isUpdating = false
    /// Time of the data: the "down 12 min" counts to it.
    var now: Date = .now

    var body: some View {
        VStack(alignment: .leading, spacing: 9) {
            HStack(alignment: .firstTextBaseline, spacing: 12) {
                VStack(alignment: .leading, spacing: 2) {
                    nameLine
                    detailLine
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                trailing
                    .fixedSize()
                    .layoutPriority(1)
            }

            if monitor.health != .paused, let window = monitor.uptimeWindow {
                DowntimeBar(
                    periods: monitor.uptime?.downPeriods ?? [],
                    start: window.start, end: window.end, checkedFrom: window.checkedFrom, height: 7)
                    .clipShape(.capsule)
            }
        }
        .padding(.vertical, 2)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(monitor.rowAccessibilityLabel(now: now, isUpdating: isUpdating))
    }

    private var nameLine: some View {
        HStack(spacing: 6) {
            Text(monitor.name)
                .font(.body)
                .foregroundStyle(.primary)
                .lineLimit(1)
                .truncationMode(.tail)
            if monitor.state == .silent {
                Image(systemName: "bell.slash")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            if isUpdating {
                ProgressView()
                    .controlSize(.mini)
            }
        }
    }

    private var detailLine: some View {
        let detail = monitor.rowDetail(now: now)
        let isDown: Bool = if case .down = detail { true } else { false }
        return HStack(spacing: 6) {
            if let environment = monitor.environment {
                EnvironmentBadge(environment: environment, compact: true)
            }
            Text(detail.text)
                .font(.subheadline.monospacedDigit())
                .foregroundStyle(isDown ? AnyShapeStyle(Brand.down) : AnyShapeStyle(.secondary))
                .lineLimit(1)
                .truncationMode(.tail)
        }
    }

    @ViewBuilder
    private var trailing: some View {
        if monitor.health == .paused {
            Text("Paused")
                .font(.body)
                .foregroundStyle(.secondary)
        } else {
            VStack(alignment: .trailing, spacing: 0) {
                Text(Format.uptime(monitor.uptime?.uptimePercentage))
                    .font(.body.weight(.semibold).monospacedDigit())
                    .foregroundStyle(monitor.rowUptimeColor)
                Text(monitor.timeframe.title)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
    }
}

// MARK: - Previews

#if DEBUG
private enum RowFixtures {
    static let now = Date(timeIntervalSince1970: 1_800_000_000)
    static let weekStart = now.addingTimeInterval(-7 * 86_400)

    static func summary(
        _ id: Int, _ name: String, state: MonitorState = .active, status type: StatusType? = .up,
        responseTime: Int? = 154, statusCode: Int? = 200, uptime: Double? = 99.99,
        environment: MonitorEnvironment? = .prod, downPeriods: [DownPeriod] = []
    ) -> MonitorSummary {
        let status = type.map {
            MonitorStatusDTO(
                monitorId: id, monitorName: name, currentStatus: $0, lastCheckedAt: now,
                lastUpAt: $0 == .down ? now.addingTimeInterval(-12 * 60) : now, lastDownAt: nil,
                consecutiveFailures: $0 == .down ? 4 : 0, lastResponseTimeMs: responseTime,
                lastStatusCode: statusCode, outageStartedAt: $0 == .down ? now.addingTimeInterval(-12 * 60) : nil)
        }
        let week = UptimeStatsDTO(
            monitorId: id, periodType: .sevenDays, periodStart: weekStart, periodEnd: now, intervalMinutes: 60,
            totalChecks: 10_000, successfulChecks: 9_999, uptimePercentage: uptime, minResponseTimeMs: nil,
            maxResponseTimeMs: nil, avgResponseTimeMs: nil, p99ResponseTimeMs: nil, responseTimeDataPoints: nil,
            statusDownPeriods: downPeriods, firstCheckAt: weekStart)
        return MonitorSummary(
            id: id, name: name, url: "https://example.com/health", tenant: nil, state: state,
            alertingThreshold: 3, createdAt: nil, status: status, uptime: MonitorUptime(stats: week), environment: environment)
    }

    static func outage(daysAgo: Double, minutes: Double) -> DownPeriod {
        let start = now.addingTimeInterval(-daysAgo * 86_400)
        return DownPeriod(start: start, end: start.addingTimeInterval(minutes * 60))
    }

    static let rows: [(MonitorSummary, Bool)] = [
        (summary(1, "Anerkennung API", downPeriods: [outage(daysAgo: 4, minutes: 5)]), false),
        (summary(2, "Kammerportal Login", status: .down, statusCode: 503, uptime: 98.71,
                 downPeriods: [outage(daysAgo: 2, minutes: 30), outage(daysAgo: 0.008, minutes: 12)]), false),
        (summary(3, "Anerkennung API", state: .silent, responseTime: 162, uptime: 99.95,
                 environment: .stage, downPeriods: [outage(daysAgo: 1.4, minutes: 0.5)]), false),
        (summary(4, "Anerkennung Frontend", state: .inactive, status: nil, environment: nil), false),
        (summary(5, "Anerkennung API Produktion mit einem sehr langen Namen", responseTime: 43,
                 uptime: 100, environment: .dev), true),
        (summary(6, "Neuer Monitor", status: nil, uptime: nil, environment: nil), false),
    ]
}

#Preview("Rows") {
    List {
        Section("Monitors") {
            ForEach(RowFixtures.rows, id: \.0.id) { monitor, updating in
                MonitorRow(monitor: monitor, isUpdating: updating, now: RowFixtures.now)
            }
        }
    }
    .listStyle(.insetGrouped)
}

#Preview("Up, PROD") {
    List { MonitorRow(monitor: RowFixtures.rows[0].0, now: RowFixtures.now) }
}

#Preview("Down") {
    List { MonitorRow(monitor: RowFixtures.rows[1].0, now: RowFixtures.now) }
}

#Preview("Silent") {
    List { MonitorRow(monitor: RowFixtures.rows[2].0, now: RowFixtures.now) }
}

#Preview("Paused") {
    List { MonitorRow(monitor: RowFixtures.rows[3].0, now: RowFixtures.now) }
}

#Preview("Long name, updating") {
    List { MonitorRow(monitor: RowFixtures.rows[4].0, isUpdating: true, now: RowFixtures.now) }
}

#Preview("Large text") {
    List {
        MonitorRow(monitor: RowFixtures.rows[1].0, now: RowFixtures.now)
        MonitorRow(monitor: RowFixtures.rows[4].0, now: RowFixtures.now)
    }
    .dynamicTypeSize(.accessibility2)
}
#endif
