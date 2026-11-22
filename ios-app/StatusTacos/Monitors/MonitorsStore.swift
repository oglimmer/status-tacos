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
    /// Monitors with a "check now" that has not run yet.
    private(set) var checkQueuedIDs: Set<Int> = []
    /// Time frame of the uptime in the list. Call `refresh()` after a change.
    var timeframe: Timeframe = .week
    /// Tenants of the user, from /users/me. Nil until loaded.
    private var userTenants: [Tenant]?

    @ObservationIgnored private let api: APIClient
    /// A refresh was asked for while one was running: its data may be too old.
    @ObservationIgnored private var refreshAgain = false
    /// Time from "check now" to the reload: the next run (at most 15 s), its start spread and the check.
    @ObservationIgnored private let checkWait: Duration

    init(api: APIClient, checkWait: Duration = .seconds(20)) {
        self.api = api
        self.checkWait = checkWait
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
            let timeframe = timeframe
            async let uptimes = loadUptimes(timeframe)
            self.monitors = MonitorSummary.merge(
                monitors: try await monitors,
                statuses: try await statuses,
                uptimes: await uptimes,
                timeframe: timeframe)
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

    /// The list still works without the uptime: an error gives an empty list.
    private func loadUptimes(_ timeframe: Timeframe) async -> [MonitorUptime] {
        switch timeframe {
        case .day:
            let histories = (try? await api.responseTimeHistory24hOfAllMonitors()) ?? []
            let fetchedAt = Date.now
            return histories.map { MonitorUptime(history: $0, fetchedAt: fetchedAt) }
        case .week:
            return ((try? await api.uptimeStatsOfAllMonitors(period: .sevenDays)) ?? []).map(MonitorUptime.init(stats:))
        case .quarter:
            return ((try? await api.uptimeStatsOfAllMonitors(period: .ninetyDays)) ?? []).map(MonitorUptime.init(stats:))
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

    /// Check now: asks the server for a check, waits for it and then loads the new status.
    /// Throws when the server refuses, for example in the daily pause. A second call while a check
    /// is queued does nothing.
    func checkNow(monitor id: Int) async throws {
        guard !checkQueuedIDs.contains(id) else { return }
        checkQueuedIDs.insert(id)
        defer { checkQueuedIDs.remove(id) }
        try await api.requestCheck(monitorID: id)
        try? await Task.sleep(for: checkWait)
        await refresh()
    }
}
