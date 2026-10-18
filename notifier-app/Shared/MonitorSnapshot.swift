//
//  MonitorSnapshot.swift
//  StatusTacosAlert
//

import Foundation

/// Up/down state of all monitors. The app writes it to the App Group, the widget reads it.
struct MonitorSnapshot: Codable, Equatable {
    enum Status: String, Codable {
        case up, down, unknown
    }

    /// Why the widget data is not live.
    enum Notice: String, Codable {
        case noConnection, signedOut, appNotRunning
    }

    struct Monitor: Codable, Equatable {
        let name: String
        let tenantName: String
        let status: Status
        let downSince: Date?
    }

    var monitors: [Monitor]
    var notice: Notice?

    static let widgetKind = "StatusTacosAlertWidget"
    /// A click on the widget opens this URL. The app then opens the monitors page of the server.
    static let openMonitorsURL = URL(string: "de.oglimmer.statustacos.alert:/monitors")!
    private static let storageKey = "monitorSnapshot"

    /// The App Group is set in Info.plist, so the team ID is not written in the code.
    private static var defaults: UserDefaults? {
        guard let group = Bundle.main.object(forInfoDictionaryKey: "AppGroupIdentifier") as? String else { return nil }
        return UserDefaults(suiteName: group)
    }

    static func load() -> MonitorSnapshot? {
        guard let data = defaults?.data(forKey: storageKey) else { return nil }
        return try? JSONDecoder().decode(MonitorSnapshot.self, from: data)
    }

    func save() {
        guard let data = try? JSONEncoder().encode(self) else { return }
        Self.defaults?.set(data, forKey: Self.storageKey)
    }
}
