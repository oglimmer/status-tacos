//
//  BackendDate.swift
//  StatusTacosAlert
//

import Foundation


/// Parses timestamps like `2026-10-07T14:37:12.123456` as UTC.
enum BackendDate {
    private static let utc: Calendar = {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "UTC")!
        return calendar
    }()

    static func parse(_ value: String) -> Date? {
        var text = Substring(value)
        if text.hasSuffix("Z") { text = text.dropLast() }

        let dateAndTime = text.split(separator: "T", omittingEmptySubsequences: false)
        guard dateAndTime.count == 2 else { return nil }
        let timeAndFraction = dateAndTime[1].split(separator: ".", omittingEmptySubsequences: false)
        guard (1...2).contains(timeAndFraction.count),
              let ymd = numbers(dateAndTime[0], separator: "-"), ymd.count == 3,
              let hms = numbers(timeAndFraction[0], separator: ":"), (2...3).contains(hms.count)
        else { return nil }

        var fraction = 0.0
        if timeAndFraction.count == 2 {
            let digits = timeAndFraction[1]
            guard !digits.isEmpty, digits.allSatisfy(\.isASCIIDigit),
                  let value = Double("0." + digits) else { return nil }
            fraction = value
        }

        let components = DateComponents(
            year: ymd[0], month: ymd[1], day: ymd[2],
            hour: hms[0], minute: hms[1], second: hms.count == 3 ? hms[2] : 0)
        guard components.isValidDate(in: utc), let date = utc.date(from: components) else { return nil }
        return date.addingTimeInterval(fraction)
    }

    private static func numbers(_ text: Substring, separator: Character) -> [Int]? {
        var result: [Int] = []
        for part in text.split(separator: separator, omittingEmptySubsequences: false) {
            guard !part.isEmpty, part.allSatisfy(\.isASCIIDigit), let number = Int(part) else { return nil }
            result.append(number)
        }
        return result
    }
}

private extension Character {
    var isASCIIDigit: Bool { isASCII && isNumber }
}
