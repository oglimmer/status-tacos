import Foundation

/// The user's date format. `system` follows the device; the others are fixed numeric patterns.
enum DateFormatChoice: String, CaseIterable, Sendable {
    case system
    /// 31.12.2026
    case dmyDots
    /// 31/12/2026
    case dmySlashes
    /// 12/31/2026
    case mdySlashes
    /// 2026-12-31
    case iso

    /// Title for the settings picker, with an example.
    var title: String {
        switch self {
        case .system: "System"
        case .dmyDots: "31.12.2026"
        case .dmySlashes: "31/12/2026"
        case .mdySlashes: "12/31/2026"
        case .iso: "2026-12-31"
        }
    }
}

/// The user's time format. `system` follows the device.
enum TimeFormatChoice: String, CaseIterable, Sendable {
    case system
    /// 14:05
    case h24
    /// 2:05 PM
    case h12

    /// Title for the settings picker, with an example.
    var title: String {
        switch self {
        case .system: "System"
        case .h24: "24-hour (14:05)"
        case .h12: "12-hour (2:05 PM)"
        }
    }
}

/// Formats absolute dates and times with the user's date and time format.
/// With `.system` for both, the output is the same as the plain `Date.FormatStyle`.
struct DateFormats: Equatable, Sendable {
    var date: DateFormatChoice = .system
    var time: TimeFormatChoice = .system

    /// "14:05:09". Monitor list: time of the last refresh.
    func timeWithSeconds(
        _ value: Date, locale: Locale = .autoupdatingCurrent, timeZone: TimeZone = .autoupdatingCurrent,
        calendar: Calendar = .autoupdatingCurrent
    ) -> String {
        style(locale: locale, timeZone: timeZone, calendar: calendar).hour().minute().second().format(value)
    }

    /// Start or end of the outage window (detail screen, Outages footer). A fixed choice gives the
    /// full selected date with the year, then the time: "2026-12-31 14:05". `.system`: day, month
    /// and time from the device ("Dec 31, 2:05 PM").
    func windowEdge(
        _ value: Date, locale: Locale = .autoupdatingCurrent, timeZone: TimeZone = .autoupdatingCurrent,
        calendar: Calendar = .autoupdatingCurrent
    ) -> String {
        let style = style(locale: locale, timeZone: timeZone, calendar: calendar)
        guard date != .system else { return style.day().month().hour().minute().format(value) }
        return fixedDateAndTime(value, style: style, timeZone: timeZone)
    }

    /// Start of one outage (detail screen, outage row). A fixed choice gives the full selected date
    /// with the year and no weekday, then the time: "2026-12-31 14:05". `.system`: weekday, day,
    /// month and time from the device ("Thu, Dec 31, 2:05 PM").
    func outageStart(
        _ value: Date, locale: Locale = .autoupdatingCurrent, timeZone: TimeZone = .autoupdatingCurrent,
        calendar: Calendar = .autoupdatingCurrent
    ) -> String {
        let style = style(locale: locale, timeZone: timeZone, calendar: calendar)
        guard date != .system else { return style.weekday().day().month().hour().minute().format(value) }
        return fixedDateAndTime(value, style: style, timeZone: timeZone)
    }

    /// Chart X axis: the hour ("14", "2 PM") for 24 hours, else day and month ("Dec 31", "31.12.").
    func chartLabel(
        _ value: Date, hourly: Bool, locale: Locale = .autoupdatingCurrent,
        timeZone: TimeZone = .autoupdatingCurrent, calendar: Calendar = .autoupdatingCurrent
    ) -> String {
        let style = style(locale: locale, timeZone: timeZone, calendar: calendar)
        if hourly { return style.hour().format(value) }
        guard date != .system else { return style.day().month(.abbreviated).format(value) }
        return fixedDate(value, withYear: false, timeZone: timeZone)
    }

    /// Empty style with the hour cycle of the time setting.
    private func style(locale: Locale, timeZone: TimeZone, calendar: Calendar) -> Date.FormatStyle {
        Date.FormatStyle(locale: Self.locale(locale, time: time), calendar: calendar, timeZone: timeZone)
    }

    /// The locale with the hour cycle of the time setting; unchanged for `.system`.
    private static func locale(_ locale: Locale, time: TimeFormatChoice) -> Locale {
        var components = Locale.Components(locale: locale)
        switch time {
        case .system: return locale
        case .h24: components.hourCycle = .zeroToTwentyThree
        case .h12: components.hourCycle = .oneToTwelve
        }
        return Locale(components: components)
    }

    /// Full date of a fixed choice, then hour and minute with the time setting: "31.12.2026 14:05".
    private func fixedDateAndTime(_ value: Date, style: Date.FormatStyle, timeZone: TimeZone) -> String {
        "\(fixedDate(value, withYear: true, timeZone: timeZone)) \(style.hour().minute().format(value))"
    }

    /// Numeric date of a fixed choice (Gregorian). With the year: "31.12.2026"; without: "31.12.", "31/12", "12/31", "12-31".
    private func fixedDate(_ value: Date, withYear: Bool, timeZone: TimeZone) -> String {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = timeZone
        let parts = calendar.dateComponents([.year, .month, .day], from: value)
        let day = Self.twoDigits(parts.day ?? 0)
        let month = Self.twoDigits(parts.month ?? 0)
        let year = String(format: "%04d", parts.year ?? 0)
        switch date {
        case .system, .dmyDots: return withYear ? "\(day).\(month).\(year)" : "\(day).\(month)."
        case .dmySlashes: return withYear ? "\(day)/\(month)/\(year)" : "\(day)/\(month)"
        case .mdySlashes: return withYear ? "\(month)/\(day)/\(year)" : "\(month)/\(day)"
        case .iso: return withYear ? "\(year)-\(month)-\(day)" : "\(month)-\(day)"
        }
    }

    private static func twoDigits(_ number: Int) -> String {
        String(format: "%02d", number)
    }
}
