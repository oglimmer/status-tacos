//
//  MonitorListView.swift
//  StatusTacosAlert
//
//  The monitor list of the popover, with the filters of the web app and the iOS app:
//  tenant, environment, status, name search, sort and the time frame of the uptime.
//

import SwiftUI

struct MonitorListView: View {
    @ObservedObject var store: MonitorsStore
    let monitorsPageURL: URL

    @State private var searchText = ""
    /// 0 means all tenants.
    @AppStorage("monitorList.tenantFilter") private var tenantFilter = 0
    @AppStorage("monitorList.environmentFilter") private var environmentFilter = EnvironmentFilter.all
    @AppStorage("monitorList.statusFilter") private var statusFilter = MonitorStatusFilter.all
    @AppStorage("monitorList.sort") private var sort = MonitorSort.status
    @AppStorage("monitorList.sortAscending") private var sortAscending = true
    @AppStorage("monitorList.timeframe") private var timeframe = Timeframe.week

    var body: some View {
        VStack(spacing: 8) {
            filterBar
            statusPicker

            if let error = store.errorMessage, !store.monitors.isEmpty {
                Label(error, systemImage: "exclamationmark.triangle.fill")
                    .font(.caption)
                    .foregroundStyle(Brand.silent)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }

            list
        }
        .onAppear { store.timeframe = timeframe }
        .onChange(of: timeframe) { _, newTimeframe in store.timeframe = newTimeframe }
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
    }

    // MARK: - Filters

    private var filterBar: some View {
        VStack(spacing: 6) {
            HStack(spacing: 6) {
                TextField("Name or URL", text: $searchText)
                    .textFieldStyle(.roundedBorder)
                sortMenu
            }
            HStack(spacing: 6) {
                if !store.tenants.isEmpty {
                    Picker("Tenant", selection: $tenantFilter) {
                        Text("All tenants").tag(0)
                        Divider()
                        ForEach(store.tenants) { tenant in
                            Text(tenant.name).tag(tenant.id)
                        }
                    }
                    .labelsHidden()
                    .help("Tenant")
                }
                if hasEnvironments {
                    Picker("Environment", selection: $environmentFilter) {
                        ForEach(EnvironmentFilter.allCases) { option in
                            Text(option.title).tag(option)
                            if option == .all { Divider() }
                        }
                    }
                    .labelsHidden()
                    .help("Environment")
                }
                Picker("Uptime of the last", selection: $timeframe) {
                    ForEach(Timeframe.allCases) { Text($0.rawValue).tag($0) }
                }
                .labelsHidden()
                .fixedSize()
                .help("Time frame of the uptime")
            }
        }
    }

    private var sortMenu: some View {
        Menu {
            Picker("Sort By", selection: $sort) {
                ForEach(MonitorSort.allCases, id: \.self) { Text($0.title).tag($0) }
            }
            .pickerStyle(.inline)
            Picker("Order", selection: $sortAscending) {
                Text("Ascending").tag(true)
                Text("Descending").tag(false)
            }
            .pickerStyle(.inline)
        } label: {
            Image(systemName: "arrow.up.arrow.down")
        }
        .menuIndicator(.hidden)
        .fixedSize()
        .help("Sort: \(sort.title), \(sortAscending ? "ascending" : "descending")")
    }

    /// All, Down, Up, Paused, each with its count. Down is red when monitors are down.
    private var statusPicker: some View {
        let counts = Dictionary(uniqueKeysWithValues: MonitorStatusFilter.allCases.map { filter in
            (filter, searchedMonitors.filter(filter.matches).count)
        })
        return HStack(spacing: 4) {
            ForEach(MonitorStatusFilter.allCases) { option in
                let count = counts[option] ?? 0
                let isAlert = option == .down && count > 0
                let isSelected = statusFilter == option
                Button {
                    statusFilter = option
                } label: {
                    HStack(spacing: 4) {
                        Text(option.title)
                        Text("\(count)")
                            .monospacedDigit()
                            .fontWeight(isAlert ? .bold : .regular)
                            .foregroundStyle(isAlert ? AnyShapeStyle(Brand.down) : AnyShapeStyle(.secondary))
                    }
                    .font(.callout)
                    .foregroundStyle(isAlert ? AnyShapeStyle(Brand.down) : AnyShapeStyle(.primary))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 4)
                    .background(
                        isSelected ? AnyShapeStyle(.selection) : AnyShapeStyle(.quaternary.opacity(0.5)),
                        in: .rect(cornerRadius: 6))
                    .contentShape(.rect(cornerRadius: 6))
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(isSelected ? .isSelected : [])
            }
        }
    }

    /// The environment filter shows only when a monitor has an environment, or when the filter is on.
    private var hasEnvironments: Bool {
        environmentFilter != .all || store.monitors.contains { $0.environment != nil }
    }

    private var selectedTenant: Tenant? {
        store.tenants.first { $0.id == tenantFilter }
    }

    /// Tenant and environment.
    private var scopedMonitors: [MonitorSummary] {
        store.monitors.filter { monitor in
            (selectedTenant == nil || monitor.tenant?.id == tenantFilter) && environmentFilter.matches(monitor)
        }
    }

    /// Tenant, environment and search. The status filter counts these.
    private var searchedMonitors: [MonitorSummary] {
        let query = searchText.trimmingCharacters(in: .whitespaces)
        guard !query.isEmpty else { return scopedMonitors }
        return scopedMonitors.filter {
            $0.name.localizedCaseInsensitiveContains(query) || $0.url.localizedCaseInsensitiveContains(query)
        }
    }

    private var visibleMonitors: [MonitorSummary] {
        searchedMonitors.filter(statusFilter.matches)
    }

    // MARK: - List

    @ViewBuilder
    private var list: some View {
        let sections = MonitorListSection.sections(visibleMonitors, sort: sort, ascending: sortAscending)
        if sections.isEmpty {
            emptyState
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else {
            List {
                ForEach(sections) { section in
                    Section(section.title) {
                        ForEach(section.monitors) { monitor in
                            MonitorRow(monitor: monitor, timeframe: timeframe, now: store.lastUpdated ?? .now)
                                .contextMenu { contextMenu(for: monitor) }
                        }
                    }
                }
            }
            .listStyle(.inset)
            .scrollContentBackground(.hidden)
        }
    }

    @ViewBuilder
    private func contextMenu(for monitor: MonitorSummary) -> some View {
        if let url = URL(string: monitor.url) {
            Button("Open URL") { NSWorkspace.shared.open(url) }
        }
        Button("Copy URL") {
            NSPasteboard.general.clearContents()
            NSPasteboard.general.setString(monitor.url, forType: .string)
        }
        Divider()
        Button("Open Monitors Page") { NSWorkspace.shared.open(monitorsPageURL) }
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
                }
            } else if store.lastUpdated == nil {
                ProgressView("Loading monitors…")
            } else {
                ContentUnavailableView(
                    "No monitors", systemImage: "waveform.path.ecg",
                    description: Text("Add monitors in the web app."))
            }
        } else if !searchText.trimmingCharacters(in: .whitespaces).isEmpty && searchedMonitors.isEmpty {
            ContentUnavailableView.search(text: searchText)
        } else {
            ContentUnavailableView {
                Label("No matching monitors", systemImage: "line.3.horizontal.decrease.circle")
            } description: {
                Text("No monitor matches the filters.")
            } actions: {
                Button("Show All") {
                    tenantFilter = 0
                    environmentFilter = .all
                    statusFilter = .all
                    searchText = ""
                }
            }
        }
    }
}

/// One monitor: status dot, name, environment, what it does now, and the uptime.
struct MonitorRow: View {
    let monitor: MonitorSummary
    let timeframe: Timeframe
    /// Time of the data: the "down 12 min" counts to it.
    let now: Date

    var body: some View {
        HStack(alignment: .center, spacing: 8) {
            Circle()
                .fill(monitor.health.color)
                .frame(width: 8, height: 8)

            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 4) {
                    Text(monitor.name)
                        .lineLimit(1)
                        .truncationMode(.tail)
                    if monitor.state == .silent {
                        Image(systemName: "bell.slash")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .help("Silent: checked, no alerts")
                    }
                }
                HStack(spacing: 4) {
                    if let environment = monitor.environment {
                        EnvironmentBadge(environment: environment)
                    }
                    if let tenant = monitor.tenant {
                        Text(tenant.name)
                            .foregroundStyle(.tertiary)
                            .lineLimit(1)
                        Text("·").foregroundStyle(.tertiary)
                    }
                    Text(monitor.detail(now: now))
                        .monospacedDigit()
                        .foregroundStyle(monitor.health == .down ? AnyShapeStyle(Brand.down) : AnyShapeStyle(.secondary))
                        .lineLimit(1)
                        .truncationMode(.tail)
                }
                .font(.caption)
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            if monitor.health != .paused {
                Text(Format.uptime(monitor.uptimePercentage))
                    .font(.callout.weight(.semibold).monospacedDigit())
                    .foregroundStyle(monitor.health == .down ? Brand.down : Format.uptimeColor(monitor.uptimePercentage))
                    .help("Uptime in the last \(timeframe.title)")
            }
        }
        .padding(.vertical, 2)
        .help(monitor.url)
    }
}

/// Small tinted badge: "PROD", "STAGE" or "DEV".
struct EnvironmentBadge: View {
    let environment: MonitorEnvironment

    private var color: Color {
        switch environment {
        case .prod: return Brand.environmentProd
        case .stage: return Brand.environmentStage
        case .dev: return Brand.environmentDev
        }
    }

    var body: some View {
        Text(environment.rawValue)
            .font(.caption2.weight(.bold))
            .tracking(0.5)
            .foregroundStyle(color)
            .lineLimit(1)
            .fixedSize()
            .padding(.horizontal, 4)
            .padding(.vertical, 1)
            .background(color.opacity(0.14), in: .rect(cornerRadius: 3))
            .accessibilityLabel(environment.title)
    }
}
