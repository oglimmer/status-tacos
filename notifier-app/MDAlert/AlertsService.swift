//
//  AlertsService.swift
//  MDAlert
//
//  Created by Oliver Zimpasser on 6/26/25.
//

import Foundation
import UserNotifications
import AppKit

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

class AlertsService: NSObject, ObservableObject, UNUserNotificationCenterDelegate {
    @Published var alerts: [AlertItem] = []
    @Published var isPolling = false

    private var pollingTimer: Timer?
    private let notificationCenter = UNUserNotificationCenter.current()
    private var previousDownAlerts: Set<String> = []

    private let alertsURL = "http://localhost:8080/api/v1/alerts"

    override init() {
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

    func startPolling(accessToken: String) {
        guard !isPolling else { return }

        isPolling = true

        pollingTimer = Timer.scheduledTimer(withTimeInterval: 5.0, repeats: true) { [weak self] _ in
            self?.fetchAlerts(accessToken: accessToken)
        }

        fetchAlerts(accessToken: accessToken)
    }

    func stopPolling() {
        pollingTimer?.invalidate()
        pollingTimer = nil
        isPolling = false
        alerts.removeAll()
        previousDownAlerts.removeAll()
    }

    func printHTTPRequest(_ request: URLRequest) {
        print("🌐 HTTP Request Details:")
        print("URL: \(request.url?.absoluteString ?? "N/A")")
        print("Method: \(request.httpMethod ?? "GET")")

        // Print headers
        if let headers = request.allHTTPHeaderFields, !headers.isEmpty {
            print("Headers:")
            for (key, value) in headers {
                print("  \(key): \(value)")
            }
        }

        // Print body
        if let body = request.httpBody {
            print("Body:")
            if let bodyString = String(data: body, encoding: .utf8) {
                print("  \(bodyString)")
            } else {
                print("  <Binary data: \(body.count) bytes>")
            }
        }

        print("Timeout: \(request.timeoutInterval)s")
        print("Cache Policy: \(request.cachePolicy.rawValue)")
        print("---")
    }

    private func fetchAlerts(accessToken: String) {
        guard let url = URL(string: alertsURL) else { return }

        var request = URLRequest(url: url)
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")

        printHTTPRequest(request)


        URLSession.shared.dataTask(with: request) { [weak self] data, response, error in
            DispatchQueue.main.async {
                if let error = error {
                    print("❌ Failed to fetch alerts: \(error.localizedDescription)")
                    return
                }

                guard let data = data else {
                    print("❌ No alerts data received")
                    return
                }

                do {
                    let fetchedAlerts = try JSONDecoder().decode([AlertItem].self, from: data)
                    self?.processAlerts(fetchedAlerts)
                } catch {
                    print("❌ Failed to parse alerts: \(error.localizedDescription)")
                }
            }
        }.resume()
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
        content.userInfo = ["url": "http://localhost:5173/monitors"]

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
        let formatter = ISO8601DateFormatter()
        if let date = formatter.date(from: downtimeStart) {
            let displayFormatter = DateFormatter()
            displayFormatter.dateStyle = .none
            displayFormatter.timeStyle = .short
            return displayFormatter.string(from: date)
        }
        return downtimeStart
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse, withCompletionHandler completionHandler: @escaping () -> Void) {
        if let url = response.notification.request.content.userInfo["url"] as? String {
            if let nsUrl = URL(string: url) {
                NSWorkspace.shared.open(nsUrl)
            }
        }
        completionHandler()
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification, withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        completionHandler([.banner, .sound])
    }
}
