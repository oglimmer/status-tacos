import Foundation
import Observation
import UIKit
import UserNotifications

/// Push alerts of the signed-in user: the permission of this device, the device token and one push
/// alert per tenant (for all monitors of the tenant or for selected monitors).
@MainActor
@Observable
final class PushStore {
    enum Permission: Equatable {
        case unknown
        case notDetermined
        case denied
        case allowed
    }

    /// What the system calls do. Replaced in tests.
    struct System {
        var permission: @MainActor () async -> Permission
        var requestPermission: @MainActor () async throws -> Bool
        var registerForRemoteNotifications: @MainActor () -> Void

        static let live = System(
            permission: {
                switch await UNUserNotificationCenter.current().notificationSettings().authorizationStatus {
                case .notDetermined: .notDetermined
                case .denied: .denied
                case .authorized, .provisional, .ephemeral: .allowed
                @unknown default: .unknown
                }
            },
            requestPermission: {
                try await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound])
            },
            registerForRemoteNotifications: { UIApplication.shared.registerForRemoteNotifications() })
    }

    private(set) var permission: Permission = .unknown
    /// From the server. Nil until loaded.
    private(set) var serverEnabled: Bool?
    private(set) var deviceCount: Int?
    private(set) var alertsByTenant: [Int: PushAlertDTO] = [:]
    private(set) var hasLoaded = false
    private(set) var savingTenantIDs: Set<Int> = []
    /// Hex APNs token of this device, when iOS gave one.
    private(set) var deviceToken: String?
    private(set) var registrationError: String?
    var errorMessage: String?
    var infoMessage: String?

    @ObservationIgnored private let api: APIClient
    @ObservationIgnored private let system: System
    @ObservationIgnored private let environment: PushDeviceRequest.Environment

    init(api: APIClient, system: System = .live, environment: PushDeviceRequest.Environment = PushStore.buildEnvironment) {
        self.api = api
        self.system = system
        self.environment = environment
    }

    /// Debug builds are signed for the APNs sandbox, App Store and TestFlight builds for production.
    nonisolated static var buildEnvironment: PushDeviceRequest.Environment {
        #if DEBUG
        .sandbox
        #else
        .production
        #endif
    }

    // MARK: - Device

    /// After sign-in and when the app comes to the foreground. iOS can change the token, so the
    /// app registers again on every start.
    func start() async {
        await refreshPermission()
        if permission == .allowed {
            system.registerForRemoteNotifications()
        }
        await load()
    }

    func refreshPermission() async {
        permission = await system.permission()
    }

    /// Shows the system question. Only works while the permission is not determined.
    func requestPermission() async {
        do {
            _ = try await system.requestPermission()
        } catch {
            errorMessage = error.localizedDescription
        }
        await refreshPermission()
        if permission == .allowed {
            system.registerForRemoteNotifications()
        }
    }

    /// From the app delegate.
    func didRegister(deviceToken data: Data) async {
        let token = data.map { String(format: "%02x", $0) }.joined()
        deviceToken = token
        registrationError = nil
        do {
            try await api.registerPushDevice(PushDeviceRequest(
                token: token, environment: environment, deviceName: UIDevice.current.name))
            deviceCount = try? await api.pushStatus().devices
        } catch {
            registrationError = error.localizedDescription
        }
    }

    /// From the app delegate. In the simulator without a signed team, iOS gives no token.
    func didFailToRegister(_ error: Error) {
        registrationError = error.localizedDescription
    }

    /// Before sign-out: this device must not get alerts of the user any more.
    func unregisterDevice() async {
        if let deviceToken {
            try? await api.unregisterPushDevice(token: deviceToken)
        }
        reset()
    }

    /// Forgets everything of the user. The permission stays: it belongs to the device.
    func reset() {
        serverEnabled = nil
        deviceCount = nil
        alertsByTenant = [:]
        hasLoaded = false
        deviceToken = nil
        errorMessage = nil
        infoMessage = nil
    }

    // MARK: - Push alerts

    func load() async {
        do {
            async let status = api.pushStatus()
            async let alerts = api.pushAlerts()
            let loadedStatus = try await status
            serverEnabled = loadedStatus.enabled
            deviceCount = loadedStatus.devices
            alertsByTenant = Dictionary(
                try await alerts.compactMap { alert in alert.tenant.map { ($0.id, alert) } }
            ) { first, _ in first }
            hasLoaded = true
        } catch is CancellationError {
        } catch let error as URLError where error.code == .cancelled {
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func alert(forTenant tenantID: Int) -> PushAlertDTO? {
        alertsByTenant[tenantID]
    }

    /// Push alerts for every monitor of the tenant, or none.
    func setEnabled(_ enabled: Bool, forTenant tenantID: Int) async {
        if enabled {
            await askPermissionOnFirstUse()
            let existing = alertsByTenant[tenantID]
            await save(PushAlertRequest(
                isActive: true,
                allMonitors: existing?.allMonitors ?? true,
                monitorIds: Array(existing?.monitorIDs ?? [])), forTenant: tenantID)
        } else {
            await delete(tenantID: tenantID)
        }
    }

    func save(_ request: PushAlertRequest, forTenant tenantID: Int) async {
        guard !savingTenantIDs.contains(tenantID) else { return }
        savingTenantIDs.insert(tenantID)
        defer { savingTenantIDs.remove(tenantID) }
        do {
            alertsByTenant[tenantID] = try await api.savePushAlert(tenantID: tenantID, request)
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func delete(tenantID: Int) async {
        guard !savingTenantIDs.contains(tenantID) else { return }
        savingTenantIDs.insert(tenantID)
        defer { savingTenantIDs.remove(tenantID) }
        do {
            try await api.deletePushAlert(tenantID: tenantID)
            alertsByTenant[tenantID] = nil
        } catch APIError.notFound {
            alertsByTenant[tenantID] = nil
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func sendTest(tenantID: Int) async {
        do {
            try await api.sendPushTest(tenantID: tenantID)
            infoMessage = "Test notification sent. It arrives in a few seconds."
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    /// The first push alert the user turns on shows the system question.
    private func askPermissionOnFirstUse() async {
        if permission == .unknown {
            await refreshPermission()
        }
        if permission == .notDetermined {
            await requestPermission()
        }
    }

    // MARK: - One monitor

    /// Whether this device gets push alerts for the monitor.
    func coverage(monitorID: Int, tenantID: Int) -> PushCoverage {
        PushCoverage(alert: alertsByTenant[tenantID], monitorID: monitorID)
    }

    /// Turns push alerts for one monitor on or off.
    func setMonitor(_ monitorID: Int, tenantID: Int, enabled: Bool) async {
        if enabled {
            await askPermissionOnFirstUse()
        }
        switch PushCoverage.change(existing: alertsByTenant[tenantID], monitorID: monitorID, enabled: enabled) {
        case .save(let request): await save(request, forTenant: tenantID)
        case .delete: await delete(tenantID: tenantID)
        case .none: break
        }
    }
}

/// Push alerts of one monitor.
enum PushCoverage: Equatable {
    case off
    /// The push alert of the tenant is paused.
    case paused
    /// Selected in the push alert of the tenant.
    case selected
    /// The push alert of the tenant covers all its monitors.
    case allMonitors

    init(alert: PushAlertDTO?, monitorID: Int) {
        guard let alert else { self = .off; return }
        if alert.allMonitors {
            self = alert.isActive ? .allMonitors : .paused
        } else if alert.monitorIDs.contains(monitorID) {
            self = alert.isActive ? .selected : .paused
        } else {
            self = .off
        }
    }

    var isOn: Bool { self == .selected || self == .allMonitors }

    enum Change: Equatable {
        case save(PushAlertRequest)
        case delete
        case none
    }

    /// The request that turns push alerts for one monitor on or off. An alert for all monitors is
    /// not changed here: the user changes it in the notification settings.
    static func change(existing: PushAlertDTO?, monitorID: Int, enabled: Bool) -> Change {
        guard let existing else {
            return enabled ? .save(PushAlertRequest(isActive: true, allMonitors: false, monitorIds: [monitorID])) : .none
        }
        if existing.allMonitors {
            return enabled && !existing.isActive
                ? .save(PushAlertRequest(isActive: true, allMonitors: true, monitorIds: []))
                : .none
        }
        var ids = existing.monitorIDs
        if enabled { ids.insert(monitorID) } else { ids.remove(monitorID) }
        if ids.isEmpty { return .delete }
        return .save(PushAlertRequest(isActive: enabled ? true : existing.isActive, allMonitors: false, monitorIds: ids.sorted()))
    }
}

/// The custom keys of the notifications of the backend (PushMessage.java).
enum PushPayload {
    static func monitorID(from userInfo: [AnyHashable: Any]) -> Int? {
        switch userInfo["monitorId"] {
        case let value as Int: value
        case let value as NSNumber: value.intValue
        case let value as String: Int(value)
        default: nil
        }
    }
}
