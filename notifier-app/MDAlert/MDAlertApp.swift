//
//  MDAlertApp.swift
//  MDAlert
//
//  Created by Oliver Zimpasser on 6/26/25.
//

import SwiftUI
import Combine

@main
struct MDAlertApp: App {
    @NSApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

    var body: some Scene {
        Settings {
            EmptyView()
        }
    }
}

class AppDelegate: NSObject, NSApplicationDelegate {
    var statusItem: NSStatusItem!
    var popover: NSPopover!
    var authenticationService: AuthenticationService = AuthenticationService.shared
    var alertsService: AlertsService!

    func applicationDidFinishLaunching(_ notification: Notification) {
        NSApp.setActivationPolicy(.accessory)
        createStatusItem()
        setupAuthenticationService()
        setupAlertsService()
        createPopover()
        observeAuthenticationChanges()
    }


    func setupAuthenticationService() {
        print("🔧 Using AuthenticationService singleton instance \(ObjectIdentifier(authenticationService)) in AppDelegate")
    }

    func setupAlertsService() {
        alertsService = AlertsService()
        print("🔧 Created AlertsService instance")
    }

    func observeAuthenticationChanges() {
        let authService = authenticationService

        NotificationCenter.default.addObserver(
            forName: NSNotification.Name("AuthenticationStateChanged"),
            object: nil,
            queue: .main
        ) { [weak self] _ in
            self?.handleAuthenticationStateChange()
        }

        // Use Combine to observe authentication state changes
        authService.$isAuthenticated
            .sink { [weak self] isAuthenticated in
                print("🔔 Authentication state changed to: \(isAuthenticated)")
                self?.handleAuthenticationStateChange()
            }
            .store(in: &cancellables)
    }

    private var cancellables = Set<AnyCancellable>()

    private func handleAuthenticationStateChange() {
        let authService = authenticationService

        // Add a small delay to ensure the authentication state is fully updated
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
            print("🔍 Authentication state check - isAuthenticated: \(authService.isAuthenticated)")

            if authService.isAuthenticated, let accessToken = authService.getAccessToken() {
                print("🚀 User authenticated, starting alerts polling")
                self.alertsService.startPolling(accessToken: accessToken)
            } else {
                print("🛑 User not authenticated, stopping alerts polling")
                self.alertsService.stopPolling()
            }
        }
    }

    func createStatusItem() {
        statusItem = NSStatusBar.system.statusItem(withLength: NSStatusItem.squareLength)

        if let button = statusItem.button {
            button.image = NSImage(systemSymbolName: "bell.fill", accessibilityDescription: "MDAlert")
            button.action = #selector(togglePopover)
            button.target = self
        }
    }

    func createPopover() {
        popover = NSPopover()
        popover.contentSize = NSSize(width: 400, height: 300)
        popover.behavior = .transient
        // Pass the AppDelegate's AuthenticationService instance to ContentView
        popover.contentViewController = NSHostingController(rootView: ContentView(authService: authenticationService))
    }

    @objc func togglePopover() {
        if let button = statusItem.button {
            if popover.isShown {
                popover.performClose(nil)
            } else {
                popover.show(relativeTo: button.bounds, of: button, preferredEdge: NSRectEdge.minY)
            }
        }
    }

    func application(_ application: NSApplication, open urls: [URL]) {
        print("🔗 AppDelegate received URLs: \(urls)")
        print("🎯 AppDelegate.application(_:open:) called!")

        for url in urls {
            print("🔍 Processing URL: \(url)")
            if url.scheme == "mdalert" {
                print("✅ Found mdalert URL scheme")
                print("🔧 Using AuthenticationService instance: \(ObjectIdentifier(authenticationService))")
                authenticationService.handleOAuthCallback(url: url)

                // Show popover if it's not already shown
                if !popover.isShown {
                    if let button = statusItem.button {
                        popover.show(relativeTo: button.bounds, of: button, preferredEdge: NSRectEdge.minY)
                    }
                }
                break
            } else {
                print("❌ URL scheme \(url.scheme ?? "nil") doesn't match 'mdalert'")
            }
        }
    }
}
