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

@MainActor
class AppDelegate: NSObject, NSApplicationDelegate {
    var statusItem: NSStatusItem!
    var popover: NSPopover!
    let settings = AppSettings.shared
    let authenticationService = AuthenticationService.shared
    var alertsService: AlertsService!
    private var cancellables = Set<AnyCancellable>()

    func applicationDidFinishLaunching(_ notification: Notification) {
        NSApp.setActivationPolicy(.accessory)
        createStatusItem()
        alertsService = AlertsService(authService: authenticationService, settings: settings)
        createPopover()
        observeState()
    }

    func applicationWillTerminate(_ notification: Notification) {
        alertsService.markNotRunning()
    }

    func observeState() {
        // @Published sends the new value before the property changes, so use the value from the sink.
        authenticationService.$isAuthenticated
            .removeDuplicates()
            .sink { [weak self] isAuthenticated in
                guard let self else { return }
                print("🔔 Authentication state changed to: \(isAuthenticated)")
                if isAuthenticated {
                    alertsService.startPolling()
                } else {
                    alertsService.stopPolling()
                }
            }
            .store(in: &cancellables)

        alertsService.$connectionProblem
            .sink { [weak self] connectionProblem in
                self?.updateStatusIcon(connectionProblem: connectionProblem)
            }
            .store(in: &cancellables)
    }

    func createStatusItem() {
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

    func createPopover() {
        popover = NSPopover()
        popover.contentSize = NSSize(width: 400, height: 360)
        popover.behavior = .transient
        let contentView = ContentView(
            authService: authenticationService,
            alertsService: alertsService,
            settings: settings,
            onClose: { [weak self] in self?.popover.performClose(nil) }
        )
        popover.contentViewController = NSHostingController(rootView: contentView)
    }

    @objc func togglePopover() {
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
    }

    func application(_ application: NSApplication, open urls: [URL]) {
        for url in urls where url.scheme == AppSettings.redirectScheme {
            if url == MonitorSnapshot.openMonitorsURL {
                // A click on the widget.
                NSWorkspace.shared.open(settings.monitorsPageURL)
                break
            }
            authenticationService.handleOAuthCallback(url: url)
            if !popover.isShown {
                showPopover()
            }
            break
        }
    }
}
