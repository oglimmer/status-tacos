import SwiftUI

struct MonitorDetailView: View {
    let monitorID: Int
    /// The list. Owns the monitor and its state, so a change here shows in the list too.
    let monitors: MonitorsStore
    @State private var store: MonitorDetailStore
    @State private var stateChangeError: String?
    /// The state the user picked, while the server changes it.
    @State private var pendingState: MonitorState?
    @AppStorage("monitorDetail.timeframe") private var timeframe: Timeframe = .day
    @Environment(\.scenePhase) private var scenePhase
    @Environment(PushStore.self) private var push

    private static let refreshInterval: Duration = .seconds(60)
    private static let maxOutages = 20
    private static let windowFormat = Date.FormatStyle.dateTime.day().month().hour().minute()

    init(monitorID: Int, monitors: MonitorsStore, api: APIClient) {
        self.monitorID = monitorID
        self.monitors = monitors
        let status = monitors.monitors.first { $0.id == monitorID }?.status
        _store = State(initialValue: MonitorDetailStore(monitorID: monitorID, status: status, api: api))
    }

    /// Nil when the list has not loaded the monitor (yet).
    private var summary: MonitorSummary? {
        monitors.monitors.first { $0.id == monitorID }
    }

    var body: some View {
        List {
            if let summary {
                Section { header(summary) }
                monitoringSection(summary)
                if let tenantID = summary.tenant?.id {
                    pushSection(summary, tenantID: tenantID)
                }
            }

            if let error = store.errorMessage {
                Section {
                    Label(error, systemImage: "exclamationmark.triangle.fill")
                        .foregroundStyle(.orange)
                        .font(.subheadline)
                }
            }

            if summary?.health != .paused {
                nowSection
            }

            Section {
                Picker("Time frame", selection: $timeframe) {
                    ForEach(Timeframe.allCases) { Text($0.rawValue).tag($0) }
                }
                .pickerStyle(.segmented)
                .listRowBackground(Color.clear)
                .listRowInsets(EdgeInsets())
            }

            if let snapshot = store.snapshots[timeframe] {
                historySections(snapshot)
            } else if !store.hasLoaded {
                Section {
                    ProgressView()
                        .frame(maxWidth: .infinity)
                }
            }
        }
        .listStyle(.insetGrouped)
        .navigationTitle(summary?.name ?? "Monitor")
        .navigationBarTitleDisplayMode(.inline)
        .refreshable { await store.refresh() }
        .alert("Cannot change the monitor", isPresented: Binding(
            get: { stateChangeError != nil },
            set: { if !$0 { stateChangeError = nil } }
        )) {
            Button("OK") { stateChangeError = nil }
        } message: {
            Text(stateChangeError ?? "")
        }
        .task {
            // Opened from a notification before the list has loaded.
            if summary == nil { await monitors.refresh() }
        }
        .task(id: scenePhase) {
            guard scenePhase == .active else { return }
            while !Task.isCancelled {
                await store.refresh()
                try? await Task.sleep(for: Self.refreshInterval)
            }
        }
    }

    // MARK: - Push alerts

    /// Push alerts of this device for this monitor. An alert for all monitors of the tenant is
    /// changed in the notification settings.
    private func pushSection(_ summary: MonitorSummary, tenantID: Int) -> some View {
        let coverage = push.coverage(monitorID: summary.id, tenantID: tenantID)
        let isSaving = push.savingTenantIDs.contains(tenantID)
        return Section {
            Toggle(isOn: Binding(
                get: { coverage.isOn },
                set: { enabled in Task { await push.setMonitor(summary.id, tenantID: tenantID, enabled: enabled) } }
            )) {
                HStack {
                    Label("Notify me", systemImage: "bell.badge")
                    if isSaving {
                        ProgressView()
                    }
                }
            }
            .disabled(isSaving || coverage == .allMonitors || push.permission == .denied)
        } header: {
            Text("Push alerts")
        } footer: {
            switch (push.permission, coverage) {
            case (.denied, _):
                Text("Notifications are off for Status Tacos. Turn them on in the Settings app.")
            case (_, .allMonitors):
                Text("On for every monitor of \(summary.tenant?.name ?? "the tenant"). Change it under Notifications.")
            case (_, .paused):
                Text("Push alerts of \(summary.tenant?.name ?? "the tenant") are paused.")
            default:
                Text("A notification on this device when the monitor goes down or comes back up.")
            }
        }
    }

    // MARK: - Header

    private var health: MonitorHealth {
        guard let summary else { return .pending }
        if summary.health == .paused { return .paused }
        switch store.status?.currentStatus {
        case .down: return .down
        case .up: return .up
        default: return .pending
        }
    }

    private func header(_ summary: MonitorSummary) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 8) {
                Image(systemName: health.symbol)
                Text(health.title)
            }
            .font(.title2.weight(.bold))
            .foregroundStyle(health.color)

            if let url = URL(string: summary.url), url.scheme?.hasPrefix("http") == true {
                Link(summary.url, destination: url)
                    .font(.subheadline)
                    .lineLimit(2)
            } else {
                Text(summary.url)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }

            if let tenant = summary.tenant {
                Label(tenant.name, systemImage: "building.2")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(.vertical, 4)
    }

    // MARK: - Monitoring state

    /// Pick one of the 3 states. Same function as the state button of the web app.
    private func monitoringSection(_ summary: MonitorSummary) -> some View {
        let isUpdating = monitors.updatingStateIDs.contains(summary.id)
        return Section("Monitoring") {
            ForEach(MonitorState.selectable, id: \.self) { state in
                Button {
                    changeState(to: state)
                } label: {
                    HStack(spacing: 12) {
                        Image(systemName: state.symbol)
                            .foregroundStyle(state.color)
                            .frame(width: 24)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(state.title)
                                .foregroundStyle(.primary)
                            Text(state.explanation)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        Spacer()
                        if pendingState == state {
                            ProgressView()
                        } else if pendingState == nil, summary.state == state {
                            Image(systemName: "checkmark")
                                .fontWeight(.semibold)
                                .foregroundStyle(.tint)
                        }
                    }
                }
                .disabled(isUpdating || pendingState != nil)
                .accessibilityAddTraits(summary.state == state ? .isSelected : [])
            }
        }
    }

    private func changeState(to state: MonitorState) {
        guard summary?.state != state, pendingState == nil else { return }
        pendingState = state
        Task {
            do {
                try await monitors.setState(state, forMonitor: monitorID)
                pendingState = nil
                await store.refresh()
            } catch {
                pendingState = nil
                stateChangeError = error.localizedDescription
            }
        }
    }

    // MARK: - Now

    private var nowSection: some View {
        Section("Now") {
            if let status = store.status {
                if let checked = status.lastCheckedAt {
                    LabeledContent("Last check") {
                        Text(checked, format: .relative(presentation: .named))
                    }
                }
                LabeledContent("Response time", value: responseTimeText(status))
                LabeledContent("Status code", value: Format.statusCode(status.lastStatusCode))
                LabeledContent("Failed checks in a row") {
                    Text(failedChecksText(status))
                        .foregroundStyle((status.consecutiveFailures ?? 0) > 0 ? .red : .secondary)
                }
                if let lastDown = status.lastDownAt {
                    LabeledContent("Last down") {
                        Text(lastDown, format: .relative(presentation: .named))
                    }
                }
                if status.currentStatus == .down, let lastUp = status.lastUpAt {
                    LabeledContent("Last up") {
                        Text(lastUp, format: .relative(presentation: .named))
                    }
                }
            } else {
                Text("No check result yet.")
                    .foregroundStyle(.secondary)
            }
        }
    }

    /// A failed check (for example a timeout) has no useful response time.
    private func responseTimeText(_ status: MonitorStatusDTO) -> String {
        if status.currentStatus == .down, (status.lastResponseTimeMs ?? 0) <= 0 { return "–" }
        return Format.responseTime(status.lastResponseTimeMs)
    }

    private func failedChecksText(_ status: MonitorStatusDTO) -> String {
        let failures = status.consecutiveFailures ?? 0
        guard let threshold = summary?.alertingThreshold, summary?.state == .active else { return "\(failures)" }
        return "\(failures) (alert at \(threshold))"
    }

    // MARK: - History

    @ViewBuilder
    private func historySections(_ snapshot: PeriodSnapshot) -> some View {
        Section {
            uptimeRow(snapshot)
            if let times = snapshot.responseTimes {
                responseTimeGrid(times)
            }
        }

        Section("Response time") {
            ResponseTimeChart(snapshot: snapshot)
                .padding(.vertical, 8)
        }

        Section {
            DowntimeBar(periods: snapshot.downPeriods, start: snapshot.start, end: snapshot.end, height: 14)
                .padding(.vertical, 6)
            let outages = snapshot.outages
            if outages.isEmpty {
                Label("No outages", systemImage: "checkmark.seal")
                    .foregroundStyle(.green)
            } else {
                ForEach(outages.prefix(Self.maxOutages), id: \.start) { outage in
                    OutageRow(outage: outage, isOngoing: isOngoing(outage, in: snapshot))
                }
                if outages.count > Self.maxOutages {
                    Text("\(outages.count - Self.maxOutages) older outages are not shown.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
        } header: {
            Text("Outages")
        } footer: {
            Text("\(snapshot.start, format: Self.windowFormat) – \(snapshot.end, format: Self.windowFormat)")
        }
    }

    /// The monitor is down now and the outage reaches the end of the window.
    private func isOngoing(_ outage: DownPeriod, in snapshot: PeriodSnapshot) -> Bool {
        store.status?.currentStatus == .down && snapshot.end.timeIntervalSince(outage.end) < 5 * 60
    }

    private func uptimeRow(_ snapshot: PeriodSnapshot) -> some View {
        HStack(alignment: .firstTextBaseline) {
            VStack(alignment: .leading, spacing: 2) {
                Text("Uptime")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                Text(Format.uptime(snapshot.uptimePercentage))
                    .font(.largeTitle.weight(.bold).monospacedDigit())
                    .foregroundStyle(UptimeLevel(snapshot.uptimePercentage).color)
            }
            Spacer()
            VStack(alignment: .trailing, spacing: 2) {
                if let total = snapshot.totalChecks {
                    Text("\(snapshot.successfulChecks ?? 0) of \(total) checks OK")
                }
                let downtime = snapshot.totalDowntime
                Text(downtime > 0 ? "\(Format.duration(downtime)) down" : "No downtime")
                    .foregroundStyle(downtime > 0 ? .red : .secondary)
            }
            .font(.footnote.monospacedDigit())
        }
    }

    private func responseTimeGrid(_ times: PeriodSnapshot.ResponseTimeSummary) -> some View {
        HStack {
            metric("Avg", times.average)
            metric("P99", times.p99)
            metric("Min", times.minimum)
            metric("Max", times.maximum)
        }
    }

    private func metric(_ title: String, _ milliseconds: Int?) -> some View {
        VStack(spacing: 2) {
            Text(title)
                .font(.caption)
                .foregroundStyle(.secondary)
            Text(Format.responseTime(milliseconds))
                .font(.subheadline.weight(.semibold).monospacedDigit())
        }
        .frame(maxWidth: .infinity)
        .accessibilityElement(children: .combine)
    }
}

private struct OutageRow: View {
    let outage: DownPeriod
    let isOngoing: Bool

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(outage.start, format: .dateTime.weekday().day().month().hour().minute())
                if isOngoing {
                    Text("Ongoing")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(.red)
                }
            }
            Spacer()
            Text(Format.duration(outage.duration))
                .monospacedDigit()
                .foregroundStyle(.red)
        }
        .font(.subheadline)
    }
}
