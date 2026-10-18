//
//  AlertsService.swift
//  StatusTacosAlert
//
//  Created by Oliver Zimpasser on 6/26/25.
//

import Foundation
import UserNotifications
import AppKit
import WidgetKit

struct AlertItem: Codable, Identifiable {
    let id = UUID()
    let monitorName: String
    let tenantName: String
    let status: String
    let downtimeStart: String?

    enum CodingKeys: String, CodingKey {
        case monitorName, tenantName, status, downtimeStart
    }
}

enum AlertsError: LocalizedError {
    case http(Int)

    var errorDescription: String? {
        switch self {
        case .http(let status): return "The server answered with HTTP \(status)"
        }
    }
}

@MainActor
class AlertsService: NSObject, ObservableObject, UNUserNotificationCenterDelegate {
    @Published var alerts: [AlertItem] = []
    @Published var isPolling = false
    /// Set while polling fails, so a broken connection is visible and not silent.
    @Published var connectionProblem: String?

    private let authService: AuthenticationService
    private let settings: AppSettings
    private var pollingTimer: Timer?
    private var isFetching = false
    private let notificationCenter = UNUserNotificationCenter.current()
    private var previousDownAlerts: Set<String> = []
    private var lastSnapshot = MonitorSnapshot.load()

    init(authService: AuthenticationService, settings: AppSettings) {
        self.authService = authService
        self.settings = settings
        super.init()
        requestNotificationPermission()
        notificationCenter.delegate = self
    }

    private func requestNotificationPermission() {
        notificationCenter.requestAuthorization(options: [.alert, .sound, .badge]) { granted, error in
            if granted {
                print("✅ Notification permission granted")
            } else if let error = error {
                print("❌ Notification permission error: \(error.localizedDescription)")
            } else {
                print("❌ Notification permission denied")
            }
        }
    }

    func startPolling() {
        guard !isPolling else { return }

        isPolling = true

        pollingTimer = Timer.scheduledTimer(withTimeInterval: 5.0, repeats: true) { [weak self] _ in
            Task { @MainActor in await self?.fetchAlerts() }
        }

        Task { await fetchAlerts() }
    }

    func stopPolling() {
        pollingTimer?.invalidate()
        pollingTimer = nil
        isPolling = false
        alerts.removeAll()
        previousDownAlerts.removeAll()
        connectionProblem = nil
        publishSnapshot(MonitorSnapshot(monitors: [], notice: .signedOut))
    }

    private func fetchAlerts() async {
        guard isPolling, !isFetching else { return }
        isFetching = true
        defer { isFetching = false }

        do {
            let data = try await authorizedGET(settings.apiBaseURL.appending(path: "alerts"))
            let fetchedAlerts = try JSONDecoder().decode([AlertItem].self, from: data)
            // Polling can stop while the request runs.
            guard isPolling else { return }
            connectionProblem = nil
            processAlerts(fetchedAlerts)
            publishSnapshot(MonitorSnapshot(monitors: fetchedAlerts.map(Self.snapshotMonitor), notice: nil))
        } catch {
            guard isPolling else { return }
            print("❌ Failed to fetch alerts: \(error.localizedDescription)")
            connectionProblem = error.localizedDescription
            var snapshot = lastSnapshot ?? MonitorSnapshot(monitors: [], notice: nil)
            snapshot.notice = .noConnection
            publishSnapshot(snapshot)
        }
    }

    // MARK: - Widget

    private static func snapshotMonitor(_ alert: AlertItem) -> MonitorSnapshot.Monitor {
        MonitorSnapshot.Monitor(
            name: alert.monitorName,
            tenantName: alert.tenantName,
            status: MonitorSnapshot.Status(rawValue: alert.status) ?? .unknown,
            downSince: alert.downtimeStart.flatMap(BackendDate.parse)
        )
    }

    /// Writes the state for the widget. Reloads the widget only on a change, because macOS limits reloads.
    func publishSnapshot(_ snapshot: MonitorSnapshot) {
        guard snapshot != lastSnapshot else { return }
        lastSnapshot = snapshot
        snapshot.save()
        WidgetCenter.shared.reloadTimelines(ofKind: MonitorSnapshot.widgetKind)
    }

    /// Called when the app quits, so the widget does not show old data as live.
    func markNotRunning() {
        guard var snapshot = lastSnapshot else { return }
        snapshot.notice = .appNotRunning
        publishSnapshot(snapshot)
    }

    private func authorizedGET(_ url: URL) async throws -> Data {
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
                throw AlertsError.http(status)
            }
        }
    }

    private func processAlerts(_ newAlerts: [AlertItem]) {
        alerts = newAlerts

        let currentDownAlerts = Set(newAlerts.filter { $0.status == "down" }.map { "\($0.tenantName):\($0.monitorName)" })

        // Clear acknowledgement for monitors that are now up
        let recoveredAlerts = previousDownAlerts.subtracting(currentDownAlerts)
        for recoveredAlertKey in recoveredAlerts {
            print("🔄 Monitor recovered: \(recoveredAlertKey) - clearing acknowledgement")
        }

        let newDownAlerts = currentDownAlerts.subtracting(previousDownAlerts)

        for alertKey in newDownAlerts {
            if let alert = newAlerts.first(where: { "\($0.tenantName):\($0.monitorName)" == alertKey && $0.status == "down" }) {
                sendDownNotification(for: alert)
            }
        }

        previousDownAlerts = currentDownAlerts
    }

    private func sendDownNotification(for alert: AlertItem) {
        let content = UNMutableNotificationContent()
        content.title = "Monitor Down"
        content.body = "\(alert.monitorName) in \(alert.tenantName) is down"
        if let downtimeStart = alert.downtimeStart {
            content.body += " since \(formatDowntimeStart(downtimeStart))"
        }
        content.sound = UNNotificationSound.default
        content.userInfo = ["url": settings.monitorsPageURL.absoluteString]

        let request = UNNotificationRequest(
            identifier: "monitor-down-\(alert.tenantName)-\(alert.monitorName)",
            content: content,
            trigger: nil
        )

        notificationCenter.add(request) { error in
            if let error = error {
                print("Failed to send notification: \(error.localizedDescription)")
            } else {
                print("📢 Sent notification for down monitor: \(alert.monitorName)")
            }
        }
    }

    private func formatDowntimeStart(_ downtimeStart: String) -> String {
        if let date = BackendDate.parse(downtimeStart) {
            let displayFormatter = DateFormatter()
            displayFormatter.dateStyle = .none
            displayFormatter.timeStyle = .short
            return displayFormatter.string(from: date)
        }
        return downtimeStart
    }

    nonisolated func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse, withCompletionHandler completionHandler: @escaping () -> Void) {
        if let url = response.notification.request.content.userInfo["url"] as? String {
            if let nsUrl = URL(string: url) {
                NSWorkspace.shared.open(nsUrl)
            }
        }
        completionHandler()
    }

    nonisolated func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification, withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        completionHandler([.banner, .sound])
    }
}
