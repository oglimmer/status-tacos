//
//  MonitorsStore.swift
//  StatusTacosAlert
//

import Foundation

/// The monitors for the list in the popover and the window.
/// Refreshes only while the user is signed in and at least one of them is on screen.
@MainActor
final class MonitorsStore: ObservableObject {
    /// A place that shows the list.
    enum Viewer: Hashable {
        case popover, window
    }

    @Published private(set) var monitors: [MonitorSummary] = []
    @Published private(set) var lastUpdated: Date?
    @Published private(set) var errorMessage: String?
    /// Tenants of the user, from /users/me. Nil until loaded.
    @Published private var userTenants: [Tenant]?

    /// Time frame of the uptime. A change loads the data again.
    var timeframe: Timeframe = .week {
        didSet { if timeframe != oldValue { Task { await refresh() } } }
    }

    private let api: APIClient
    private var refreshTask: Task<Void, Never>?
    private var visibleViewers: Set<Viewer> = []
    private var isSignedIn = false
    private var isLoading = false
    private var refreshAgain = false

    /// The backend checks each monitor every 15 seconds, so a shorter interval shows nothing new.
    private static let refreshInterval: Duration = .seconds(15)

    init(api: APIClient) {
        self.api = api
    }

    /// Tenants of the user, by name. Falls back to the tenants of the monitors when /users/me fails.
    var tenants: [Tenant] {
        let tenants = userTenants ?? Array(Set(monitors.compactMap(\.tenant)))
        return tenants.sorted { $0.name.localizedStandardCompare($1.name) == .orderedAscending }
    }

    /// Call when a viewer comes on screen or goes away.
    func setVisible(_ isVisible: Bool, for viewer: Viewer) {
        if isVisible {
            visibleViewers.insert(viewer)
        } else {
            visibleViewers.remove(viewer)
        }
        updateRefreshing()
    }

    /// Signed out: stops the refresh and clears all data, so the next user sees none of it.
    func setSignedIn(_ signedIn: Bool) {
        isSignedIn = signedIn
        if !signedIn {
            monitors = []
            userTenants = nil
            lastUpdated = nil
            errorMessage = nil
        }
        updateRefreshing()
    }

    private func updateRefreshing() {
        let shouldRefresh = isSignedIn && !visibleViewers.isEmpty
        if shouldRefresh, refreshTask == nil {
            refreshTask = Task { [weak self] in
                while !Task.isCancelled {
                    await self?.refresh()
                    try? await Task.sleep(for: Self.refreshInterval)
                }
            }
        } else if !shouldRefresh {
            refreshTask?.cancel()
            refreshTask = nil
        }
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
            if userTenants == nil {
                // On the first sign-in, /users/me creates the user and its tenant. So it runs first.
                userTenants = try? await api.currentUser().tenants
            }
            let timeframe = timeframe
            async let monitors = api.monitors()
            async let statuses = api.monitorStatuses()
            async let uptimes = loadUptimes(timeframe)
            let merged = MonitorSummary.merge(
                monitors: try await monitors, statuses: try await statuses, uptimes: await uptimes)
            // The time frame may have changed while the requests ran: then a new load follows.
            guard timeframe == self.timeframe else { refreshAgain = true; return }
            self.monitors = merged
            lastUpdated = .now
            errorMessage = nil
        } catch is CancellationError {
            // The popover closed. Keep the old data.
        } catch let error as URLError where error.code == .cancelled {
            // Same as above, from URLSession.
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    /// The list still works without the uptime: an error gives no values.
    private func loadUptimes(_ timeframe: Timeframe) async -> [Int: Double] {
        let pairs: [(Int, Double?)]
        switch timeframe {
        case .day:
            pairs = ((try? await api.responseTimeHistory24hOfAllMonitors()) ?? []).map { ($0.monitorId, $0.uptimePercentage24h) }
        case .week:
            pairs = ((try? await api.uptimeStatsOfAllMonitors(period: .sevenDays)) ?? []).map { ($0.monitorId, $0.uptimePercentage) }
        case .quarter:
            pairs = ((try? await api.uptimeStatsOfAllMonitors(period: .ninetyDays)) ?? []).map { ($0.monitorId, $0.uptimePercentage) }
        }
        return Dictionary(pairs.compactMap { id, value in value.map { (id, $0) } }) { first, _ in first }
    }
}
