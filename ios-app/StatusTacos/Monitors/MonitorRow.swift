import SwiftUI

struct MonitorRow: View {
    let monitor: MonitorSummary
    var showsTenant = false
    var isUpdating = false

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .firstTextBaseline, spacing: 10) {
                Group {
                    if isUpdating {
                        ProgressView()
                    } else {
                        Image(systemName: monitor.health.symbol)
                            .foregroundStyle(monitor.health.color)
                            .imageScale(.large)
                    }
                }
                .frame(width: 24)
                .accessibilityLabel(isUpdating ? "Updating" : monitor.health.title)

                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 4) {
                        Text(monitor.name)
                            .font(.headline)
                            .lineLimit(1)
                        if monitor.state == .silent {
                            Image(systemName: "bell.slash.fill")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                                .accessibilityLabel("Silent, no alerts")
                        }
                    }
                    Text(subtitle)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }

                Spacer(minLength: 8)

                VStack(alignment: .trailing, spacing: 2) {
                    trailingValue
                    if monitor.health != .paused {
                        Text("\(Format.uptime(monitor.week?.uptimePercentage)) 7d")
                            .font(.caption.monospacedDigit())
                            .foregroundStyle(UptimeLevel(monitor.week?.uptimePercentage).color)
                    }
                }
            }

            if monitor.health == .down, let status = monitor.status {
                downDetails(status)
            }

            if monitor.health != .paused, let window = monitor.weekWindow {
                DowntimeBar(
                    periods: monitor.week?.statusDownPeriods ?? [],
                    start: window.start, end: window.end, height: 6)
            }
        }
        .padding(.vertical, 4)
    }

    private var subtitle: String {
        guard showsTenant, let tenant = monitor.tenant else { return monitor.host }
        return "\(tenant.name) · \(monitor.host)"
    }

    @ViewBuilder
    private var trailingValue: some View {
        switch monitor.health {
        case .down:
            Text(Format.statusCode(monitor.status?.lastStatusCode))
                .font(.subheadline.weight(.semibold).monospacedDigit())
                .foregroundStyle(.red)
        case .up:
            Text(Format.responseTime(monitor.status?.lastResponseTimeMs))
                .font(.subheadline.monospacedDigit())
        case .pending:
            Text("Waiting")
                .font(.subheadline)
                .foregroundStyle(.secondary)
        case .paused:
            Text("Paused")
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
    }

    private func downDetails(_ status: MonitorStatusDTO) -> some View {
        HStack(spacing: 4) {
            if let lastUp = status.lastUpAt {
                Text("Last up \(lastUp, format: .relative(presentation: .named))")
            } else {
                Text("Never up")
            }
            if let failures = status.consecutiveFailures, failures > 0 {
                Text("· \(failures) failed checks")
            }
        }
        .font(.caption)
        .foregroundStyle(.red)
        .lineLimit(1)
    }
}
