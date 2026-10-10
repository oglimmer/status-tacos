import Foundation
import Testing
@testable import StatusTacos

struct FormatTests {
    // Same cases as frontend/src/utils/uptime.spec.ts.
    @Test func uptimeRoundsDown() {
        #expect(Format.uptime(99.996) == "99.99%")
        #expect(Format.uptime(99.999999) == "99.99%")
        #expect(Format.uptime(66.666) == "66.66%")
    }

    @Test func uptimeKeepsValuesWithTwoDecimals() {
        #expect(Format.uptime(99.99) == "99.99%")
        #expect(Format.uptime(0.29) == "0.29%")
        #expect(Format.uptime(100) == "100.00%")
        #expect(Format.uptime(0) == "0.00%")
    }

    @Test func uptimeWithoutData() {
        #expect(Format.uptime(nil) == "N/A")
    }

    @Test func uptimeLevels() {
        #expect(UptimeLevel(99) == .excellent)
        #expect(UptimeLevel(98.99) == .good)
        #expect(UptimeLevel(90) == .warning)
        #expect(UptimeLevel(89.99) == .poor)
        #expect(UptimeLevel(nil) == .noData)
    }

    @Test func responseTimes() {
        #expect(Format.responseTime(nil) == "–")
        #expect(Format.responseTime(0) == "0 ms")
        #expect(Format.responseTime(999) == "999 ms")
        #expect(Format.responseTime(1000) == "1.0 s")
        #expect(Format.responseTime(2450) == "2.5 s")
    }

    @Test(arguments: [
        (0.0, "0s"), (0.4, "0s"), (45, "45s"), (60, "1m"), (303, "5m 3s"), (3600, "1h"),
        (7500, "2h 5m"), (7530, "2h 5m"), (86_400, "1d"), (273_600, "3d 4h"), (-5, "0s"),
    ] as [(TimeInterval, String)])
    func durations(_ seconds: TimeInterval, _ expected: String) {
        #expect(Format.duration(seconds) == expected)
    }

    @Test func statusCodes() {
        #expect(Format.statusCode(200) == "HTTP 200")
        #expect(Format.statusCode(nil) == "–")
        #expect(Format.statusCode(0) == "–")
    }
}

struct DowntimeTimelineTests {
    // 10 slots of 1 hour each. Same cases as frontend/src/utils/uptime.spec.ts.
    private let start = Date(timeIntervalSince1970: 1_767_225_600) // 2026-01-01T00:00:00Z
    private var end: Date { at(10) }

    private func at(_ hour: Int, _ minute: Int = 0) -> Date {
        start.addingTimeInterval(TimeInterval(hour * 3600 + minute * 60))
    }

    @Test func givesTheDownShareOfEachSlot() throws {
        let columns = DowntimeTimeline.columns(
            periods: [DownPeriod(start: at(2), end: at(2, 6))], start: start, end: end, slots: 10)
        #expect(columns.count == 1)
        let column = try #require(columns.first)
        #expect(column.index == 2)
        #expect(abs(column.fraction - 0.1) < 1e-9)
    }

    @Test func splitsAPeriodOverSlotsAndMergesEqualNeighbours() {
        let columns = DowntimeTimeline.columns(
            periods: [DownPeriod(start: at(3, 30), end: at(7))], start: start, end: end, slots: 10)
        #expect(columns == [
            .init(index: 3, width: 1, fraction: 0.5),
            .init(index: 4, width: 3, fraction: 1),
        ])
    }

    @Test func doesNotCountOverlapsTwiceAndClipsToTheWindow() {
        let columns = DowntimeTimeline.columns(
            periods: [
                DownPeriod(start: at(0), end: at(0, 30)),
                DownPeriod(start: at(0, 15), end: at(0, 45)),
                DownPeriod(start: at(9, 30), end: at(24)),
            ],
            start: start, end: end, slots: 10)
        #expect(columns.map(\.index) == [0, 9])
        #expect(columns.map(\.fraction) == [0.75, 0.5])
    }

    @Test func ignoresPeriodsOutsideTheWindow() {
        let columns = DowntimeTimeline.columns(
            periods: [DownPeriod(start: at(-5), end: at(-1)), DownPeriod(start: at(11), end: at(12))],
            start: start, end: end, slots: 10)
        #expect(columns.isEmpty)
    }

    @Test func countsOnlyTheCheckedTimeOfASlot() {
        // A new monitor, down since its first check 15 minutes before the end: the last slot is
        // all down, not a quarter.
        let columns = DowntimeTimeline.columns(
            periods: [DownPeriod(start: at(9, 45), end: at(10))],
            start: start, end: end, checkedFrom: at(9, 45), slots: 10)
        #expect(columns == [.init(index: 9, width: 1, fraction: 1)])
    }

    @Test func checkedFromStartsAtTheFirstCheck() {
        #expect(DowntimeTimeline.checkedFrom(firstCheckAt: at(3), totalChecks: 5, start: start) == at(3))
        // Rolled-up hours can start before the window.
        #expect(DowntimeTimeline.checkedFrom(firstCheckAt: at(-1), totalChecks: 5, start: start) == start)
        // No checks: no data at all.
        #expect(DowntimeTimeline.checkedFrom(firstCheckAt: nil, totalChecks: 0, start: start) == nil)
        // An older server sends no first check: the whole window counts as checked.
        #expect(DowntimeTimeline.checkedFrom(firstCheckAt: nil, totalChecks: 5, start: start) == start)
        #expect(DowntimeTimeline.checkedFrom(firstCheckAt: nil, totalChecks: nil, start: start) == start)
    }

    @Test func emptyWindowGivesNoColumns() {
        #expect(DowntimeTimeline.columns(periods: [], start: end, end: start, slots: 10).isEmpty)
        #expect(DowntimeTimeline.columns(periods: [], start: start, end: end, slots: 0).isEmpty)
    }

    @Test func totalDowntimeMergesOverlaps() {
        let total = DowntimeTimeline.totalDowntime(
            periods: [
                DownPeriod(start: at(1), end: at(2)),
                DownPeriod(start: at(1, 30), end: at(2, 30)),
                DownPeriod(start: at(9), end: at(11)),
            ],
            start: start, end: end)
        #expect(total == 2.5 * 3600)
    }

    @Test func colorsGoFromYellowToDarkRed() {
        func hex(_ fraction: Double) -> String {
            let (r, g, b) = DowntimeTimeline.rgb(for: fraction)
            return String(format: "#%02lx%02lx%02lx", r, g, b)
        }
        #expect(hex(0) == "#fef9c3")
        #expect(hex(0.25) == "#facc15")
        #expect(hex(0.5) == "#f97316")
        #expect(hex(1) == "#7f1d1d")
        #expect(hex(2) == "#7f1d1d")
        #expect(hex(-1) == "#fef9c3")
    }
}
