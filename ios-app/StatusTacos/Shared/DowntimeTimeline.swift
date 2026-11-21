import Foundation
import SwiftUI

/// Downtime of a time window: the checked time and the outages, for the downtime bars.
enum DowntimeTimeline {
    /// One outage on a bar, in points.
    struct Mark: Equatable {
        var x: CGFloat
        var width: CGFloat
        /// Shorter than `shortOutage`: drawn in yellow, longer ones in red.
        var isShort: Bool
    }

    /// Outages shorter than this are short (yellow).
    static let shortOutage: TimeInterval = 60
    /// Marks are at least this wide, so a one-minute outage on a 7-day bar is still visible.
    static let minimumMarkWidth: CGFloat = 3

    /// True when an outage of this length is short (yellow). Bar marks and outage rows both use it.
    static func isShort(_ duration: TimeInterval) -> Bool {
        duration < shortOutage
    }

    /// Start of the checked part of [start, end). Before it the bar shows no data.
    /// - Returns: nil when there are no checks. An older server sends no first check: then the
    ///   whole window counts as checked.
    static func checkedFrom(firstCheckAt: Date?, totalChecks: Int?, start: Date) -> Date? {
        if let firstCheckAt { return max(start, firstCheckAt) }
        return totalChecks == 0 ? nil : start
    }

    /// One mark per merged outage, at its position on a bar `width` points wide. A mark is at least
    /// `minimumWidth` wide and stays inside the bar. Short marks come first, so the long (red) ones
    /// are drawn on top of them.
    static func marks(
        periods: [DownPeriod], start: Date, end: Date, width: CGFloat,
        minimumWidth: CGFloat = minimumMarkWidth
    ) -> [Mark] {
        let total = end.timeIntervalSince(start)
        guard total > 0, width > 0 else { return [] }
        let marks = merged(periods, start: start, end: end).map { periodStart, periodEnd in
            let duration = periodEnd.timeIntervalSince(periodStart)
            let markWidth = min(width, max(minimumWidth, width * CGFloat(duration) / CGFloat(total)))
            let x = width * CGFloat(periodStart.timeIntervalSince(start)) / CGFloat(total)
            return Mark(x: max(0, min(x, width - markWidth)), width: markWidth, isShort: isShort(duration))
        }
        return marks.filter(\.isShort) + marks.filter { !$0.isShort }
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
}

/// A bar over a time window: a calm grey track (lighter before the first check) with a mark per
/// outage. Outages of a minute or more are red, shorter ones yellow.
struct DowntimeBar: View {
    let periods: [DownPeriod]
    let start: Date
    let end: Date
    /// Start of the checked time. Nil: no checks, the whole bar is the light grey.
    let checkedFrom: Date?
    var height: CGFloat = 8

    /// The checked time. Visible on a white card and on the dark card (#1C1C1E).
    static let track = Color(light: 0xE5E5EA, dark: 0x3A3A3C)
    /// Before the first check.
    static let unchecked = Color(light: 0xF0F0F3, dark: 0x2C2C2E)
    static let shortOutageColor = Brand.masa

    /// Color of a short or long outage mark.
    static func color(isShort: Bool) -> Color {
        isShort ? shortOutageColor : Brand.down
    }

    /// Color of an outage of this length, the same as its mark on the bar.
    static func color(forDuration duration: TimeInterval) -> Color {
        color(isShort: DowntimeTimeline.isShort(duration))
    }

    var body: some View {
        Canvas { context, size in
            context.fill(Path(CGRect(origin: .zero, size: size)), with: .color(Self.unchecked))
            guard let checkedFrom else { return }
            let total = end.timeIntervalSince(start)
            guard total > 0 else { return }
            let checkedX = size.width * CGFloat(max(0, min(1, checkedFrom.timeIntervalSince(start) / total)))
            context.fill(
                Path(CGRect(x: checkedX, y: 0, width: size.width - checkedX, height: size.height)),
                with: .color(Self.track))
            for mark in DowntimeTimeline.marks(periods: periods, start: start, end: end, width: size.width) {
                context.fill(
                    Path(CGRect(x: mark.x, y: 0, width: mark.width, height: size.height)),
                    with: .color(Self.color(isShort: mark.isShort)))
            }
        }
        .frame(height: height)
        .clipShape(.rect(cornerRadius: height / 3))
        .accessibilityElement()
        .accessibilityLabel("Downtime")
        .accessibilityValue(accessibilityValue)
    }

    private var accessibilityValue: String {
        guard checkedFrom != nil else { return "No checks" }
        let downtime = DowntimeTimeline.totalDowntime(periods: periods, start: start, end: end)
        return downtime > 0 ? "\(Format.duration(downtime)) down" : "No downtime"
    }
}

#Preview {
    let end = Date.now
    let start = end.addingTimeInterval(-7 * 86_400)
    VStack(spacing: 16) {
        DowntimeBar(
            periods: [
                DownPeriod(start: start.addingTimeInterval(2 * 86_400), end: start.addingTimeInterval(2 * 86_400 + 30)),
                DownPeriod(start: start.addingTimeInterval(4 * 86_400), end: start.addingTimeInterval(4 * 86_400 + 600)),
                DownPeriod(start: end.addingTimeInterval(-720), end: end),
            ],
            start: start, end: end, checkedFrom: start)
        DowntimeBar(periods: [], start: start, end: end, checkedFrom: start.addingTimeInterval(3 * 86_400))
        DowntimeBar(periods: [], start: start, end: end, checkedFrom: nil)
    }
    .padding()
}
