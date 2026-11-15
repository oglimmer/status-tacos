//
//  APIClient.swift
//  StatusTacosAlert
//

import Foundation

enum APIError: LocalizedError {
    case http(Int)
    case invalidResponse(String)

    var errorDescription: String? {
        switch self {
        case .http(let status): return "The server answered with HTTP \(status)"
        case .invalidResponse(let reason): return "The server sent data the app cannot read. \(reason)"
        }
    }
}

/// GET requests to the Status Tacos REST API with the token of the signed-in user.
@MainActor
final class APIClient {
    private let authService: AuthenticationService
    private let settings: AppSettings

    init(authService: AuthenticationService, settings: AppSettings) {
        self.authService = authService
        self.settings = settings
    }

    func currentUser() async throws -> CurrentUserDTO {
        try await get("users/me")
    }

    func monitors() async throws -> [MonitorDTO] {
        try await get("monitors")
    }

    /// Status of all monitors that are ACTIVE or SILENT.
    func monitorStatuses() async throws -> [MonitorStatusDTO] {
        try await get("monitor-statuses")
    }

    func uptimeStatsOfAllMonitors(period: StatsPeriod) async throws -> [UptimeStatsDTO] {
        try await get("uptime-stats", query: [URLQueryItem(name: "period", value: period.pathValue)])
    }

    /// The 24h history of all monitors, in one request.
    func responseTimeHistory24hOfAllMonitors() async throws -> [ResponseTimeHistoryDTO] {
        try await get("monitor-statuses/response-time-history-24h")
    }

    func get<T: Decodable>(_ path: String, query: [URLQueryItem] = []) async throws -> T {
        let data = try await data(path, query: query)
        do {
            return try JSONDecoder.backend().decode(T.self, from: data)
        } catch {
            throw APIError.invalidResponse(String(describing: error))
        }
    }

    /// The body of a GET request. Refreshes the token once after a 401.
    func data(_ path: String, query: [URLQueryItem] = []) async throws -> Data {
        var components = URLComponents(url: settings.apiBaseURL.appending(path: path), resolvingAgainstBaseURL: false)!
        if !query.isEmpty { components.queryItems = query }
        let url = components.url!

        var retried = false
        while true {
            let token = try await authService.validAccessToken(forceRefresh: retried)
            var request = URLRequest(url: url)
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
            request.setValue("application/json", forHTTPHeaderField: "Accept")

            let (data, response) = try await URLSession.shared.data(for: request)
            let status = (response as? HTTPURLResponse)?.statusCode ?? 0
            switch status {
            case 200..<300:
                return data
            case 401 where !retried:
                // The token may have been revoked before it expired: refresh once and try again.
                retried = true
            case 401:
                authService.endSession(message: AuthError.sessionExpired.errorDescription)
                throw AuthError.sessionExpired
            default:
                throw APIError.http(status)
            }
        }
    }
}
