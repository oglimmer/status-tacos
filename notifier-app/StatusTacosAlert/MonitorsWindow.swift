//
//  MonitorsWindow.swift
//  StatusTacosAlert
//

import AppKit
import Combine
import SwiftUI

/// A normal, resizable window with the monitor list. While it is open the app has a Dock icon and
/// is in the app switcher; after it closes the app is a menu bar app again.
@MainActor
final class MonitorsWindowController: NSWindowController, NSWindowDelegate {
    private let monitorsStore: MonitorsStore
    private var subtitleSubscription: AnyCancellable?

    init(services: AppServices) {
        monitorsStore = services.monitorsStore

        let hostingController = NSHostingController(rootView: MonitorsWindowView(services: services))
        // Only the toolbar comes from SwiftUI. The SwiftUI title does not always reach
        // the window (it then shows "Untitled"), so AppKit sets title and subtitle.
        hostingController.sceneBridgingOptions = [.toolbars]
        hostingController.title = "Monitors"

        let window = NSWindow(contentViewController: hostingController)
        window.styleMask = [.titled, .closable, .miniaturizable, .resizable]
        window.toolbarStyle = .unified
        window.title = "Monitors"
        window.isReleasedWhenClosed = false
        window.setContentSize(NSSize(width: 560, height: 720))
        window.center()
        // Keeps the size and the place of the window between launches.
        window.setFrameAutosaveName("MonitorsWindow")

        super.init(window: window)
        window.delegate = self

        subtitleSubscription = monitorsStore.$lastUpdated
            .receive(on: RunLoop.main)
            .sink { [weak window] lastUpdated in
                window?.subtitle = lastUpdated.map {
                    "Updated \($0.formatted(.dateTime.hour().minute().second()))"
                } ?? ""
            }
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
}
