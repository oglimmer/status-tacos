import Foundation
import SwiftUI

enum Format {
    /// Uptime with 2 decimals, rounded down: 99.996 % is "99.99%", never "100.00%".
    /// Same rule as the web app (frontend/src/utils/uptime.ts).
    static func uptime(_ percentage: Double?) -> String {
        guard let percentage else { return "N/A" }
        // The small epsilon keeps values like 99.99 (9998.999… after * 100) at 99.99.
        let floored = (percentage * 100 + 1e-6).rounded(.down) / 100
        return String(format: "%.2f%%", locale: Locale(identifier: "en_US_POSIX"), floored)
    }

    static func responseTime(_ milliseconds: Int?) -> String {
        guard let milliseconds else { return "–" }
        if milliseconds < 1000 { return "\(milliseconds) ms" }
        // Integer math: 2450 ms is "2.5 s" (a Double would round 2.45 down).
        let tenths = (milliseconds + 50) / 100
        return "\(tenths / 10).\(tenths % 10) s"
    }

    /// Short duration with the 2 largest units, for example "45s", "5m 3s", "2h 5m", "3d 4h".
    static func duration(_ interval: TimeInterval) -> String {
        let seconds = max(0, Int(interval.rounded()))
        let units: [(String, Int)] = [("d", 86_400), ("h", 3_600), ("m", 60), ("s", 1)]
        guard let first = units.firstIndex(where: { seconds >= $0.1 }) else { return "0s" }
        let (bigName, bigSize) = units[first]
        var text = "\(seconds / bigSize)\(bigName)"
        if first + 1 < units.count {
            let (smallName, smallSize) = units[first + 1]
            let rest = (seconds % bigSize) / smallSize
            if rest > 0 { text += " \(rest)\(smallName)" }
        }
        return text
    }

    /// "HTTP 200", or "–" without a status code (for example a timeout).
    static func statusCode(_ code: Int?) -> String {
        guard let code, code > 0 else { return "–" }
        return "HTTP \(code)"
    }
}

/// Color class of an uptime value, same thresholds as the web app.
enum UptimeLevel: Equatable {
    case excellent
    case good
    case warning
    case poor
    case noData

    init(_ percentage: Double?) {
        switch percentage {
        case nil: self = .noData
        case let value? where value >= 99: self = .excellent
        case let value? where value >= 95: self = .good
        case let value? where value >= 90: self = .warning
        default: self = .poor
        }
    }

    var color: Color {
        switch self {
        case .excellent: .green
        case .good: .mint
        case .warning: .orange
        case .poor: .red
        case .noData: .secondary
        }
    }
}
