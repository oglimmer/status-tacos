//
//  MonitorsScreen.swift
//  StatusTacosAlert
//
//  The parts that the popover and the monitors window share.
//

import SwiftUI

/// Signed in: the monitor list. Signed out: the sign-in. `header` sits above the list; the popover
/// puts its title row there, the window uses its title bar and toolbar instead.
struct MonitorsScreen<Header: View>: View {
    @ObservedObject var authService: AuthenticationService
    @ObservedObject var alertsService: AlertsService
    @ObservedObject var monitorsStore: MonitorsStore
    @ObservedObject var settings: AppSettings
    @ViewBuilder let header: () -> Header

    var body: some View {
        if authService.isAuthenticated {
            VStack(spacing: 8) {
                header()
                if let connectionProblem = alertsService.connectionProblem {
                    ConnectionProblemBanner(message: connectionProblem)
                }
                MonitorListView(store: monitorsStore, monitorsPageURL: settings.monitorsPageURL)
            }
        } else {
            SignInView(authService: authService, settings: settings)
        }
    }
}

struct ConnectionProblemBanner: View {
    let message: String

    var body: some View {
        Label(message, systemImage: "exclamationmark.triangle.fill")
            .font(.caption)
            .foregroundStyle(Brand.down)
            .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// "Updated 14:03:12", or nothing before the first load.
struct LastUpdatedText: View {
    let date: Date?

    var body: some View {
        if let date {
            Text("Updated \(date, format: .dateTime.hour().minute().second())")
                .monospacedDigit()
        }
    }
}

/// The user, the web app, the window, sign-out and quit.
struct AccountMenu: View {
    @ObservedObject var authService: AuthenticationService
    let monitorsPageURL: URL
    /// Nil hides the item, for example in the window itself.
    var onOpenWindow: (() -> Void)?

    var body: some View {
        Menu {
            if let name = authService.userInfo?.name {
                Text(name)
            }
            if let email = authService.userInfo?.email {
                Text(email)
            }
            Divider()
            if let onOpenWindow {
                Button("Open Monitors Window", action: onOpenWindow)
            }
            Button("Open Web App") { NSWorkspace.shared.open(monitorsPageURL) }
            Button("Sign Out") { authService.logout() }
            Divider()
            Button("Quit Status Tacos Alert") { NSApplication.shared.terminate(nil) }
        } label: {
            Image(systemName: "person.crop.circle")
        }
        .menuIndicator(.hidden)
        .fixedSize()
        .help("Account")
    }
}
