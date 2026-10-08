import SwiftUI

struct MonitorListView: View {
    @State private var store: MonitorsStore
    @State private var searchText = ""
    @State private var showsSettings = false
    @State private var showsNotifications = false
    @State private var stateChangeError: String?
    /// 0 means all tenants.
    @AppStorage("monitorList.tenantFilter") private var tenantFilter = 0
    @Environment(\.scenePhase) private var scenePhase
    @Environment(APIClient.self) private var api

    private static let refreshInterval: Duration = .seconds(30)

    init(api: APIClient) {
        _store = State(initialValue: MonitorsStore(api: api))
    }

    var body: some View {
        List {
            if !store.tenants.isEmpty {
                Section {
                    tenantPicker
                }
            }

            if !store.monitors.isEmpty {
                Section {
                    SummaryHeader(monitors: filteredByTenant)
                }
                .listRowInsets(EdgeInsets())
                .listRowBackground(Color.clear)
            }

            if let error = store.errorMessage, !store.monitors.isEmpty {
                Section {
                    Label(error, systemImage: "exclamationmark.triangle.fill")
                        .foregroundStyle(.orange)
                        .font(.subheadline)
                }
            }

            ForEach(MonitorHealth.allCases, id: \.self) { health in
                let rows = visibleMonitors.filter { $0.health == health }
                if !rows.isEmpty {
                    Section {
                        ForEach(rows) { monitor in
                            NavigationLink(value: monitor.id) {
                                MonitorRow(monitor: monitor, showsTenant: showsTenant,
                                           isUpdating: store.updatingStateIDs.contains(monitor.id))
                            }
                            .swipeActions(edge: .trailing) { swipeActions(for: monitor) }
                            .contextMenu { stateMenu(for: monitor) }
                        }
                    } header: {
                        Text("\(health.title) (\(rows.count))")
                    }
                }
            }

            if let lastUpdated = store.lastUpdated {
                Section {
                } footer: {
                    Text("Updated \(lastUpdated, format: .dateTime.hour().minute().second())")
                        .frame(maxWidth: .infinity)
                }
            }
        }
        .listStyle(.insetGrouped)
        .overlay { emptyState }
        .navigationTitle("Monitors")
        .navigationDestination(for: Int.self) { id in
            MonitorDetailView(monitorID: id, monitors: store, api: api)
        }
        .searchable(text: $searchText, prompt: "Name or URL")
        .refreshable { await store.refresh() }
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button("Notifications", systemImage: "bell.badge") { showsNotifications = true }
            }
            ToolbarItem(placement: .topBarTrailing) {
                Button("Settings", systemImage: "gearshape") { showsSettings = true }
            }
        }
        .sheet(isPresented: $showsNotifications) { NotificationsView(monitors: store) }
        .sheet(isPresented: $showsSettings) { SettingsView() }
        .alert("Cannot change the monitor", isPresented: Binding(
            get: { stateChangeError != nil },
            set: { if !$0 { stateChangeError = nil } }
        )) {
            Button("OK") { stateChangeError = nil }
        } message: {
            Text(stateChangeError ?? "")
        }
        .onChange(of: store.tenants.map(\.id)) { _, ids in
            // The saved tenant may be gone (removed, or another user signed in).
            if tenantFilter != 0, !ids.isEmpty, !ids.contains(tenantFilter) {
                tenantFilter = 0
            }
        }
        .task(id: scenePhase) {
            // Refreshes while the list is on screen and the app is in the foreground.
            guard scenePhase == .active else { return }
            while !Task.isCancelled {
                await store.refresh()
                try? await Task.sleep(for: Self.refreshInterval)
            }
        }
    }

    private var showsTenant: Bool { store.tenants.count > 1 && tenantFilter == 0 }

    private var filteredByTenant: [MonitorSummary] {
        guard tenantFilter != 0, store.tenants.contains(where: { $0.id == tenantFilter }) else {
            return store.monitors
        }
        return store.monitors.filter { $0.tenant?.id == tenantFilter }
    }

    private var visibleMonitors: [MonitorSummary] {
        let query = searchText.trimmingCharacters(in: .whitespaces)
        guard !query.isEmpty else { return filteredByTenant }
        return filteredByTenant.filter {
            $0.name.localizedCaseInsensitiveContains(query) || $0.url.localizedCaseInsensitiveContains(query)
        }
    }

    // MARK: - State changes

    /// The most common changes: silence or turn on alerts, and pause or resume checks.
    @ViewBuilder
    private func swipeActions(for monitor: MonitorSummary) -> some View {
        switch monitor.state {
        case .active:
            stateButton(.inactive, for: monitor, title: "Pause")
            stateButton(.silent, for: monitor, title: "Silence")
        case .silent:
            stateButton(.inactive, for: monitor, title: "Pause")
            stateButton(.active, for: monitor, title: "Alerts On")
        case .inactive:
            stateButton(.active, for: monitor, title: "Resume")
        case .unknown:
            EmptyView()
        }
    }

    private func stateButton(_ state: MonitorState, for monitor: MonitorSummary, title: String) -> some View {
        Button(title, systemImage: state.symbol) { changeState(of: monitor, to: state) }
            .tint(state.color)
            .disabled(store.updatingStateIDs.contains(monitor.id))
    }

    /// All states, for a long press on a row.
    @ViewBuilder
    private func stateMenu(for monitor: MonitorSummary) -> some View {
        Section("Monitoring") {
            ForEach(MonitorState.selectable, id: \.self) { state in
                Button {
                    changeState(of: monitor, to: state)
                } label: {
                    Label(state.title, systemImage: monitor.state == state ? "checkmark" : state.symbol)
                }
                .disabled(monitor.state == state || store.updatingStateIDs.contains(monitor.id))
            }
        }
    }

    private func changeState(of monitor: MonitorSummary, to state: MonitorState) {
        Task {
            do {
                try await store.setState(state, forMonitor: monitor.id)
            } catch {
                stateChangeError = error.localizedDescription
            }
        }
    }

    // MARK: - Tenants

    private var tenantPicker: some View {
        Picker(selection: $tenantFilter) {
            Text("All tenants").tag(0)
            ForEach(store.tenants) { tenant in
                Text(tenant.name).tag(tenant.id)
            }
        } label: {
            Label("Tenant", systemImage: "building.2")
        }
        .pickerStyle(.menu)
    }

    private var selectedTenant: Tenant? {
        store.tenants.first { $0.id == tenantFilter }
    }

    @ViewBuilder
    private var emptyState: some View {
        if store.monitors.isEmpty {
            if let error = store.errorMessage {
                ContentUnavailableView {
                    Label("Cannot load monitors", systemImage: "wifi.exclamationmark")
                } description: {
                    Text(error)
                } actions: {
                    Button("Try Again") { Task { await store.refresh() } }
                        .buttonStyle(.borderedProminent)
                }
            } else if store.lastUpdated == nil {
                ProgressView("Loading monitors…")
            } else {
                ContentUnavailableView(
                    "No monitors", systemImage: "waveform.path.ecg",
                    description: Text("Add monitors in the web app."))
            }
        } else if visibleMonitors.isEmpty && !searchText.isEmpty {
            ContentUnavailableView.search(text: searchText)
        } else if filteredByTenant.isEmpty, let tenant = selectedTenant {
            ContentUnavailableView(
                "No monitors in \(tenant.name)", systemImage: "building.2",
                description: Text("Pick another tenant or add monitors in the web app."))
        }
    }
}

/// Counts per health, with the most important first.
private struct SummaryHeader: View {
    let monitors: [MonitorSummary]

    var body: some View {
        HStack(spacing: 10) {
            tile(.down)
            tile(.up)
            tile(.paused)
        }
    }

    private func tile(_ health: MonitorHealth) -> some View {
        let count = monitors.filter { $0.health == health }.count
        let highlighted = health == .down && count > 0
        return VStack(alignment: .leading, spacing: 4) {
            Label(health.title, systemImage: health.symbol)
                .font(.caption.weight(.semibold))
                .foregroundStyle(highlighted ? .white : health.color)
            Text("\(count)")
                .font(.title.weight(.bold).monospacedDigit())
                .foregroundStyle(highlighted ? .white : .primary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(highlighted ? AnyShapeStyle(Color.red) : AnyShapeStyle(Color(.secondarySystemGroupedBackground)),
                    in: .rect(cornerRadius: 12))
        .accessibilityElement(children: .combine)
    }
}
