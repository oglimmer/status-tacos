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

    @Test(arguments: [
        (0.0, "0 s"), (-5, "0 s"), (45, "45 s"), (59.9, "59 s"), (60, "1 min"), (12 * 60 + 59, "12 min"),
        (3600, "1 h"), (3 * 3600 + 3599, "3 h"), (86_400, "1 day"), (2 * 86_400 - 1, "1 day"),
        (4 * 86_400 + 7200, "4 days"),
    ] as [(TimeInterval, String)])
    func durationsInWords(_ seconds: TimeInterval, _ expected: String) {
        #expect(Format.durationWords(seconds) == expected)
    }

    @Test func downtimeOfAWindow() {
        #expect(Format.downtime(0) == "No downtime")
        #expect(Format.downtime(12 * 60 + 5) == "12 min down")
        #expect(Format.downtime(30) == "30 s down")
    }

    @Test func uptimeLevelColorsUseTheBrand() {
        #expect(UptimeLevel(99.5).color == Brand.up)
        #expect(UptimeLevel(96).color == Brand.up)
        #expect(UptimeLevel(91).color == Brand.silent)
        #expect(UptimeLevel(50).color == Brand.down)
        #expect(UptimeLevel(nil).color == .secondary)
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

    // A 10-hour window on a 1000 pt bar: 100 pt per hour.
    private func marks(_ periods: [DownPeriod], width: CGFloat = 1000) -> [DowntimeTimeline.Mark] {
        DowntimeTimeline.marks(periods: periods, start: start, end: end, width: width)
    }

    @Test func markHasTheTruePositionAndWidth() {
        #expect(marks([DownPeriod(start: at(2), end: at(2, 30))]) == [.init(x: 200, width: 50, isShort: false)])
    }

    @Test func shortOutagesGetTheMinimumWidth() {
        // 30 s is 0.83 pt.
        let period = DownPeriod(start: at(5), end: at(5).addingTimeInterval(30))
        #expect(marks([period]) == [.init(x: 500, width: 3, isShort: true)])
    }

    @Test func outagesOfAMinuteOrMoreAreLong() {
        let oneMinute = DownPeriod(start: at(1), end: at(1, 1))
        let almost = DownPeriod(start: at(3), end: at(3).addingTimeInterval(59.9))
        #expect(marks([oneMinute, almost]).map(\.isShort) == [true, false])
    }

    @Test func shortMarksComeFirstSoRedIsDrawnOnTop() {
        let long = DownPeriod(start: at(1), end: at(1, 10))
        let short = DownPeriod(start: at(1, 30), end: at(1, 30).addingTimeInterval(20))
        let result = marks([long, short])
        #expect(result.map(\.isShort) == [true, false])
        #expect(result.map(\.x) == [150, 100])
    }

    @Test func marksStayInsideTheBar() {
        // 10 s at the very end, and the minimum width wider than the bar.
        let atEnd = DownPeriod(start: end.addingTimeInterval(-10), end: end)
        #expect(marks([atEnd]) == [.init(x: 997, width: 3, isShort: true)])
        #expect(marks([atEnd], width: 2) == [.init(x: 0, width: 2, isShort: true)])
    }

    @Test func marksMergeOverlapsAndClipToTheWindow() {
        let result = marks([
            DownPeriod(start: at(0), end: at(0, 30)),
            DownPeriod(start: at(0, 15), end: at(0, 45)),
            DownPeriod(start: at(9, 30), end: at(24)),
            DownPeriod(start: at(-5), end: at(-1)),
        ])
        #expect(result == [.init(x: 0, width: 75, isShort: false), .init(x: 950, width: 50, isShort: false)])
    }

    @Test func emptyWindowGivesNoMarks() {
        let period = DownPeriod(start: at(1), end: at(2))
        #expect(DowntimeTimeline.marks(periods: [period], start: end, end: start, width: 100).isEmpty)
        #expect(marks([period], width: 0).isEmpty)
        #expect(marks([]).isEmpty)
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
}

struct StatusMessageTests {
    private let now = Date(timeIntervalSince1970: 1_767_225_600)

    private func status(
        _ type: StatusType, code: Int? = nil, error: String? = nil, lastUpAt: Date? = nil,
        lastDownAt: Date? = nil, outageStartedAt: Date? = nil
    ) -> MonitorStatusDTO {
        MonitorStatusDTO(
            monitorId: 1, monitorName: nil, currentStatus: type, lastCheckedAt: nil, lastUpAt: lastUpAt,
            lastDownAt: lastDownAt, consecutiveFailures: nil, lastResponseTimeMs: nil, lastStatusCode: code,
            lastErrorMessage: error, outageStartedAt: outageStartedAt)
    }

    @Test func downMessageWithStatusCode() {
        let s = status(.down, code: 503, error: "ignored", outageStartedAt: now.addingTimeInterval(-12 * 60))
        #expect(s.downMessage(now: now) == "HTTP 503 · down 12 min")
    }

    @Test func downMessageUsesTheErrorWithoutStatusCode() {
        let s = status(.down, code: 0, error: "  Read timed out\n after 10 s ", lastUpAt: now.addingTimeInterval(-45))
        #expect(s.downMessage(now: now) == "Read timed out after 10 s · down 45 s")
    }

    @Test func downMessageFallsBackToDown() {
        #expect(status(.down, error: " \n ").downMessage(now: now) == "Down")
        #expect(status(.down, lastUpAt: now.addingTimeInterval(-3 * 3600)).downMessage(now: now) == "Down · down 3 h")
    }

    @Test func downMessagePrefersTheOutageStart() {
        let s = status(.down, code: 500, lastUpAt: now.addingTimeInterval(-86_400 * 2),
                       outageStartedAt: now.addingTimeInterval(-300))
        #expect(s.downMessage(now: now) == "HTTP 500 · down 5 min")
    }

    @Test func upMessage() {
        #expect(status(.up, lastDownAt: now.addingTimeInterval(-4 * 86_400 - 100)).upMessage(now: now) == "Up for 4 days")
        #expect(status(.up, lastDownAt: now.addingTimeInterval(-86_400)).upMessage(now: now) == "Up for 1 day")
        #expect(status(.up).upMessage(now: now) == "No downtime recorded")
    }
}
