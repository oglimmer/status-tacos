import SwiftUI

struct MonitorListView: View {
    @State private var store: MonitorsStore
    @State private var searchText = ""
    @State private var showsSettings = false
    @State private var showsNotifications = false
    @State private var stateChangeError: String?
    /// 0 means all tenants.
    @AppStorage("monitorList.tenantFilter") private var tenantFilter = 0
    @AppStorage("monitorList.sort") private var sort = MonitorSort.name
    @AppStorage("monitorList.sortAscending") private var sortAscending = true
    /// Time frame of the uptime and the downtime bar of each row.
    @AppStorage("monitorList.timeframe") private var timeframe = Timeframe.week
    @Environment(\.scenePhase) private var scenePhase
    @Environment(APIClient.self) private var api
    @Environment(AppSettings.self) private var settings

    private static let refreshInterval: Duration = .seconds(30)
    /// The gap between the health groups.
    private static let groupSpacing: CGFloat = 12

    init(api: APIClient) {
        _store = State(initialValue: MonitorsStore(api: api))
    }

    var body: some View {
        List {
            // A header without rows: it sits closer to the toolbar than a first row, and its menus
            // are outside of the row tap handling.
            Section {
            } header: {
                VStack(spacing: 0) {
                    titleRow
                        .padding(.horizontal, 4)
                    if !store.tenants.isEmpty || !store.monitors.isEmpty {
                        filterRow
                    }
                }
                .textCase(nil)
                .listRowInsets(EdgeInsets())
            }
            .listSectionSpacing(Self.groupSpacing)

            if let error = store.errorMessage, !store.monitors.isEmpty {
                Section {
                    Label(error, systemImage: "exclamationmark.triangle.fill")
                        .foregroundStyle(Brand.silent)
                        .font(.subheadline)
                }
            }

            ForEach(MonitorListSection.sections(visibleMonitors, sort: sort, ascending: sortAscending)) { section in
                monitorSection(section)
                    .listSectionSpacing(Self.groupSpacing)
            }
        }
        .listStyle(.insetGrouped)
        .overlay { emptyState }
        // The page shows its own title row with the update time. The navigation title stays for the
        // back button of the detail screen.
        .navigationTitle("Monitors")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(removing: .title)
        .navigationDestination(for: Int.self) { id in
            MonitorDetailView(monitorID: id, monitors: store, api: api)
        }
        .searchable(text: $searchText, prompt: "Name or URL")
        .refreshable { await store.refresh() }
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                sortMenu
                    .tint(.primary)
            }
            ToolbarItem(placement: .topBarTrailing) {
                Button("Notifications", systemImage: "bell") { showsNotifications = true }
                    .tint(.primary)
            }
            ToolbarItem(placement: .topBarTrailing) {
                Button("Settings", systemImage: "gearshape") { showsSettings = true }
                    .tint(.primary)
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
        .onChange(of: sort) { _, newSort in
            // A new sort starts in its usual direction, so "Last downtime" shows the latest first.
            sortAscending = newSort.startsAscending
        }
        .onChange(of: timeframe) { _, newTimeframe in
            store.timeframe = newTimeframe
            Task { await store.refresh() }
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
            store.timeframe = timeframe
            while !Task.isCancelled {
                await store.refresh()
                try? await Task.sleep(for: Self.refreshInterval)
            }
        }
    }

    /// "Monitors" with the time of the last refresh on the same baseline. It scrolls with the list.
    private var titleRow: some View {
        HStack(alignment: .firstTextBaseline, spacing: 12) {
            Text("Monitors")
                .font(.largeTitle.bold())
                // Explicit colors: a section header turns the hierarchical styles grey.
                .foregroundStyle(Color(.label))
                .lineLimit(1)
                .minimumScaleFactor(0.7)
                .layoutPriority(1)
                .accessibilityAddTraits(.isHeader)
            Spacer(minLength: 0)
            if let lastUpdated = store.lastUpdated {
                Text("Updated \(settings.dateFormats.timeWithSeconds(lastUpdated))")
                    .font(.subheadline.monospacedDigit())
                    .foregroundStyle(Color(.secondaryLabel))
                    .lineLimit(1)
            }
        }
        .padding(.bottom, 6)
    }

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

    private func monitorSection(_ section: MonitorListSection) -> some View {
        Section {
            ForEach(section.monitors) { monitor in
                // The link sits invisible behind the row: the row navigates on tap, without the chevron.
                ZStack {
                    NavigationLink(value: monitor.id) { EmptyView() }
                        .opacity(0)
                    MonitorRow(monitor: monitor, isUpdating: store.updatingStateIDs.contains(monitor.id),
                               now: store.lastUpdated ?? .now)
                }
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(monitor.rowAccessibilityLabel(
                    now: store.lastUpdated ?? .now, isUpdating: store.updatingStateIDs.contains(monitor.id)))
                .accessibilityAddTraits(.isButton)
                .swipeActions(edge: .trailing) { swipeActions(for: monitor) }
                .contextMenu { stateMenu(for: monitor) }
            }
        }
    }

    // MARK: - Sorting

    private var sortMenu: some View {
        Menu {
            Picker("Sort By", selection: $sort) {
                ForEach(MonitorSort.allCases, id: \.self) { option in
                    Label(option.title, systemImage: option.symbol).tag(option)
                }
            }
            Picker("Order", selection: $sortAscending) {
                Label("Ascending", systemImage: "arrow.up").tag(true)
                Label("Descending", systemImage: "arrow.down").tag(false)
            }
        } label: {
            Label("Sort", systemImage: "arrow.up.arrow.down")
        }
        .accessibilityValue("\(sort.title), \(sortAscending ? "ascending" : "descending")")
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
        if monitor.state != .inactive {
            Button {
                checkNow(monitor)
            } label: {
                Label(store.checkQueuedIDs.contains(monitor.id) ? "Check Queued" : "Check Now",
                      systemImage: "arrow.clockwise")
            }
            .disabled(store.checkQueuedIDs.contains(monitor.id))
        }
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

    private func checkNow(_ monitor: MonitorSummary) {
        Task {
            do {
                try await store.checkNow(monitor: monitor.id)
            } catch {
                stateChangeError = error.localizedDescription
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

    // MARK: - Tenant and time frame

    /// The tenant menu on the left, the time frame on the right.
    private var filterRow: some View {
        HStack(spacing: 8) {
            if store.tenants.isEmpty {
                Spacer(minLength: 0)
            } else {
                tenantMenu
            }
            if !store.monitors.isEmpty {
                timeframeMenu
            }
        }
        .fixedSize(horizontal: false, vertical: true)
    }

    /// Only the tenant name: a long name stays on one line and truncates.
    private var tenantMenu: some View {
        Menu {
            Picker("Tenant", selection: $tenantFilter) {
                Text("All tenants").tag(0)
                ForEach(store.tenants) { tenant in
                    Text(tenant.name).tag(tenant.id)
                }
            }
        } label: {
            HStack(spacing: 6) {
                Text(selectedTenant?.name ?? "All tenants")
                    .font(.body)
                    .lineLimit(1)
                    .truncationMode(.tail)
                Spacer(minLength: 0)
                Image(systemName: "chevron.up.chevron.down")
                    .font(.footnote.weight(.semibold))
            }
            .menuTile(accent: settings.accentColor)
        }
        .tint(settings.accentColor)
        .accessibilityLabel("Tenant")
        .accessibilityValue(selectedTenant?.name ?? "All tenants")
    }

    /// "7d" with a menu of the three time frames. Its width is that of the longest value, so the
    /// row does not move when the value changes.
    private var timeframeMenu: some View {
        Menu {
            Picker("Uptime of the last", selection: $timeframe) {
                ForEach(Timeframe.allCases) { Text($0.title).tag($0) }
            }
        } label: {
            HStack(spacing: 4) {
                ZStack(alignment: .leading) {
                    ForEach(Timeframe.allCases) { option in
                        Text(option.rawValue).opacity(option == timeframe ? 1 : 0)
                    }
                }
                .font(.subheadline.weight(.semibold).monospacedDigit())
                Image(systemName: "chevron.up.chevron.down")
                    .font(.caption2.weight(.semibold))
            }
            .lineLimit(1)
            .fixedSize()
            .menuTile(accent: settings.accentColor)
        }
        .tint(settings.accentColor)
        .accessibilityLabel("Time frame")
        .accessibilityValue(timeframe.title)
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

private extension View {
    /// A rounded tile of the menu row, at least 44 pt high.
    func menuTile(accent: Color) -> some View {
        foregroundStyle(accent)
            .padding(.horizontal, 14)
            .frame(minHeight: 44)
            .background(Color(.secondarySystemGroupedBackground), in: .rect(cornerRadius: 18))
            .contentShape(.rect(cornerRadius: 18))
    }
}

/// One health group of the monitor list. It has no title: the row icons show the health.
struct MonitorListSection: Identifiable {
    let health: MonitorHealth
    let monitors: [MonitorSummary]

    var id: MonitorHealth { health }

    /// Groups by health in the fixed order down, no data, paused, up, whatever the sort. The sort
    /// orders the rows inside each group. Empty groups are left out.
    static func sections(_ monitors: [MonitorSummary], sort: MonitorSort, ascending: Bool) -> [MonitorListSection] {
        let sorted = monitors.sorted(by: sort, ascending: ascending)
        return MonitorHealth.allCases.compactMap { health in
            let rows = sorted.filter { $0.health == health }
            return rows.isEmpty ? nil : MonitorListSection(health: health, monitors: rows)
        }
    }
}
