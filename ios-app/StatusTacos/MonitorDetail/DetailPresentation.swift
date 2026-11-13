import Foundation

/// Texts and shapes of the monitor detail screen that do not depend on SwiftUI.
enum DetailPresentation {
    /// The small line under the status in the header. Nil: no second line.
    static func statusLine(health: MonitorHealth, status: MonitorStatusDTO?, now: Date) -> String? {
        switch health {
        case .down: status?.downMessage(now: now) ?? "Down"
        case .up: status?.upMessage(now: now)
        case .paused: "Not checked while paused"
        case .pending: "Waiting for the first check"
        }
    }

    /// Downtime bands of the response time chart. Each band is at least `minimumFraction` of the
    /// window wide, so a short outage (also a running one at the right edge) stays visible. A widened
    /// band keeps its start and grows to the right; at the end of the window it grows to the left.
    /// Bands that overlap after widening are merged.
    static func chartBands(
        periods: [DownPeriod], start: Date, end: Date, minimumFraction: Double = 0.015
    ) -> [(start: Date, end: Date)] {
        let window = end.timeIntervalSince(start)
        guard window > 0 else { return [] }
        let minimum = window * minimumFraction
        var result: [(start: Date, end: Date)] = []
        for (bandStart, bandEnd) in DowntimeTimeline.merged(periods, start: start, end: end) {
            var lower = bandStart
            var upper = bandEnd
            if upper.timeIntervalSince(lower) < minimum {
                upper = lower.addingTimeInterval(minimum)
                if upper > end {
                    upper = end
                    lower = max(start, end.addingTimeInterval(-minimum))
                }
            }
            if let last = result.last, lower <= last.end {
                result[result.count - 1] = (min(last.start, lower), max(last.end, upper))
            } else {
                result.append((lower, upper))
            }
        }
        return result
    }
}
