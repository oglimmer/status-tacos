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
    /// Nil from older servers.
    let monitorId: Int?
    let monitorName: String
    let tenantName: String
    let status: String
    let downtimeStart: String?
    /// PROD, STAGE or DEV. Nil when not set, and from older servers.
    let environment: String?
    /// ACTIVE, SILENT or INACTIVE. Nil from older servers.
    let state: String?

    enum CodingKeys: String, CodingKey {
        case monitorId, monitorName, tenantName, status, downtimeStart, environment, state
    }
}

@MainActor
class AlertsService: NSObject, ObservableObject, UNUserNotificationCenterDelegate {
    @Published var alerts: [AlertItem] = []
    @Published var isPolling = false
    /// Set while polling fails, so a broken connection is visible and not silent.
    @Published var connectionProblem: String?

    private let api: APIClient
    private let settings: AppSettings
    private var pollingTimer: Timer?
    private var isFetching = false
    private let notificationCenter = UNUserNotificationCenter.current()
    private var previousDownAlerts: Set<String> = []
    private var lastSnapshot = MonitorSnapshot.load()
    /// 24h uptime by monitor ID, for the widget. Loaded less often than the alerts.
    private var uptimes24h: [Int: Double] = [:]
    private var uptimesLoadedAt: Date?
    /// Each change reloads the widget, and macOS limits reloads. So the uptime is not loaded on each poll.
    private static let uptimeRefreshInterval: TimeInterval = 15 * 60

    init(api: APIClient, settings: AppSettings) {
        self.api = api
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
        uptimes24h = [:]
        uptimesLoadedAt = nil
        previousDownAlerts.removeAll()
        connectionProblem = nil
        publishSnapshot(MonitorSnapshot(monitors: [], notice: .signedOut))
    }

    private func fetchAlerts() async {
        guard isPolling, !isFetching else { return }
        isFetching = true
        defer { isFetching = false }

        do {
            let data = try await api.data("alerts")
            let fetchedAlerts = try JSONDecoder().decode([AlertItem].self, from: data)
            // Polling can stop while the request runs.
            guard isPolling else { return }
            connectionProblem = nil
            processAlerts(fetchedAlerts)
            await refreshUptimesIfDue()
            guard isPolling else { return }
            publishSnapshot(MonitorSnapshot(monitors: fetchedAlerts.map(snapshotMonitor), notice: nil))
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

    /// An error keeps the old values: the widget then shows no uptime or an old one, never a wrong status.
    private func refreshUptimesIfDue() async {
        if let uptimesLoadedAt, Date.now.timeIntervalSince(uptimesLoadedAt) < Self.uptimeRefreshInterval { return }
        uptimesLoadedAt = .now
        guard let history = try? await api.responseTimeHistory24hOfAllMonitors() else { return }
        uptimes24h = Dictionary(history.compactMap { item in item.uptimePercentage24h.map { (item.monitorId, $0) } }) { first, _ in first }
    }

    private func snapshotMonitor(_ alert: AlertItem) -> MonitorSnapshot.Monitor {
        MonitorSnapshot.Monitor(
            name: alert.monitorName,
            tenantName: alert.tenantName,
            status: MonitorSnapshot.Status(rawValue: alert.status) ?? .unknown,
            downSince: alert.downtimeStart.flatMap(BackendDate.parse),
            environment: alert.environment,
            isPaused: alert.state == "INACTIVE",
            uptime24h: alert.monitorId.flatMap { uptimes24h[$0] }
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
