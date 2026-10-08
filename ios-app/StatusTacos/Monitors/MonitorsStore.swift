import Foundation
import Observation

@MainActor
@Observable
final class MonitorsStore {
    private(set) var monitors: [MonitorSummary] = []
    private(set) var isLoading = false
    private(set) var lastUpdated: Date?
    private(set) var errorMessage: String?
    /// Monitors with a state change in progress.
    private(set) var updatingStateIDs: Set<Int> = []
    /// Tenants of the user, from /users/me. Nil until loaded.
    private var userTenants: [Tenant]?

    @ObservationIgnored private let api: APIClient
    /// A refresh was asked for while one was running: its data may be too old.
    @ObservationIgnored private var refreshAgain = false

    init(api: APIClient) {
        self.api = api
    }

    /// Tenants of the user, by name. Falls back to the tenants of the monitors when /users/me fails.
    var tenants: [Tenant] {
        let tenants = userTenants ?? Array(Set(monitors.compactMap(\.tenant)))
        return tenants.sorted { $0.name.localizedStandardCompare($1.name) == .orderedAscending }
    }

    func refresh() async {
        guard !isLoading else {
            refreshAgain = true
            return
        }
        isLoading = true
        defer { isLoading = false }
        repeat {
            refreshAgain = false
            await load()
        } while refreshAgain && !Task.isCancelled
    }

    private func load() async {
        do {
            let isFirstLoad = userTenants == nil
            if isFirstLoad {
                // On the first sign-in, /users/me creates the user and its tenant. Without it, the
                // backend falls back to the default tenant. So it runs before the other requests.
                userTenants = try? await api.currentUser().tenants
            }
            // Later refreshes pick up new tenants, in parallel with the monitors.
            async let user = isFirstLoad ? nil : try? api.currentUser()
            async let monitors = api.monitors()
            async let statuses = api.monitorStatuses()
            // The list still works without the 7-day stats.
            async let weekStats = try? api.uptimeStatsOfAllMonitors(period: .sevenDays)
            self.monitors = MonitorSummary.merge(
                monitors: try await monitors,
                statuses: try await statuses,
                weekStats: await weekStats ?? [])
            if let tenants = await user?.tenants {
                userTenants = tenants
            }
            lastUpdated = .now
            errorMessage = nil
        } catch is CancellationError {
            // The view went away. Keep the old data.
        } catch let error as URLError where error.code == .cancelled {
            // Same as above, from URLSession.
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    /// Changes the state on the server, then shows it here. Throws when the server refuses.
    func setState(_ state: MonitorState, forMonitor id: Int) async throws {
        guard !updatingStateIDs.contains(id) else { return }
        updatingStateIDs.insert(id)
        defer { updatingStateIDs.remove(id) }

        let updated = try await api.updateMonitorState(monitorID: id, state: state)
        monitors = monitors
            .map { $0.id == id ? $0.with(state: updated.state) : $0 }
            .sortedForDisplay()
        // A monitor that is active again has an old status: load the current one.
        Task { await refresh() }
    }
}
