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

    @MainActor @Test func outageRowsUseTheColorOfTheirMark() {
        #expect(DowntimeTimeline.isShort(59.9))
        #expect(!DowntimeTimeline.isShort(60))
        #expect(DowntimeBar.color(forDuration: 30) == DowntimeBar.shortOutageColor)
        #expect(DowntimeBar.color(forDuration: 60) == Brand.down)
        #expect(DowntimeBar.color(forDuration: 600) == DowntimeBar.color(isShort: false))
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

struct DateFormatsTests {
    private static let utc = TimeZone(identifier: "UTC") ?? .gmt
    private static let gregorian: Calendar = {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = utc
        return calendar
    }()
    private static let us = Locale(identifier: "en_US")
    private static let de = Locale(identifier: "de_DE")
    /// Thursday, 2026-12-31 14:05:09 UTC.
    private static let date = Date(timeIntervalSince1970: 1_798_725_909)
    /// Monday, 2026-01-05 03:07:00 UTC: needs zero padding.
    private static let earlyDate = Date(timeIntervalSince1970: 1_767_582_420)

    /// ICU puts narrow or no-break spaces before AM/PM.
    private func plain(_ text: String) -> String {
        text.replacingOccurrences(of: "\u{202F}", with: " ").replacingOccurrences(of: "\u{00A0}", with: " ")
    }

    private func systemStyle(_ locale: Locale) -> Date.FormatStyle {
        Date.FormatStyle(locale: locale, calendar: Self.gregorian, timeZone: Self.utc)
    }

    private func formats(_ date: DateFormatChoice, _ time: TimeFormatChoice) -> DateFormats {
        DateFormats(date: date, time: time)
    }

    @Test func fixtureDatesAreRight() {
        #expect(Self.gregorian.dateComponents([.year, .month, .day, .hour, .minute, .second, .weekday], from: Self.date)
            == DateComponents(year: 2026, month: 12, day: 31, hour: 14, minute: 5, second: 9, weekday: 5))
        #expect(Self.gregorian.dateComponents([.year, .month, .day, .hour, .minute], from: Self.earlyDate)
            == DateComponents(year: 2026, month: 1, day: 5, hour: 3, minute: 7))
    }

    @Test(arguments: [Locale(identifier: "en_US"), Locale(identifier: "de_DE")])
    func systemIsTheSameAsBefore(_ locale: Locale) {
        let system = DateFormats()
        let style = systemStyle(locale)
        let (tz, cal, date) = (Self.utc, Self.gregorian, Self.date)
        #expect(system.timeWithSeconds(date, locale: locale, timeZone: tz, calendar: cal)
            == style.hour().minute().second().format(date))
        #expect(system.windowEdge(date, locale: locale, timeZone: tz, calendar: cal)
            == style.day().month().hour().minute().format(date))
        #expect(system.outageStart(date, locale: locale, timeZone: tz, calendar: cal)
            == style.weekday().day().month().hour().minute().format(date))
        #expect(system.chartLabel(date, hourly: true, locale: locale, timeZone: tz, calendar: cal)
            == style.hour().format(date))
        #expect(system.chartLabel(date, hourly: false, locale: locale, timeZone: tz, calendar: cal)
            == style.day().month(.abbreviated).format(date))
    }

    @Test(arguments: [
        (DateFormatChoice.dmyDots, "31.12.2026", "31.12.", "05.01.2026"),
        (.dmySlashes, "31/12/2026", "31/12", "05/01/2026"),
        (.mdySlashes, "12/31/2026", "12/31", "01/05/2026"),
        (.iso, "2026-12-31", "12-31", "2026-01-05"),
    ])
    func fixedDateChoices(_ choice: DateFormatChoice, _ full: String, _ short: String, _ early: String) {
        for locale in [Self.us, Self.de] {
            let formats = formats(choice, .h24)
            let (tz, cal) = (Self.utc, Self.gregorian)
            #expect(formats.outageStart(Self.date, locale: locale, timeZone: tz, calendar: cal) == "\(full) 14:05")
            #expect(formats.outageStart(Self.earlyDate, locale: locale, timeZone: tz, calendar: cal) == "\(early) 03:07")
            #expect(formats.windowEdge(Self.date, locale: locale, timeZone: tz, calendar: cal) == "\(full) 14:05")
            #expect(formats.windowEdge(Self.earlyDate, locale: locale, timeZone: tz, calendar: cal) == "\(early) 03:07")
            #expect(formats.chartLabel(Self.date, hourly: false, locale: locale, timeZone: tz, calendar: cal) == short)
        }
    }

    @Test func outageStartHasNoWeekdayWithAFixedDate() {
        let (tz, cal) = (Self.utc, Self.gregorian)
        #expect(formats(.dmyDots, .h24).outageStart(Self.date, locale: Self.us, timeZone: tz, calendar: cal)
            == "31.12.2026 14:05")
        #expect(formats(.iso, .h24).outageStart(Self.date, locale: Self.de, timeZone: tz, calendar: cal)
            == "2026-12-31 14:05")
        #expect(plain(formats(.mdySlashes, .h12).outageStart(Self.date, locale: Self.us, timeZone: tz, calendar: cal))
            == "12/31/2026 2:05 PM")
    }

    @Test func timeChoices() {
        let (tz, cal) = (Self.utc, Self.gregorian)
        // 24-hour in a 12-hour locale, 12-hour in a 24-hour locale.
        #expect(formats(.system, .h24).timeWithSeconds(Self.date, locale: Self.us, timeZone: tz, calendar: cal) == "14:05:09")
        #expect(plain(formats(.system, .h12).timeWithSeconds(Self.date, locale: Self.de, timeZone: tz, calendar: cal))
            == "2:05:09 PM")
        #expect(formats(.system, .system).timeWithSeconds(Self.date, locale: Self.de, timeZone: tz, calendar: cal) == "14:05:09")
        #expect(plain(formats(.system, .system).timeWithSeconds(Self.date, locale: Self.us, timeZone: tz, calendar: cal))
            == "2:05:09 PM")
        #expect(formats(.dmyDots, .h24).timeWithSeconds(Self.earlyDate, locale: Self.us, timeZone: tz, calendar: cal) == "03:07:00")
        #expect(plain(formats(.dmyDots, .h12).windowEdge(Self.earlyDate, locale: Self.de, timeZone: tz, calendar: cal))
            == "05.01.2026 3:07 AM")
    }

    @Test func timeChoiceAppliesWithTheSystemDate() {
        let (tz, cal) = (Self.utc, Self.gregorian)
        let us24 = formats(.system, .h24).windowEdge(Self.date, locale: Self.us, timeZone: tz, calendar: cal)
        #expect(us24.contains("14:05") && !us24.contains("PM") && us24.contains("Dec"))
        let de12 = plain(formats(.system, .h12).outageStart(Self.date, locale: Self.de, timeZone: tz, calendar: cal))
        #expect(de12.contains("2:05 PM") && de12.contains("Dez"))
    }

    @Test func dateChoiceAppliesWithTheSystemTime() {
        let (tz, cal) = (Self.utc, Self.gregorian)
        #expect(formats(.dmySlashes, .system).windowEdge(Self.date, locale: Self.de, timeZone: tz, calendar: cal)
            == "31/12/2026 14:05")
        #expect(plain(formats(.iso, .system).outageStart(Self.date, locale: Self.us, timeZone: tz, calendar: cal))
            == "2026-12-31 2:05 PM")
    }

    @Test func chartHoursFollowTheTimeChoice() {
        let (tz, cal) = (Self.utc, Self.gregorian)
        #expect(formats(.system, .h24).chartLabel(Self.date, hourly: true, locale: Self.us, timeZone: tz, calendar: cal) == "14")
        #expect(plain(formats(.system, .h12).chartLabel(Self.date, hourly: true, locale: Self.de, timeZone: tz, calendar: cal))
            == "2 PM")
    }

    @MainActor
    @Test func settingsAreStoredWithSystemAsDefault() throws {
        let suite = "test.\(UUID().uuidString)"
        let defaults = try #require(UserDefaults(suiteName: suite))
        defer { defaults.removePersistentDomain(forName: suite) }

        let settings = AppSettings(defaults: defaults)
        #expect(settings.dateFormats == DateFormats(date: .system, time: .system))

        settings.dateFormat = .iso
        settings.timeFormat = .h12
        #expect(defaults.string(forKey: "settings.dateFormat") == "iso")
        #expect(defaults.string(forKey: "settings.timeFormat") == "h12")
        let reloaded = AppSettings(defaults: defaults)
        #expect(reloaded.dateFormats == DateFormats(date: .iso, time: .h12))

        // Connection reset keeps the formats.
        reloaded.resetToDefaults()
        #expect(AppSettings(defaults: defaults).dateFormat == .iso)
    }

    @MainActor
    @Test func invalidStoredFormatsFallBackToSystem() throws {
        let suite = "test.\(UUID().uuidString)"
        let defaults = try #require(UserDefaults(suiteName: suite))
        defer { defaults.removePersistentDomain(forName: suite) }

        defaults.set("dd.MM.yyyy", forKey: "settings.dateFormat")
        defaults.set(24, forKey: "settings.timeFormat")
        let settings = AppSettings(defaults: defaults)
        #expect(settings.dateFormat == .system)
        #expect(settings.timeFormat == .system)
    }
}
