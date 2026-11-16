//
//  MonitorWidgetView.swift
//  StatusTacosAlertWidget
//

import SwiftUI
import WidgetKit

/// The widget content. Size and rendering mode are parameters, so a preview can set them.
struct MonitorWidgetView: View {
    let snapshot: MonitorSnapshot?
    let configuration: MonitorWidgetIntent
    let family: WidgetFamily
    /// False on a tinted or vibrant desktop: macOS then draws the widget in one color.
    let isFullColor: Bool

    var body: some View {
        if let snapshot, !snapshot.monitors.isEmpty {
            MonitorList(snapshot: snapshot, configuration: configuration, family: family, isFullColor: isFullColor)
        } else {
            EmptyState(text: snapshot == nil || snapshot?.notice == .signedOut
                       ? "Sign in to Status Tacos Alert to see your monitors."
                       : "No monitors yet. Add one in Status Tacos.")
        }
    }
}

// MARK: - List

private struct MonitorList: View {
    let snapshot: MonitorSnapshot
    let configuration: MonitorWidgetIntent
    let family: WidgetFamily
    let isFullColor: Bool

    /// Down first, the longest outage on top. Then by name.
    private var monitors: [MonitorSnapshot.Monitor] {
        configuration.filter(snapshot.monitors).sorted { a, b in
            if a.isDown != b.isDown { return a.isDown }
            if a.isDown, a.downSince != b.downSince {
                return (a.downSince ?? .distantFuture) < (b.downSince ?? .distantFuture)
            }
            return a.name.localizedStandardCompare(b.name) == .orderedAscending
        }
    }

    /// Old data stays visible, but dimmed, so it does not look live.
    private var isStale: Bool { snapshot.notice != nil }

    private var maxRows: Int {
        switch family {
        case .systemSmall: return 4
        case .systemMedium: return 6
        default: return 16
        }
    }

    var body: some View {
        let monitors = monitors
        let downCount = monitors.filter(\.isDown).count
        VStack(alignment: .leading, spacing: 8) {
            Header(downCount: downCount, total: monitors.count, filter: configuration.filterSummary,
                   isCompact: family == .systemSmall, isFullColor: isFullColor)

            Group {
                if monitors.isEmpty {
                    Text(configuration.mode == .down ? "No monitor is down." : "No monitor matches the filter.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                } else if family == .systemSmall, downCount == 0 {
                    // Not enough room for a useful list. The header says it all.
                    EmptyView()
                } else {
                    Rows(monitors: family == .systemSmall ? monitors.filter(\.isDown) : monitors,
                         maxRows: maxRows,
                         showsTenant: family != .systemSmall && Set(monitors.map(\.tenantName)).count > 1,
                         isFullColor: isFullColor)
                }
            }
            .opacity(isStale ? 0.5 : 1)

            Spacer(minLength: 0)

            if let notice = snapshot.notice {
                NoticeLine(notice: notice)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }
}

// MARK: - Header

private struct Header: View {
    let downCount: Int
    let total: Int
    let filter: String?
    let isCompact: Bool
    let isFullColor: Bool

    var body: some View {
        let isUp = downCount == 0
        VStack(alignment: .leading, spacing: 1) {
            HStack(spacing: 5) {
                Image(systemName: isUp ? "checkmark.circle.fill" : "xmark.circle.fill")
                    .foregroundStyle(isFullColor ? (isUp ? Brand.up : Brand.down) : .primary)
                    .widgetAccentable()
                Text(isUp ? "All up" : "\(downCount) down")
                    .fontWeight(.semibold)
                if !isCompact {
                    Spacer(minLength: 4)
                    Text(countText).font(.caption).foregroundStyle(.secondary)
                }
            }
            .font(isCompact ? .title3 : .headline)

            if isCompact || filter != nil {
                Text([isCompact ? countText : nil, filter].compactMap { $0 }.joined(separator: " · "))
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
        }
        .accessibilityElement(children: .combine)
    }

    private var countText: String { total == 1 ? "1 monitor" : "\(total) monitors" }
}

// MARK: - Rows

private struct Rows: View {
    let monitors: [MonitorSnapshot.Monitor]
    let maxRows: Int
    let showsTenant: Bool
    let isFullColor: Bool

    var body: some View {
        // When more rows exist than fit, the last row says how many are hidden.
        let fits = monitors.count <= maxRows
        let shown = monitors.prefix(fits ? maxRows : maxRows - 1)
        VStack(alignment: .leading, spacing: 3) {
            ForEach(Array(shown.enumerated()), id: \.offset) { _, monitor in
                row(monitor)
            }
            if !fits {
                Text("+ \(monitors.count - shown.count) more")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                    .padding(.leading, 12)
            }
        }
    }

    private func row(_ monitor: MonitorSnapshot.Monitor) -> some View {
        HStack(spacing: 6) {
            Circle()
                .fill(color(for: monitor))
                .frame(width: 6, height: 6)
                .widgetAccentable(monitor.isDown)

            Text(monitor.name)
                .font(.system(size: 12, weight: monitor.isDown ? .semibold : .regular))
                .foregroundStyle(monitor.isPaused == true ? .secondary : .primary)
                .lineLimit(1)

            Spacer(minLength: 4)

            Text(detail(for: monitor))
                .font(.system(size: 10).monospacedDigit())
                .foregroundStyle(detailStyle(for: monitor))
                .lineLimit(1)
        }
        .accessibilityElement(children: .combine)
    }

    /// Down: since when. Else the tenant (when there are more) and the 24h uptime.
    private func detail(for monitor: MonitorSnapshot.Monitor) -> String {
        if monitor.isDown, let since = monitor.downSince {
            return "since \(since.formatted(date: .omitted, time: .shortened))"
        }
        if monitor.isPaused == true { return "paused" }
        let uptime = monitor.uptime24h.map { Format.uptime($0) }
        return [showsTenant ? monitor.tenantName : nil, uptime].compactMap { $0 }.joined(separator: " · ")
    }

    /// Calm by default. Red when down, and the warning colors of the app when the uptime is low.
    private func detailStyle(for monitor: MonitorSnapshot.Monitor) -> AnyShapeStyle {
        guard isFullColor else { return AnyShapeStyle(.secondary) }
        if monitor.isDown { return AnyShapeStyle(Brand.down) }
        if let uptime = monitor.uptime24h, uptime < 95, monitor.isPaused != true {
            return AnyShapeStyle(Format.uptimeColor(uptime))
        }
        return AnyShapeStyle(.secondary)
    }

    private func color(for monitor: MonitorSnapshot.Monitor) -> Color {
        guard isFullColor else { return monitor.isDown ? .primary : .secondary }
        if monitor.isPaused == true { return Brand.paused }
        switch monitor.status {
        case .up: return Brand.up
        case .down: return Brand.down
        case .unknown: return Brand.paused
        }
    }
}

// MARK: - Notice and empty state

private struct NoticeLine: View {
    let notice: MonitorSnapshot.Notice

    var body: some View {
        Label(text, systemImage: symbol)
            .font(.caption2)
            .foregroundStyle(.secondary)
            .lineLimit(1)
            .minimumScaleFactor(0.8)
    }

    private var text: String {
        switch notice {
        case .noConnection: return "No connection to the server"
        case .signedOut: return "Signed out"
        case .appNotRunning: return "App closed, last known state"
        }
    }

    private var symbol: String {
        switch notice {
        case .noConnection: return "wifi.slash"
        case .signedOut: return "person.crop.circle.badge.xmark"
        case .appNotRunning: return "moon.zzz"
        }
    }
}

private struct EmptyState: View {
    let text: String

    var body: some View {
        Text(text)
            .font(.caption)
            .foregroundStyle(.secondary)
            .multilineTextAlignment(.center)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}
