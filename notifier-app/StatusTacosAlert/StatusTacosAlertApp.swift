//
//  StatusTacosAlertApp.swift
//  StatusTacosAlert
//
//  Created by Oliver Zimpasser on 6/26/25.
//

import SwiftUI
import Combine

@main
struct StatusTacosAlertApp: App {
    @NSApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

    var body: some Scene {
        Settings {
            EmptyView()
        }
    }
}

/// The menu bar icon, the popover and the monitors window. The services live in `AppServices`.
@MainActor
class AppDelegate: NSObject, NSApplicationDelegate, NSPopoverDelegate {
    private var statusItem: NSStatusItem!
    private var popover: NSPopover!
    private var services: AppServices!
    /// Made on first use and kept, so the window opens again at the same place.
    private lazy var monitorsWindowController = MonitorsWindowController(services: services)
    private var cancellables = Set<AnyCancellable>()

    func applicationDidFinishLaunching(_ notification: Notification) {
        NSApp.setActivationPolicy(.accessory)
        services = AppServices()
        createStatusItem()
        createPopover()

        services.alertsService.$connectionProblem
            .sink { [weak self] connectionProblem in
                self?.updateStatusIcon(connectionProblem: connectionProblem)
            }
            .store(in: &cancellables)
    }

    func applicationWillTerminate(_ notification: Notification) {
        services.alertsService.markNotRunning()
    }

    /// A click on the Dock icon (while the window is open) shows the window again.
    func applicationShouldHandleReopen(_ sender: NSApplication, hasVisibleWindows flag: Bool) -> Bool {
        if !flag { openMonitorsWindow() }
        return true
    }

    private func createStatusItem() {
        statusItem = NSStatusBar.system.statusItem(withLength: NSStatusItem.squareLength)

        if let button = statusItem.button {
            button.action = #selector(togglePopover)
            button.target = self
        }
        updateStatusIcon(connectionProblem: nil)
    }

    private func updateStatusIcon(connectionProblem: String?) {
        guard let button = statusItem.button else { return }
        if let connectionProblem {
            button.image = NSImage(systemSymbolName: "bell.slash.fill", accessibilityDescription: "Status Tacos Alert: connection problem")
            button.toolTip = "Status Tacos Alert: \(connectionProblem)"
        } else {
            button.image = NSImage(systemSymbolName: "bell.fill", accessibilityDescription: "Status Tacos Alert")
            button.toolTip = "Status Tacos Alert"
        }
    }

    private func createPopover() {
        popover = NSPopover()
        popover.behavior = .transient
        popover.delegate = self
        let popoverView = PopoverView(
            services: services,
            onClose: { [weak self] in self?.popover.performClose(nil) },
            onOpenWindow: { [weak self] in self?.openMonitorsWindow() }
        )
        let hostingController = NSHostingController(rootView: popoverView)
        // The popover takes the size of the view: small for the sign-in, large for the monitors.
        hostingController.sizingOptions = .preferredContentSize
        popover.contentViewController = hostingController
    }

    private func openMonitorsWindow() {
        popover.performClose(nil)
        monitorsWindowController.show()
    }

    func popoverDidClose(_ notification: Notification) {
        services.monitorsStore.setVisible(false, for: .popover)
    }

    @objc private func togglePopover() {
        if popover.isShown {
            popover.performClose(nil)
        } else {
            showPopover()
        }
    }

    private func showPopover() {
        guard let button = statusItem.button else { return }
        // An accessory app must be active, or the text fields in the popover get no keyboard input.
        NSApp.activate()
        popover.show(relativeTo: button.bounds, of: button, preferredEdge: NSRectEdge.minY)
        services.monitorsStore.setVisible(true, for: .popover)
    }

    func application(_ application: NSApplication, open urls: [URL]) {
        for url in urls where url.scheme == AppSettings.redirectScheme {
            if url == MonitorSnapshot.openMonitorsURL {
                // A click on the widget.
                NSWorkspace.shared.open(services.settings.monitorsPageURL)
                break
            }
            services.authService.handleOAuthCallback(url: url)
            if !popover.isShown {
                showPopover()
            }
            break
        }
    }
}
