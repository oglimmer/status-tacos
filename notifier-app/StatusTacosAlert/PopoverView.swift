//
//  PopoverView.swift
//  StatusTacosAlert
//

import SwiftUI

/// The popover of the menu bar icon: large for the monitors, small for the sign-in.
struct PopoverView: View {
    let services: AppServices
    let onClose: () -> Void
    let onOpenWindow: () -> Void

    @ObservedObject private var authService: AuthenticationService

    init(services: AppServices, onClose: @escaping () -> Void, onOpenWindow: @escaping () -> Void) {
        self.services = services
        self.onClose = onClose
        self.onOpenWindow = onOpenWindow
        authService = services.authService
    }

    var body: some View {
        let isSignedIn = authService.isAuthenticated
        MonitorsScreen(
            authService: authService,
            alertsService: services.alertsService,
            monitorsStore: services.monitorsStore,
            settings: services.settings,
            header: { header }
        )
        .padding(12)
        .overlay(alignment: .topTrailing) {
            if !isSignedIn { closeButton.padding(8) }
        }
        .safeAreaInset(edge: .bottom) {
            if !isSignedIn {
                Button("Quit Application") { NSApplication.shared.terminate(nil) }
                    .padding(.bottom, 20)
            }
        }
        .frame(width: isSignedIn ? 460 : 400, height: isSignedIn ? 620 : 360)
    }

    private var header: some View {
        HStack(alignment: .firstTextBaseline, spacing: 8) {
            Text("Monitors")
                .font(.title2.bold())
            LastUpdatedText(date: services.monitorsStore.lastUpdated)
                .font(.caption)
                .foregroundStyle(.secondary)
            Spacer()
            Button(action: onOpenWindow) {
                Image(systemName: "macwindow")
            }
            .buttonStyle(.borderless)
            .help("Open in a window")
            AccountMenu(authService: authService, monitorsPageURL: services.settings.monitorsPageURL,
                        onOpenWindow: onOpenWindow)
                .menuStyle(.borderlessButton)
            closeButton
        }
    }

    private var closeButton: some View {
        Button(action: onClose) {
            Image(systemName: "xmark.circle.fill")
                .foregroundColor(.secondary)
        }
        .buttonStyle(.plain)
        .help("Close")
    }
}

#Preview {
    PopoverView(services: AppServices(), onClose: {}, onOpenWindow: {})
}
