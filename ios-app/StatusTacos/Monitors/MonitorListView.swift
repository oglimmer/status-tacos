import SwiftUI

struct MonitorListView: View {
    @State private var store: MonitorsStore
    @State private var searchText = ""
    @State private var showsSettings = false
    @State private var showsNotifications = false
    @State private var stateChangeError: String?
    /// 0 means all tenants.
    @AppStorage("monitorList.tenantFilter") private var tenantFilter = 0
    @AppStorage("monitorList.sort") private var sort = MonitorSort.status
    @AppStorage("monitorList.sortAscending") private var sortAscending = true
    @Environment(\.scenePhase) private var scenePhase
    @Environment(APIClient.self) private var api
    @Environment(AppSettings.self) private var settings

    private static let refreshInterval: Duration = .seconds(30)

    init(api: APIClient) {
        _store = State(initialValue: MonitorsStore(api: api))
    }

    var body: some View {
        List {
            // A header without rows: it sits closer to the toolbar than a first row.
            Section {
            } header: {
                titleRow
                    .textCase(nil)
                    .listRowInsets(EdgeInsets(top: 0, leading: 4, bottom: 0, trailing: 4))
            }
            .listSectionSpacing(0)

            if !store.tenants.isEmpty {
                Section {
                    tenantPicker
                }
                .listSectionSpacing(12)
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
                        .foregroundStyle(Brand.silent)
                        .font(.subheadline)
                }
            }

            ForEach(MonitorListSection.sections(
                visibleMonitors, sort: sort, ascending: sortAscending, tenantName: selectedTenant?.name)
            ) { section in
                monitorSection(section)
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
                Text("Updated \(lastUpdated, format: .dateTime.hour().minute().second())")
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
        } header: {
            Text(section.title)
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

    /// A menu with its own label, so a long tenant name stays on one line and truncates
    /// instead of wrapping and squeezing the "Tenant" title.
    private var tenantPicker: some View {
        Menu {
            Picker("Tenant", selection: $tenantFilter) {
                Text("All tenants").tag(0)
                ForEach(store.tenants) { tenant in
                    Text(tenant.name).tag(tenant.id)
                }
            }
        } label: {
            HStack(spacing: 8) {
                Label("Tenant", systemImage: "building.2")
                    .layoutPriority(1)
                Spacer(minLength: 12)
                Text(selectedTenant?.name ?? "All tenants")
                    .lineLimit(1)
                    .truncationMode(.tail)
                Image(systemName: "chevron.up.chevron.down")
                    .font(.footnote.weight(.semibold))
            }
            .foregroundStyle(settings.accentColor)
        }
        .tint(settings.accentColor)
        .accessibilityLabel("Tenant")
        .accessibilityValue(selectedTenant?.name ?? "All tenants")
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

/// Counts per health, with the most important first. Number and word on one line.
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
        let style = MonitorCounterStyle(health: health, count: count)
        return ViewThatFits(in: .horizontal) {
            HStack(alignment: .firstTextBaseline, spacing: 6) {
                number(count, style)
                word(health, style)
            }
            VStack(alignment: .leading, spacing: 0) {
                number(count, style)
                word(health, style)
            }
        }
        .lineLimit(1)
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 14)
        .padding(.vertical, 8)
        .background(Color(.secondarySystemGroupedBackground), in: .rect(cornerRadius: 18))
        .accessibilityElement(children: .combine)
    }

    private func number(_ count: Int, _ style: MonitorCounterStyle) -> some View {
        Text("\(count)")
            .font(.title.weight(.medium).monospacedDigit())
            .foregroundStyle(style.numberColor)
    }

    private func word(_ health: MonitorHealth, _ style: MonitorCounterStyle) -> some View {
        Text(health.title)
            .font(.subheadline)
            .foregroundStyle(style.wordColor)
    }
}

/// Colors of a counter tile: only the down count is red, and only when there is one.
struct MonitorCounterStyle {
    enum Tone: Equatable { case down, up, neutral }

    let number: Tone
    let word: Tone

    init(health: MonitorHealth, count: Int) {
        switch health {
        case .down where count > 0:
            number = .down
            word = .down
        case .up:
            number = .up
            word = .neutral
        default:
            number = .neutral
            word = .neutral
        }
    }

    var numberColor: AnyShapeStyle { Self.style(number) }
    var wordColor: AnyShapeStyle { Self.style(word) }

    private static func style(_ tone: Tone) -> AnyShapeStyle {
        switch tone {
        case .down: AnyShapeStyle(Brand.down)
        case .up: AnyShapeStyle(Brand.up)
        case .neutral: AnyShapeStyle(.secondary)
        }
    }
}

/// One group of the monitor list.
struct MonitorListSection: Identifiable {
    let id: String
    let title: String
    let monitors: [MonitorSummary]

    /// The status sort groups by health: down, no data, up, paused (reversed when descending). With
    /// a tenant filter the up group carries the tenant name. Other sorts: one group with all monitors.
    static func sections(
        _ monitors: [MonitorSummary], sort: MonitorSort, ascending: Bool, tenantName: String?
    ) -> [MonitorListSection] {
        let sorted = monitors.sorted(by: sort, ascending: ascending)
        guard sort == .status else {
            return sorted.isEmpty ? [] : [MonitorListSection(
                id: "all", title: "All monitors (\(sorted.count))", monitors: sorted)]
        }
        let healths = ascending ? MonitorHealth.allCases : MonitorHealth.allCases.reversed()
        return healths.compactMap { health in
            let rows = sorted.filter { $0.health == health }
            guard !rows.isEmpty else { return nil }
            let name = health == .up ? (tenantName ?? health.title) : health.title
            return MonitorListSection(id: "\(health)", title: "\(name) (\(rows.count))", monitors: rows)
        }
    }
}
