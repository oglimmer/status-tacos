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
                Section {
                    statusRow
                    detailsRow(summary)
                }
                monitoringSection(summary)
                if let tenantID = summary.tenant?.id {
                    pushSection(summary, tenantID: tenantID)
                }
            }

            if let error = store.errorMessage {
                Section {
                    Label(error, systemImage: "exclamationmark.triangle.fill")
                        .foregroundStyle(Brand.silent)
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
                        .tint(.secondary)
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
                HStack(spacing: Self.iconSpacing) {
                    Image(systemName: "bell")
                        .foregroundStyle(.secondary)
                        .frame(width: iconColumnWidth)
                        .accessibilityHidden(true)
                    Text("Notify me")
                        .fontWeight(.medium)
                    if isSaving {
                        ProgressView()
                            .tint(.secondary)
                    }
                }
            }
            .tint(Brand.up)
            .disabled(isSaving || coverage == .allMonitors || push.permission == .denied)
        } header: {
            Text("Push on this iPhone")
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

    /// Status, the environment badge and how long it is up or down. No icon: the word and its color.
    private var statusRow: some View {
        let health = health
        return VStack(alignment: .leading, spacing: 2) {
            HStack(alignment: .center) {
                Text(health.title)
                    .font(.title2.weight(.bold))
                    .foregroundStyle(health.color)
                Spacer(minLength: 8)
                if let environment = summary?.environment {
                    EnvironmentBadge(environment: environment)
                }
            }
            TimelineView(.periodic(from: .now, by: 60)) { context in
                if let line = DetailPresentation.statusLine(health: health, status: store.status, now: context.date) {
                    Text(line)
                        .font(.subheadline)
                        .foregroundStyle(health == .down ? Brand.down : .secondary)
                }
            }
        }
        .padding(.vertical, 2)
        .accessibilityElement(children: .combine)
    }

    /// URL, endpoint and tenant in two columns. Opens the URL in the browser.
    @ViewBuilder
    private func detailsRow(_ summary: MonitorSummary) -> some View {
        if let url = URL(string: summary.url), url.scheme?.hasPrefix("http") == true {
            Link(destination: url) {
                detailsGrid(summary)
            }
            .foregroundStyle(.primary)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(summary.url)
            .accessibilityValue(summary.tenant.map { "Tenant \($0.name)" } ?? "")
            .accessibilityAddTraits(.isLink)
        } else {
            detailsGrid(summary)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(summary.url)
                .accessibilityValue(summary.tenant.map { "Tenant \($0.name)" } ?? "")
        }
    }

    private func detailsGrid(_ summary: MonitorSummary) -> some View {
        let parts = summary.urlParts
        return Grid(alignment: .leadingFirstTextBaseline, horizontalSpacing: 16, verticalSpacing: 8) {
            detailLine("URL", parts.host)
            if let path = parts.path {
                detailLine("Endpoint", path)
            }
            if let tenant = summary.tenant {
                detailLine("Tenant", tenant.name)
            }
        }
        .padding(.vertical, 4)
    }

    private func detailLine(_ label: String, _ value: String) -> some View {
        GridRow {
            Text(label)
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .lineLimit(1)
                .fixedSize()
            Text(value)
                .foregroundStyle(.primary)
                .lineLimit(1)
                .truncationMode(.tail)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    // MARK: - Monitoring state

    /// Width of the dot and bell column, so the titles of both sections line up.
    @ScaledMetric(relativeTo: .body) private var iconColumnWidth: CGFloat = 22
    @ScaledMetric(relativeTo: .body) private var dotSize: CGFloat = 10
    private static let iconSpacing: CGFloat = 12

    /// Pick one of the 3 states. Same function as the state button of the web app.
    private func monitoringSection(_ summary: MonitorSummary) -> some View {
        let isUpdating = monitors.updatingStateIDs.contains(summary.id)
        return Section("Monitoring") {
            ForEach(MonitorState.selectable, id: \.self) { state in
                Button {
                    changeState(to: state)
                } label: {
                    HStack(spacing: Self.iconSpacing) {
                        Circle()
                            .fill(state.color)
                            .frame(width: dotSize, height: dotSize)
                            .frame(width: iconColumnWidth)
                            .accessibilityHidden(true)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(state.title)
                                .fontWeight(.medium)
                                .foregroundStyle(.primary)
                            Text(state.explanation)
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                        }
                        Spacer()
                        if pendingState == state {
                            ProgressView()
                                .tint(.secondary)
                        } else if pendingState == nil, summary.state == state {
                            Image(systemName: "checkmark")
                                .fontWeight(.semibold)
                                .foregroundStyle(.secondary)
                        }
                    }
                    .contentShape(.rect)
                }
                .foregroundStyle(.primary)
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

    // MARK: - Current status

    private var nowSection: some View {
        Section("Current status") {
            if let status = store.status {
                let isDown = status.currentStatus == .down
                if let checked = status.lastCheckedAt {
                    LabeledContent("Last check") {
                        Text(checked, format: .relative(presentation: .named))
                    }
                }
                LabeledContent("Response time", value: responseTimeText(status))
                LabeledContent("Status code") {
                    Text(Format.statusCode(status.lastStatusCode))
                        .foregroundStyle(isDown ? Brand.down : .secondary)
                }
                LabeledContent("Failed checks in a row") {
                    Text(failedChecksText(status))
                        .foregroundStyle(isDown || (status.consecutiveFailures ?? 0) > 0 ? Brand.down : .secondary)
                }
                if isDown {
                    if let lastUp = status.lastUpAt {
                        LabeledContent("Last up") {
                            Text(lastUp, format: .relative(presentation: .named))
                        }
                    }
                } else if let lastDown = status.lastDownAt {
                    LabeledContent("Last down") {
                        Text(lastDown, format: .relative(presentation: .named))
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
            DowntimeBar(
                periods: snapshot.downPeriods, start: snapshot.start, end: snapshot.end,
                checkedFrom: snapshot.checkedFrom, height: 8)
                .padding(.vertical, 6)
            let outages = snapshot.outages
            if outages.isEmpty {
                Text("No outages")
                    .foregroundStyle(.secondary)
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

    @ScaledMetric(relativeTo: .largeTitle) private var uptimeSize: CGFloat = 32

    private func uptimeRow(_ snapshot: PeriodSnapshot) -> some View {
        let downtime = snapshot.totalDowntime
        return HStack(alignment: .firstTextBaseline) {
            VStack(alignment: .leading, spacing: 0) {
                Text("Uptime")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                Text(Format.uptime(snapshot.uptimePercentage))
                    .font(.system(size: uptimeSize, weight: .medium).monospacedDigit())
                    .foregroundStyle(health == .down ? Brand.down : UptimeLevel(snapshot.uptimePercentage).color)
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
            }
            Spacer()
            Text(Format.downtime(downtime))
                .font(.subheadline.monospacedDigit())
                .foregroundStyle(downtime > 0 ? Brand.down : .secondary)
        }
        .padding(.vertical, 2)
        .accessibilityElement(children: .combine)
    }

    private func responseTimeGrid(_ times: PeriodSnapshot.ResponseTimeSummary) -> some View {
        HStack(spacing: 4) {
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
                .font(.callout.weight(.medium).monospacedDigit())
                .lineLimit(1)
                .minimumScaleFactor(0.8)
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
                    .foregroundStyle(.primary)
                if isOngoing {
                    Text("Ongoing")
                        .font(.caption.weight(.bold))
                        .foregroundStyle(Brand.down)
                }
            }
            Spacer()
            Text(Format.durationWords(outage.duration))
                .monospacedDigit()
                .foregroundStyle(Brand.down)
        }
        .accessibilityElement(children: .combine)
    }
}
