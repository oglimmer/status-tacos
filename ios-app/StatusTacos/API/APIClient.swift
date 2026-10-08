import Foundation
import Observation

enum APIError: LocalizedError, Equatable {
    case unauthorized
    case forbidden
    case notFound
    case http(Int)
    /// The server explained the error.
    case server(String)
    case invalidResponse(String)

    var errorDescription: String? {
        switch self {
        case .unauthorized: "The server did not accept your sign-in."
        case .forbidden: "You do not have access to this."
        case .notFound: "Not found. It may have been deleted."
        case .http(let status): "The server sent an error (HTTP \(status))."
        case .server(let message): message
        case .invalidResponse(let reason): "The server sent data the app cannot read. \(reason)"
        }
    }
}

/// The Status Tacos REST API with the token of the signed-in user.
@MainActor
@Observable
final class APIClient {
    @ObservationIgnored private let settings: AppSettings
    @ObservationIgnored private let auth: AuthStore
    @ObservationIgnored private let session: URLSession

    init(settings: AppSettings, auth: AuthStore, session: URLSession = .shared) {
        self.settings = settings
        self.auth = auth
        self.session = session
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

    /// One item per period (7 and 90 days).
    func uptimeStats(monitorID: Int) async throws -> [UptimeStatsDTO] {
        try await get("uptime-stats/\(monitorID)")
    }

    func responseTimeHistory24h(monitorID: Int) async throws -> ResponseTimeHistoryDTO {
        try await get("monitor-statuses/\(monitorID)/response-time-history-24h")
    }

    /// Active (checks and alerts), silent (checks, no alerts) or inactive (no checks).
    func updateMonitorState(monitorID: Int, state: MonitorState) async throws -> MonitorDTO {
        try await send("PATCH", "monitors/\(monitorID)/state", query: [URLQueryItem(name: "state", value: state.rawValue)])
    }

    // MARK: - iOS push alerts

    func pushStatus() async throws -> PushStatusDTO {
        try await get("push/status")
    }

    /// Saves the APNs token of this device for the signed-in user.
    func registerPushDevice(_ device: PushDeviceRequest) async throws {
        try await sendNoContent("PUT", "push/devices", body: device)
    }

    func unregisterPushDevice(token: String) async throws {
        try await sendNoContent("DELETE", "push/devices/\(token)")
    }

    /// The push alerts of the signed-in user, one per tenant at most.
    func pushAlerts() async throws -> [PushAlertDTO] {
        try await get("push/alerts")
    }

    func savePushAlert(tenantID: Int, _ request: PushAlertRequest) async throws -> PushAlertDTO {
        try await send("PUT", "push/alerts/\(tenantID)", body: request)
    }

    func deletePushAlert(tenantID: Int) async throws {
        try await sendNoContent("DELETE", "push/alerts/\(tenantID)")
    }

    func sendPushTest(tenantID: Int) async throws {
        try await sendNoContent("POST", "push/alerts/\(tenantID)/test")
    }

    // MARK: - Requests

    private func get<T: Decodable & Sendable>(_ path: String, query: [URLQueryItem] = []) async throws -> T {
        try await send("GET", path, query: query)
    }

    private func send<T: Decodable & Sendable>(
        _ method: String, _ path: String, query: [URLQueryItem] = [], body: (any Encodable & Sendable)? = nil
    ) async throws -> T {
        let data = try await perform(method, path, query: query, body: body)
        do {
            return try JSONDecoder.backend().decode(T.self, from: data)
        } catch {
            throw APIError.invalidResponse(String(describing: error))
        }
    }

    /// For answers without a body (204).
    private func sendNoContent(
        _ method: String, _ path: String, body: (any Encodable & Sendable)? = nil
    ) async throws {
        _ = try await perform(method, path, query: [], body: body)
    }

    private func perform(
        _ method: String, _ path: String, query: [URLQueryItem], body: (any Encodable & Sendable)?
    ) async throws -> Data {
        var components = URLComponents(url: settings.apiBaseURL.appending(path: path), resolvingAgainstBaseURL: false)!
        if !query.isEmpty { components.queryItems = query }
        let url = components.url!
        let bodyData = try body.map { try JSONEncoder().encode($0) }

        var retried = false
        while true {
            let token = try await auth.accessToken(forceRefresh: retried)
            var request = URLRequest(url: url)
            request.httpMethod = method
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
            request.setValue("application/json", forHTTPHeaderField: "Accept")
            if let bodyData {
                request.httpBody = bodyData
                request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            }

            let (data, response) = try await session.data(for: request)
            let status = (response as? HTTPURLResponse)?.statusCode ?? 0
            switch status {
            case 200..<300:
                return data
            case 401 where !retried:
                // The token may have been revoked before it expired: refresh once and try again.
                retried = true
            case 401:
                auth.endLocalSession(message: APIError.unauthorized.errorDescription)
                throw APIError.unauthorized
            case 403:
                throw APIError.forbidden
            case 404:
                throw APIError.notFound
            default:
                // The push endpoints explain 400 and 409 answers: {"message": "..."}.
                if let message = try? JSONDecoder().decode(ServerMessage.self, from: data).message {
                    throw APIError.server(message)
                }
                throw APIError.http(status)
            }
        }
    }
}

private struct ServerMessage: Decodable {
    let message: String
}
