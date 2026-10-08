import Foundation
import SwiftUI

/// Down share per time slot, for the downtime bars. Port of `downFractionColumns` of the web app
/// (frontend/src/utils/uptime.ts).
enum DowntimeTimeline {
    struct Column: Equatable {
        /// Index of the first slot.
        var index: Int
        /// Number of adjacent slots with the same color.
        var width: Int
        /// Share of the slot time that was down, 0 < fraction <= 1.
        var fraction: Double
    }

    /// Splits [start, end) into `slots` equal parts and gives the down share of each part.
    /// Parts without downtime are left out. Adjacent parts with the same color are merged.
    static func columns(periods: [DownPeriod], start: Date, end: Date, slots: Int) -> [Column] {
        let total = end.timeIntervalSince(start)
        guard total > 0, slots > 0 else { return [] }
        let slotLength = total / Double(slots)

        var down = [Double](repeating: 0, count: slots)
        for (periodStart, periodEnd) in merged(periods, start: start, end: end) {
            let s = periodStart.timeIntervalSince(start)
            let e = periodEnd.timeIntervalSince(start)
            let first = min(slots - 1, Int(s / slotLength))
            let last = min(slots - 1, Int(e / slotLength))
            for i in first...last {
                let slotStart = Double(i) * slotLength
                let overlap = min(e, slotStart + slotLength) - max(s, slotStart)
                if overlap > 0 { down[i] += overlap }
            }
        }

        var result: [Column] = []
        for (i, seconds) in down.enumerated() where seconds > 0 {
            let fraction = min(1, seconds / slotLength)
            if var previous = result.last, previous.index + previous.width == i,
               colorStop(previous.fraction) == colorStop(fraction) {
                previous.width += 1
                result[result.count - 1] = previous
            } else {
                result.append(Column(index: i, width: 1, fraction: fraction))
            }
        }
        return result
    }

    /// Down time inside [start, end). Overlapping periods count once.
    static func totalDowntime(periods: [DownPeriod], start: Date, end: Date) -> TimeInterval {
        merged(periods, start: start, end: end).reduce(0) { $0 + $1.1.timeIntervalSince($1.0) }
    }

    /// Clips the periods to [start, end), sorts them and merges overlaps.
    static func merged(_ periods: [DownPeriod], start: Date, end: Date) -> [(Date, Date)] {
        let clipped = periods
            .map { (max(start, $0.start), min(end, $0.end)) }
            .filter { $0.1 > $0.0 }
            .sorted { $0.0 < $1.0 }
        var result: [(Date, Date)] = []
        for (s, e) in clipped {
            if let last = result.last, s <= last.1 {
                result[result.count - 1].1 = max(last.1, e)
            } else {
                result.append((s, e))
            }
        }
        return result
    }

    // Light yellow (a short part of the slot was down) to dark red (all of the slot was down).
    private static let stops: [(position: Double, rgb: (Double, Double, Double))] = [
        (0, (254, 249, 195)),   // #fef9c3
        (0.25, (250, 204, 21)), // #facc15
        (0.5, (249, 115, 22)),  // #f97316
        (0.75, (220, 38, 38)),  // #dc2626
        (1, (127, 29, 29)),     // #7f1d1d
    ]

    /// RGB (0…255) of a down share.
    static func rgb(for fraction: Double) -> (red: Int, green: Int, blue: Int) {
        let f = min(1, max(0, fraction))
        let upper = max(1, stops.firstIndex { f <= $0.position } ?? stops.count - 1)
        let (p0, c0) = stops[upper - 1]
        let (p1, c1) = stops[upper]
        let t = (f - p0) / (p1 - p0)
        func channel(_ a: Double, _ b: Double) -> Int { Int((a + (b - a) * t).rounded()) }
        return (channel(c0.0, c1.0), channel(c0.1, c1.1), channel(c0.2, c1.2))
    }

    static func color(for fraction: Double) -> Color {
        let (r, g, b) = rgb(for: fraction)
        return Color(red: Double(r) / 255, green: Double(g) / 255, blue: Double(b) / 255)
    }

    /// Adjacent slots are merged when they get the same color.
    private static func colorStop(_ fraction: Double) -> [Int] {
        let (r, g, b) = rgb(for: fraction)
        return [r, g, b]
    }
}

/// A bar over a time window: green when up, yellow to dark red by the share of downtime.
struct DowntimeBar: View {
    let periods: [DownPeriod]
    let start: Date
    let end: Date
    var height: CGFloat = 8

    var body: some View {
        Canvas { context, size in
            let slots = max(1, Int(size.width.rounded(.down)))
            let slotWidth = size.width / CGFloat(slots)
            context.fill(Path(CGRect(origin: .zero, size: size)), with: .color(.green.opacity(0.35)))
            for column in DowntimeTimeline.columns(periods: periods, start: start, end: end, slots: slots) {
                let rect = CGRect(
                    x: CGFloat(column.index) * slotWidth, y: 0,
                    width: CGFloat(column.width) * slotWidth, height: size.height)
                context.fill(Path(rect), with: .color(DowntimeTimeline.color(for: column.fraction)))
            }
        }
        .frame(height: height)
        .clipShape(.rect(cornerRadius: height / 3))
        .accessibilityElement()
        .accessibilityLabel("Downtime")
        .accessibilityValue(accessibilityValue)
    }

    private var accessibilityValue: String {
        let downtime = DowntimeTimeline.totalDowntime(periods: periods, start: start, end: end)
        return downtime > 0 ? "\(Format.duration(downtime)) down" : "No downtime"
    }
}
