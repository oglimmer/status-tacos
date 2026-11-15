//
//  Models.swift
//  StatusTacosAlert
//
//  Wire types of the Status Tacos REST API, copied from the iOS app (ios-app/StatusTacos/API/Models.swift).
//  The backend leaves out null fields, so most fields are optional.
//

import Foundation

struct Tenant: Decodable, Hashable, Identifiable {
    let id: Int
    let name: String
    let code: String?
}

/// GET /v1/users/me. The first call creates the user and a tenant.
struct CurrentUserDTO: Decodable {
    let id: Int?
    let email: String?
    let tenants: [Tenant]?
}

/// State of a monitor, set by the user.
enum MonitorState: String, Decodable {
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

enum StatusType: String, Decodable {
    case up
    case down
    case unknown

    init(from decoder: Decoder) throws {
        let raw = try decoder.singleValueContainer().decode(String.self)
        self = StatusType(rawValue: raw) ?? .unknown
    }
}

/// GET /v1/monitors
struct MonitorDTO: Decodable {
    let id: Int
    let name: String
    let url: String
    let tenant: Tenant?
    let state: MonitorState
    let createdAt: Date?
    /// Nil when not set, unknown, and from older servers.
    let environment: MonitorEnvironment?

    private enum CodingKeys: String, CodingKey {
        case id, name, url, tenant, state, createdAt, environment
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(Int.self, forKey: .id)
        name = try container.decode(String.self, forKey: .name)
        url = try container.decode(String.self, forKey: .url)
        tenant = try container.decodeIfPresent(Tenant.self, forKey: .tenant)
        state = try container.decode(MonitorState.self, forKey: .state)
        createdAt = try? container.decodeIfPresent(Date.self, forKey: .createdAt)
        // Never fails the list: a wrong type or an unknown value is no environment.
        environment = (try? container.decodeIfPresent(String.self, forKey: .environment))
            .flatMap(MonitorEnvironment.init(wireValue:))
    }
}

/// Environment of a monitor, set by the user.
enum MonitorEnvironment: String, CaseIterable, Comparable {
    case prod = "PROD"
    case stage = "STAGE"
    case dev = "DEV"

    /// Case-insensitive. Nil for unknown values.
    init?(wireValue: String) {
        self.init(rawValue: wireValue.trimmingCharacters(in: .whitespacesAndNewlines).uppercased())
    }

    /// Sort order: production first. Monitors without an environment come after all of these.
    var rank: Int {
        switch self {
        case .prod: return 0
        case .stage: return 1
        case .dev: return 2
        }
    }

    static func < (lhs: Self, rhs: Self) -> Bool { lhs.rank < rhs.rank }

    var title: String {
        switch self {
        case .prod: return "Production"
        case .stage: return "Stage"
        case .dev: return "Development"
        }
    }
}

/// GET /v1/monitor-statuses (only monitors that are ACTIVE or SILENT)
struct MonitorStatusDTO: Decodable {
    let monitorId: Int
    let currentStatus: StatusType
    let lastUpAt: Date?
    let lastDownAt: Date?
    let lastResponseTimeMs: Int?
    let lastStatusCode: Int?
    /// Error of the last failed check, for example a timeout.
    let lastErrorMessage: String?
    /// Start of the current outage. Nil when up, and from older servers.
    let outageStartedAt: Date?
}

/// Period names of the uptime stats API.
enum StatsPeriod: String, Decodable {
    case sevenDays = "SEVEN_DAYS"
    case ninetyDays = "NINETY_DAYS"

    /// Value of the `period` query parameter.
    var pathValue: String { rawValue.lowercased() }
}

/// GET /v1/uptime-stats?period=…
struct UptimeStatsDTO: Decodable {
    let monitorId: Int
    /// Rounded down. Null when there are no checks.
    let uptimePercentage: Double?
}

/// GET /v1/monitor-statuses/response-time-history-24h
struct ResponseTimeHistoryDTO: Decodable {
    let monitorId: Int
    /// Rounded down. Null when there are no checks.
    let uptimePercentage24h: Double?
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
