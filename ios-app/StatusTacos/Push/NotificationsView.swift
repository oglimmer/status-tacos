import SwiftUI

/// Push alerts of the user: this device, and per tenant all monitors or selected monitors.
struct NotificationsView: View {
    let monitors: MonitorsStore
    @Environment(PushStore.self) private var push
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        NavigationStack {
            Form {
                deviceSection

                if push.serverEnabled == false {
                    Section {
                        Label("The server does not send push notifications yet. Ask the admin to set up APNs.",
                              systemImage: "exclamationmark.triangle.fill")
                            .foregroundStyle(.orange)
                    }
                }

                ForEach(monitors.tenants) { tenant in
                    TenantPushSection(tenant: tenant, monitors: monitorsOf(tenant))
                }

                if monitors.tenants.isEmpty {
                    Section {
                        Text("You have no tenants yet.")
                            .foregroundStyle(.secondary)
                    }
                }
            }
            .navigationTitle("Notifications")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
            .overlay {
                if !push.hasLoaded && push.errorMessage == nil {
                    ProgressView()
                }
            }
            .task { await push.load() }
            .onChange(of: scenePhase) { _, phase in
                // The user may have changed the permission in the Settings app.
                if phase == .active { Task { await push.start() } }
            }
            .alert("Notifications", isPresented: Binding(
                get: { push.errorMessage != nil || push.infoMessage != nil },
                set: { if !$0 { push.errorMessage = nil; push.infoMessage = nil } }
            )) {
                Button("OK") {
                    push.errorMessage = nil
                    push.infoMessage = nil
                }
            } message: {
                Text(push.errorMessage ?? push.infoMessage ?? "")
            }
        }
    }

    private func monitorsOf(_ tenant: Tenant) -> [MonitorSummary] {
        monitors.monitors
            .filter { $0.tenant?.id == tenant.id }
            .sorted { $0.name.localizedStandardCompare($1.name) == .orderedAscending }
    }

    @ViewBuilder
    private var deviceSection: some View {
        Section {
            switch push.permission {
            case .allowed:
                Label("Notifications are allowed", systemImage: "checkmark.circle.fill")
                    .foregroundStyle(.green)
                if let error = push.registrationError {
                    Label("This device could not register: \(error)", systemImage: "exclamationmark.triangle.fill")
                        .foregroundStyle(.orange)
                        .font(.footnote)
                }
            case .notDetermined, .unknown:
                Button("Allow Notifications", systemImage: "bell.badge") {
                    Task { await push.requestPermission() }
                }
            case .denied:
                Label("Notifications are off for Status Tacos.", systemImage: "bell.slash")
                    .foregroundStyle(.secondary)
                Button("Open Settings") {
                    if let url = URL(string: UIApplication.openNotificationSettingsURLString) {
                        openURL(url)
                    }
                }
            }
        } header: {
            Text("This device")
        } footer: {
            if let count = push.deviceCount, count > 0 {
                Text(count == 1
                     ? "Push alerts go to 1 device of yours."
                     : "Push alerts go to \(count) devices of yours.")
            } else {
                Text("Push alerts go to every device where you use this app.")
            }
        }
    }
}

/// The push alert of one tenant: on or off, all monitors or selected monitors, and a test.
private struct TenantPushSection: View {
    let tenant: Tenant
    let monitors: [MonitorSummary]
    @Environment(PushStore.self) private var push

    private var alert: PushAlertDTO? { push.alert(forTenant: tenant.id) }
    private var isSaving: Bool { push.savingTenantIDs.contains(tenant.id) }

    var body: some View {
        Section {
            Toggle(isOn: Binding(
                get: { alert?.isActive == true },
                set: { enabled in Task { await push.setEnabled(enabled, forTenant: tenant.id) } }
            )) {
                HStack {
                    Text("Push alerts")
                    if isSaving {
                        ProgressView()
                    }
                }
            }
            .disabled(isSaving)

            if let alert, alert.isActive {
                Picker("Monitors", selection: Binding(
                    get: { alert.allMonitors },
                    set: { all in Task { await setAllMonitors(all, alert: alert) } }
                )) {
                    Text("All monitors").tag(true)
                    Text("Selected monitors").tag(false)
                }
                .disabled(isSaving || (monitors.isEmpty && alert.allMonitors))

                if !alert.allMonitors {
                    ForEach(monitors) { monitor in
                        monitorRow(monitor, alert: alert)
                    }
                }

                Button("Send Test Notification", systemImage: "paperplane") {
                    Task { await push.sendTest(tenantID: tenant.id) }
                }
                .disabled(isSaving)
            }
        } header: {
            Text(tenant.name)
        } footer: {
            if let alert, alert.isActive {
                Text(alert.allMonitors
                     ? "Alerts for every monitor of \(tenant.name), also new ones."
                     : "Alerts only for the selected monitors. Keep at least one.")
            }
        }
    }

    private func monitorRow(_ monitor: MonitorSummary, alert: PushAlertDTO) -> some View {
        let selected = alert.monitorIDs.contains(monitor.id)
        // The backend needs at least one monitor.
        let isLastSelected = selected && alert.monitorIDs.count == 1
        return Button {
            Task {
                var ids = alert.monitorIDs
                if selected { ids.remove(monitor.id) } else { ids.insert(monitor.id) }
                await push.save(
                    PushAlertRequest(isActive: true, allMonitors: false, monitorIds: ids.sorted()),
                    forTenant: tenant.id)
            }
        } label: {
            HStack {
                Image(systemName: monitor.health.symbol)
                    .foregroundStyle(monitor.health.color)
                Text(monitor.name)
                    .foregroundStyle(.primary)
                Spacer()
                if selected {
                    Image(systemName: "checkmark")
                        .fontWeight(.semibold)
                        .foregroundStyle(.tint)
                }
            }
        }
        .disabled(isSaving || isLastSelected)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }

    /// "Selected monitors" starts with every monitor selected, so nothing changes until the user
    /// removes some.
    private func setAllMonitors(_ all: Bool, alert: PushAlertDTO) async {
        let ids = all ? [] : monitors.map(\.id)
        guard all || !ids.isEmpty else { return }
        await push.save(PushAlertRequest(isActive: true, allMonitors: all, monitorIds: ids), forTenant: tenant.id)
    }
}
