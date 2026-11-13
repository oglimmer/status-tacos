import Foundation
import Testing
@testable import StatusTacos

struct DetailPresentationTests {
    private let start = Date(timeIntervalSince1970: 1_000_000)
    private var end: Date { start.addingTimeInterval(24 * 3_600) }

    private func at(_ minutes: Double) -> Date { start.addingTimeInterval(minutes * 60) }

    private func close(_ lhs: Date, _ rhs: Date) -> Bool { abs(lhs.timeIntervalSince(rhs)) < 0.01 }

    private func status(_ type: StatusType, code: Int? = nil, lastUpAt: Date? = nil, lastDownAt: Date? = nil)
        -> MonitorStatusDTO {
        MonitorStatusDTO(
            monitorId: 1, monitorName: nil, currentStatus: type, lastCheckedAt: nil, lastUpAt: lastUpAt,
            lastDownAt: lastDownAt, consecutiveFailures: nil, lastResponseTimeMs: nil, lastStatusCode: code)
    }

    // MARK: - Status line

    @Test func statusLineWhenDown() {
        let now = at(100)
        let line = DetailPresentation.statusLine(
            health: .down, status: status(.down, code: 503, lastUpAt: at(88)), now: now)
        #expect(line == "HTTP 503 · down 12 min")
    }

    @Test func statusLineWhenUp() {
        let now = at(60 * 24)
        let line = DetailPresentation.statusLine(health: .up, status: status(.up, lastDownAt: at(0)), now: now)
        #expect(line == "Up for 1 day")
        #expect(DetailPresentation.statusLine(health: .up, status: status(.up), now: now) == "No downtime recorded")
    }

    @Test func statusLineWhenPausedOrPending() {
        #expect(DetailPresentation.statusLine(health: .paused, status: nil, now: start) == "Not checked while paused")
        #expect(DetailPresentation.statusLine(health: .pending, status: nil, now: start) == "Waiting for the first check")
    }

    // MARK: - Chart bands

    @Test func longOutageKeepsItsSize() {
        let bands = DetailPresentation.chartBands(
            periods: [DownPeriod(start: at(60), end: at(180))], start: start, end: end)
        #expect(bands.count == 1)
        #expect(close(bands[0].start, at(60)))
        #expect(close(bands[0].end, at(180)))
    }

    @Test func shortOutageGrowsToTheRight() {
        // 1.5 % of 24 h is 21.6 min.
        let bands = DetailPresentation.chartBands(
            periods: [DownPeriod(start: at(60), end: at(61))], start: start, end: end)
        #expect(bands.count == 1)
        #expect(close(bands[0].start, at(60)))
        #expect(close(bands[0].end, at(81.6)))
    }

    @Test func runningOutageAtTheEndGrowsToTheLeft() {
        let bands = DetailPresentation.chartBands(
            periods: [DownPeriod(start: end.addingTimeInterval(-5 * 60), end: end.addingTimeInterval(60))],
            start: start, end: end)
        #expect(bands.count == 1)
        #expect(close(bands[0].end, end))
        #expect(close(bands[0].start, end.addingTimeInterval(-21.6 * 60)))
    }

    @Test func widenedBandsThatOverlapAreMerged() {
        let bands = DetailPresentation.chartBands(
            periods: [DownPeriod(start: at(60), end: at(61)), DownPeriod(start: at(70), end: at(71))],
            start: start, end: end)
        #expect(bands.count == 1)
        #expect(close(bands[0].start, at(60)))
        #expect(close(bands[0].end, at(91.6)))
    }

    @Test func noBandsWithoutOutagesOrWindow() {
        #expect(DetailPresentation.chartBands(periods: [], start: start, end: end).isEmpty)
        #expect(DetailPresentation.chartBands(
            periods: [DownPeriod(start: at(1), end: at(2))], start: end, end: start).isEmpty)
    }
}
