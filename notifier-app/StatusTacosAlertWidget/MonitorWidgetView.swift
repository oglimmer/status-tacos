//
//  MonitorWidgetView.swift
//  StatusTacosAlertWidget
//

import SwiftUI
import WidgetKit

/// The widget content. Size and rendering mode are parameters, so a preview can set them.
struct MonitorWidgetView: View {
    let snapshot: MonitorSnapshot?
    let family: WidgetFamily
    /// False on a tinted or vibrant desktop: macOS then draws the widget in one color.
    let isFullColor: Bool

    var body: some View {
        if let snapshot, !snapshot.monitors.isEmpty {
            MonitorBoard(snapshot: snapshot, family: family, isFullColor: isFullColor)
        } else {
            EmptyBoard(isSignedOut: snapshot == nil || snapshot?.notice == .signedOut, family: family)
        }
    }
}

// MARK: - Board

private struct MonitorBoard: View {
    let snapshot: MonitorSnapshot
    let family: WidgetFamily
    let isFullColor: Bool

    private var down: [MonitorSnapshot.Monitor] {
        snapshot.monitors.filter { $0.status == .down }
            .sorted { ($0.downSince ?? .distantFuture) < ($1.downSince ?? .distantFuture) }
    }

    private var others: [MonitorSnapshot.Monitor] {
        snapshot.monitors.filter { $0.status != .down }
            .sorted { $0.name.localizedStandardCompare($1.name) == .orderedAscending }
    }

    /// Down first, the longest outage on top.
    private var ordered: [MonitorSnapshot.Monitor] { down + others }

    private var showsTenant: Bool {
        Set(snapshot.monitors.map(\.tenantName)).count > 1
    }

    /// Old data stays visible, but dimmed, so it does not look live.
    private var isStale: Bool { snapshot.notice != nil }

    var body: some View {
        Group {
            switch family {
            case .systemSmall: small
            case .systemMedium: medium
            default: large
            }
        }
    }

    private var small: some View {
        VStack(alignment: .leading, spacing: 0) {
            SignPlate(downCount: down.count, size: 17, isLive: !isStale, isFullColor: isFullColor)
            Text(summary)
                .font(.caption2)
                .foregroundStyle(Brand.inkSoft)
                .padding(.top, 7)

            Spacer(minLength: 8)

            Group {
                if down.isEmpty {
                    TileBoard(monitors: ordered, isFullColor: isFullColor)
                } else {
                    MonitorRows(monitors: down, maxRows: 3, showsTenant: false, compact: true, isFullColor: isFullColor)
                }
            }
            .opacity(isStale ? 0.45 : 1)

            if let notice = snapshot.notice {
                NoticeLine(notice: notice).padding(.top, 6)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }

    private var medium: some View {
        HStack(alignment: .top, spacing: 16) {
            VStack(alignment: .leading, spacing: 0) {
                SignPlate(downCount: down.count, size: 17, isLive: !isStale, isFullColor: isFullColor)
                Text(summary)
                    .font(.caption2)
                    .foregroundStyle(Brand.inkSoft)
                    .padding(.top, 7)
                Spacer(minLength: 8)
                TileBoard(monitors: ordered, isFullColor: isFullColor)
                    .opacity(isStale ? 0.45 : 1)
                if let notice = snapshot.notice {
                    NoticeLine(notice: notice).padding(.top, 6)
                }
            }
            .frame(width: 128, alignment: .leading)
            .frame(maxHeight: .infinity, alignment: .topLeading)

            MonitorRows(monitors: ordered, maxRows: 6, showsTenant: showsTenant, compact: false, isFullColor: isFullColor)
                .opacity(isStale ? 0.45 : 1)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        }
    }

    private var large: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .center) {
                SignPlate(downCount: down.count, size: 20, isLive: !isStale, isFullColor: isFullColor)
                Spacer(minLength: 8)
                Text(summary)
                    .font(.caption)
                    .foregroundStyle(Brand.inkSoft)
            }

            TileBoard(monitors: ordered, isFullColor: isFullColor)
                .opacity(isStale ? 0.45 : 1)
                .padding(.top, 14)

            Rectangle()
                .fill(Brand.inkSoft.opacity(0.18))
                .frame(height: 1)
                .padding(.vertical, 12)

            MonitorRows(monitors: ordered, maxRows: 13, showsTenant: showsTenant, compact: false, isFullColor: isFullColor)
                .opacity(isStale ? 0.45 : 1)

            Spacer(minLength: 0)

            if let notice = snapshot.notice {
                NoticeLine(notice: notice).padding(.top, 6)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }

    private var summary: String {
        let total = snapshot.monitors.count
        if down.isEmpty {
            return total == 1 ? "1 monitor" : "\(total) monitors"
        }
        let up = snapshot.monitors.filter { $0.status == .up }.count
        return "\(up) of \(total) up"
    }
}

// MARK: - Sign

/// The verdict as a taco-stand sign: Bungee on a teal or salsa plate, with an ink outline
/// and a hard masa shade, like the buttons on the website. When the data is old, the sign light is off.
private struct SignPlate: View {
    let downCount: Int
    let size: CGFloat
    let isLive: Bool
    let isFullColor: Bool

    private var isLit: Bool { isFullColor && isLive }

    private var text: String { downCount == 0 ? "All up" : "\(downCount) down" }

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: size * 0.42, style: .continuous)
        Text(text)
            .font(Brand.sign(size))
            .foregroundStyle(isLit ? (downCount == 0 ? Brand.inkOnColor : Brand.paperOnColor) : Brand.ink)
            .lineLimit(1)
            .minimumScaleFactor(0.7)
            .padding(.horizontal, size * 0.55)
            .padding(.top, size * 0.30)
            .padding(.bottom, size * 0.22)
            .background {
                if isLit {
                    shape.fill(downCount == 0 ? Brand.teal : Brand.down)
                        .background(shape.fill(Brand.masa).offset(x: 2.5, y: 2.5))
                } else if isFullColor {
                    shape.fill(Brand.inkSoft.opacity(0.14))
                }
            }
            .overlay(shape.strokeBorder(isLit ? Brand.inkOnColor : Brand.inkSoft, lineWidth: 1.5))
            .widgetAccentable()
            .accessibilityLabel(downCount == 0 ? "All monitors up" : "\(downCount) monitors down")
    }
}

// MARK: - Tiles

/// One tile per monitor. Up tiles are quiet, down tiles light up.
private struct TileBoard: View {
    let monitors: [MonitorSnapshot.Monitor]
    let isFullColor: Bool

    private var tileSize: CGFloat {
        switch monitors.count {
        case ..<25: return 12
        case ..<61: return 9
        default: return 6
        }
    }

    var body: some View {
        let gap = tileSize / 3
        LazyVGrid(columns: [GridItem(.adaptive(minimum: tileSize, maximum: tileSize), spacing: gap, alignment: .leading)],
                  alignment: .leading, spacing: gap) {
            ForEach(Array(monitors.enumerated()), id: \.offset) { _, monitor in
                RoundedRectangle(cornerRadius: tileSize * 0.28, style: .continuous)
                    .fill(fill(for: monitor.status))
                    .frame(width: tileSize, height: tileSize)
            }
        }
        .accessibilityHidden(true)
    }

    private func fill(for status: MonitorSnapshot.Status) -> Color {
        switch status {
        case .up: return isFullColor ? Brand.up.opacity(0.32) : Brand.ink.opacity(0.25)
        case .down: return isFullColor ? Brand.down : Brand.ink
        case .unknown: return Brand.inkSoft.opacity(0.18)
        }
    }
}

// MARK: - Rows

private struct MonitorRows: View {
    let monitors: [MonitorSnapshot.Monitor]
    let maxRows: Int
    let showsTenant: Bool
    let compact: Bool
    let isFullColor: Bool

    var body: some View {
        // When more rows exist than fit, the last row says how many are hidden.
        let fits = monitors.count <= maxRows
        let shown = monitors.prefix(fits ? maxRows : maxRows - 1)
        VStack(alignment: .leading, spacing: compact ? 4 : 5) {
            ForEach(Array(shown.enumerated()), id: \.offset) { _, monitor in
                row(monitor)
            }
            if !fits {
                Text("\(monitors.count - shown.count) more")
                    .font(.caption2)
                    .foregroundStyle(Brand.inkSoft)
                    .padding(.leading, 17)
            }
        }
    }

    private func row(_ monitor: MonitorSnapshot.Monitor) -> some View {
        let isDown = monitor.status == .down
        return HStack(alignment: .firstTextBaseline, spacing: 6) {
            Image(systemName: symbol(for: monitor.status))
                .font(.system(size: 10, weight: .bold))
                .foregroundStyle(color(for: monitor.status))
                .frame(width: 11)
                .widgetAccentable(isDown)

            VStack(alignment: .leading, spacing: 0) {
                Text(monitor.name)
                    .font(.system(size: 12, weight: isDown ? .semibold : .regular))
                    .foregroundStyle(isDown ? Brand.ink : Brand.inkSoft)
                    .lineLimit(1)
                if compact, isDown, let since = monitor.downSince {
                    Text("since \(since.formatted(date: .omitted, time: .shortened))")
                        .font(.system(size: 10).monospacedDigit())
                        .foregroundStyle(Brand.inkSoft)
                }
            }

            Spacer(minLength: 4)

            if !compact {
                if isDown, let since = monitor.downSince {
                    // Red time on a down row reads as "down since". It leaves room for the name.
                    Text(since.formatted(date: .omitted, time: .shortened))
                        .font(.system(size: 10, weight: .medium).monospacedDigit())
                        .foregroundStyle(isFullColor ? Brand.down : Brand.ink)
                } else if showsTenant {
                    Text(monitor.tenantName)
                        .font(.system(size: 10))
                        .foregroundStyle(Brand.inkSoft.opacity(0.8))
                        .lineLimit(1)
                }
            }
        }
        .accessibilityElement(children: .combine)
    }

    private func symbol(for status: MonitorSnapshot.Status) -> String {
        switch status {
        case .up: return "checkmark"
        case .down: return "xmark"
        case .unknown: return "questionmark"
        }
    }

    private func color(for status: MonitorSnapshot.Status) -> Color {
        guard isFullColor else { return Brand.ink }
        switch status {
        case .up: return Brand.up
        case .down: return Brand.down
        case .unknown: return Brand.inkSoft
        }
    }
}

// MARK: - Notice and empty state

private struct NoticeLine: View {
    let notice: MonitorSnapshot.Notice

    var body: some View {
        Label(text, systemImage: symbol)
            .font(.system(size: 10, weight: .medium))
            .foregroundStyle(Brand.inkSoft)
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

private struct EmptyBoard: View {
    let isSignedOut: Bool
    let family: WidgetFamily

    var body: some View {
        VStack(spacing: 10) {
            Image("Taco")
                .resizable()
                .scaledToFit()
                .frame(height: family == .systemSmall ? 52 : 64)
                .widgetAccentable()
            Text(isSignedOut ? "Sign in to Status Tacos Alert to see your monitors." : "No monitors yet. Add one in Status Tacos.")
                .font(.caption)
                .foregroundStyle(Brand.inkSoft)
                .multilineTextAlignment(.center)
                .frame(maxWidth: 220)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}
