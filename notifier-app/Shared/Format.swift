//
//  Format.swift
//  StatusTacosAlert
//
//  Text of values. Used by the app and the widget.
//

import Foundation
import SwiftUI

enum Format {
    /// Uptime with 2 decimals, rounded down: 99.996 % is "99.99%", never "100.00%".
    static func uptime(_ percentage: Double?) -> String {
        guard let percentage else { return "N/A" }
        let floored = (percentage * 100 + 1e-6).rounded(.down) / 100
        return String(format: "%.2f%%", locale: Locale(identifier: "en_US_POSIX"), floored)
    }

    static func responseTime(_ milliseconds: Int?) -> String {
        guard let milliseconds else { return "–" }
        if milliseconds < 1000 { return "\(milliseconds) ms" }
        let tenths = (milliseconds + 50) / 100
        return "\(tenths / 10).\(tenths % 10) s"
    }

    /// Duration in words with one unit, rounded down: "45 s", "12 min", "3 h", "1 day", "4 days".
    static func durationWords(_ interval: TimeInterval) -> String {
        let seconds = Swift.max(0, Int(interval.rounded(.down)))
        switch seconds {
        case ..<60: return "\(seconds) s"
        case ..<3_600: return "\(seconds / 60) min"
        case ..<86_400: return "\(seconds / 3_600) h"
        default:
            let days = seconds / 86_400
            return days == 1 ? "1 day" : "\(days) days"
        }
    }

    /// Color of an uptime value, same thresholds as the web app.
    static func uptimeColor(_ percentage: Double?) -> Color {
        guard let percentage else { return .secondary }
        if percentage >= 95 { return Brand.up }
        if percentage >= 90 { return Brand.silent }
        return Brand.down
    }
}
