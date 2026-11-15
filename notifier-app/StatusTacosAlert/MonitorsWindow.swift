//
//  MonitorsWindow.swift
//  StatusTacosAlert
//

import AppKit
import SwiftUI

/// A normal, resizable window with the monitor list. While it is open the app has a Dock icon and
/// is in the app switcher; after it closes the app is a menu bar app again.
@MainActor
final class MonitorsWindowController: NSWindowController, NSWindowDelegate {
    private let monitorsStore: MonitorsStore

    init(services: AppServices) {
        monitorsStore = services.monitorsStore

        let hostingController = NSHostingController(rootView: MonitorsWindowView(services: services))
        // The SwiftUI title, subtitle and toolbar go to the window.
        hostingController.sceneBridgingOptions = [.title, .toolbars]

        let window = NSWindow(contentViewController: hostingController)
        window.styleMask = [.titled, .closable, .miniaturizable, .resizable]
        window.toolbarStyle = .unified
        window.isReleasedWhenClosed = false
        window.setContentSize(NSSize(width: 560, height: 720))
        window.center()
        // Keeps the size and the place of the window between launches.
        window.setFrameAutosaveName("MonitorsWindow")

        super.init(window: window)
        window.delegate = self
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) is not supported")
    }

    func show() {
        NSApp.setActivationPolicy(.regular)
        showWindow(nil)
        window?.makeKeyAndOrderFront(nil)
        NSApp.activate()
        monitorsStore.setVisible(true, for: .window)
    }

    func windowWillClose(_ notification: Notification) {
        monitorsStore.setVisible(false, for: .window)
        NSApp.setActivationPolicy(.accessory)
    }

    // A window in the Dock is not on screen: no refresh.
    func windowDidMiniaturize(_ notification: Notification) {
        monitorsStore.setVisible(false, for: .window)
    }

    func windowDidDeminiaturize(_ notification: Notification) {
        monitorsStore.setVisible(true, for: .window)
    }
}

struct MonitorsWindowView: View {
    let services: AppServices

    @ObservedObject private var authService: AuthenticationService
    @ObservedObject private var monitorsStore: MonitorsStore

    init(services: AppServices) {
        self.services = services
        authService = services.authService
        monitorsStore = services.monitorsStore
    }

    var body: some View {
        MonitorsScreen(
            authService: authService,
            alertsService: services.alertsService,
            monitorsStore: monitorsStore,
            settings: services.settings,
            header: { EmptyView() }
        )
        .padding(12)
        .frame(minWidth: 420, minHeight: 400)
        .navigationTitle("Monitors")
        .navigationSubtitle(subtitle)
        .toolbar {
            if authService.isAuthenticated {
                ToolbarItem(placement: .primaryAction) {
                    Button("Refresh", systemImage: "arrow.clockwise") {
                        Task { await monitorsStore.refresh() }
                    }
                    .keyboardShortcut("r")
                    .help("Refresh now")
                }
                ToolbarItem(placement: .primaryAction) {
                    AccountMenu(authService: authService, monitorsPageURL: services.settings.monitorsPageURL)
                }
            }
        }
    }

    private var subtitle: String {
        guard let lastUpdated = monitorsStore.lastUpdated else { return "" }
        return "Updated \(lastUpdated.formatted(.dateTime.hour().minute().second()))"
    }
}
