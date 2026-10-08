import Foundation

// Wire types of the Status Tacos REST API (backend/src/main/java/.../dto).
// The backend leaves out null fields, so most fields are optional.

struct Tenant: Decodable, Sendable, Hashable, Identifiable {
    let id: Int
    let name: String
    let code: String?
}

/// GET /v1/users/me. The first call creates the user and a tenant (backend UserController).
struct CurrentUserDTO: Decodable, Sendable {
    let id: Int?
    let email: String?
    let tenants: [Tenant]?
}

/// State of a monitor, set by the user.
enum MonitorState: String, Decodable, Sendable {
    /// Checked, alerts are sent on failures.
    case active = "ACTIVE"
    /// Checked, no alerts are sent.
    case silent = "SILENT"
    /// Not checked.
    case inactive = "INACTIVE"
    case unknown

    init(from decoder: Decoder) throws {
        let raw = try decoder.singleValueContainer().decode(String.self)
        self = MonitorState(rawValue: raw) ?? .unknown
    }
}

enum StatusType: String, Decodable, Sendable {
    case up
    case down
    case unknown

    init(from decoder: Decoder) throws {
        let raw = try decoder.singleValueContainer().decode(String.self)
        self = StatusType(rawValue: raw) ?? .unknown
    }
}

/// GET /v1/monitors
struct MonitorDTO: Decodable, Sendable {
    let id: Int
    let name: String
    let url: String
    let tenantId: Int?
    let tenant: Tenant?
    let state: MonitorState
    let alertingThreshold: Int?
}

/// GET /v1/monitor-statuses (only monitors that are ACTIVE or SILENT)
struct MonitorStatusDTO: Decodable, Sendable {
    let monitorId: Int
    let monitorName: String?
    let currentStatus: StatusType
    let lastCheckedAt: Date?
    let lastUpAt: Date?
    let lastDownAt: Date?
    let consecutiveFailures: Int?
    let lastResponseTimeMs: Int?
    let lastStatusCode: Int?
}

struct ResponseTimeDataPoint: Decodable, Sendable, Hashable {
    let timestamp: Date
    /// Null when the time slot has no successful checks.
    let maxResponseTimeMs: Int?
}

struct DownPeriod: Decodable, Sendable, Hashable {
    let start: Date
    let end: Date

    var duration: TimeInterval { end.timeIntervalSince(start) }
}

/// GET /v1/monitor-statuses/{id}/response-time-history-24h
struct ResponseTimeHistoryDTO: Decodable, Sendable {
    let monitorId: Int
    let intervalMinutes: Int?
    /// Rounded down. Null when there are no checks.
    let uptimePercentage24h: Double?
    let totalChecks24h: Int?
    let successfulChecks24h: Int?
    let dataPoints: [ResponseTimeDataPoint]?
    let statusDownPeriods: [DownPeriod]?
}

/// Period names of the uptime stats API.
enum StatsPeriod: String, Decodable, Sendable {
    case sevenDays = "SEVEN_DAYS"
    case ninetyDays = "NINETY_DAYS"

    /// Value of the `period` query parameter.
    var pathValue: String { rawValue.lowercased() }
}

/// GET /v1/uptime-stats?period=… and GET /v1/uptime-stats/{id}
/// Window is [periodStart, periodEnd). Response times count successful checks only.
struct UptimeStatsDTO: Decodable, Sendable {
    let monitorId: Int
    let periodType: StatsPeriod?
    let periodStart: Date?
    let periodEnd: Date?
    let intervalMinutes: Int?
    let totalChecks: Int?
    let successfulChecks: Int?
    /// Rounded down. Null when there are no checks.
    let uptimePercentage: Double?
    let minResponseTimeMs: Int?
    let maxResponseTimeMs: Int?
    let avgResponseTimeMs: Int?
    let p99ResponseTimeMs: Int?
    let responseTimeDataPoints: [ResponseTimeDataPoint]?
    let statusDownPeriods: [DownPeriod]?
}

extension JSONDecoder {
    /// Decoder for the backend: timestamps are UTC without a zone suffix.
    static func backend() -> JSONDecoder {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .custom { decoder in
            let container = try decoder.singleValueContainer()
            let text = try container.decode(String.self)
            guard let date = BackendDate.parse(text) else {
                throw DecodingError.dataCorruptedError(
                    in: container, debugDescription: "Not a backend timestamp: \(text)")
            }
            return date
        }
        return decoder
    }
}

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

// MARK: - iOS push alerts

/// GET /v1/push/status
struct PushStatusDTO: Decodable, Sendable {
    /// The server sends push notifications (APNs is configured).
    let enabled: Bool
    /// Registered devices of the signed-in user.
    let devices: Int
}

/// An IOS_PUSH alert contact of the signed-in user (GET /v1/push/alerts).
struct PushAlertDTO: Decodable, Sendable, Identifiable {
    let id: Int
    let isActive: Bool
    let tenant: Tenant?
    /// true: every monitor of the tenant. false: only `monitors`.
    let allMonitors: Bool
    let monitors: [MonitorReference]?

    struct MonitorReference: Decodable, Sendable, Hashable {
        let id: Int
        let name: String
    }

    var monitorIDs: Set<Int> { Set((monitors ?? []).map(\.id)) }
}

/// PUT /v1/push/alerts/{tenantId}
struct PushAlertRequest: Encodable, Sendable, Equatable {
    var isActive: Bool
    var allMonitors: Bool
    var monitorIds: [Int]
}

/// PUT /v1/push/devices
struct PushDeviceRequest: Encodable, Sendable, Equatable {
    enum Environment: String, Encodable, Sendable {
        case sandbox = "SANDBOX"
        case production = "PRODUCTION"
    }

    let token: String
    let environment: Environment
    let deviceName: String?
}
