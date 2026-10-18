//
//  ContentView.swift
//  StatusTacosAlert
//
//  Created by Oliver Zimpasser on 6/26/25.
//

import SwiftUI

struct ContentView: View {
    @ObservedObject var authService: AuthenticationService
    @ObservedObject var alertsService: AlertsService
    @ObservedObject var settings: AppSettings
    let onClose: () -> Void

    @State private var showsConnectionSettings = false

    var body: some View {
        VStack {
            HStack {
                Spacer()
                Button(action: onClose) {
                    Image(systemName: "xmark.circle.fill")
                        .foregroundColor(.secondary)
                }
                .buttonStyle(PlainButtonStyle())
                .padding(.trailing, 8)
                .padding(.top, 8)
            }

            Spacer()

            if authService.isAuthenticated {
                UserInfoView(userInfo: authService.userInfo, connectionProblem: alertsService.connectionProblem, onLogout: {
                    authService.logout()
                })
            } else if showsConnectionSettings {
                // Tokens are bound to one server and one identity provider,
                // so the connection can only change while signed out.
                ConnectionSettingsView(settings: settings, onDone: { showsConnectionSettings = false })
            } else {
                LoginView(authService: authService, settings: settings, onShowConnectionSettings: {
                    showsConnectionSettings = true
                })
            }

            Spacer()

            Button("Quit Application") {
                NSApplication.shared.terminate(nil)
            }
            .padding(.bottom, 20)
        }
        .padding()
        .frame(width: 400, height: 360)
    }
}

struct LoginView: View {
    @ObservedObject var authService: AuthenticationService
    @ObservedObject var settings: AppSettings
    let onShowConnectionSettings: () -> Void

    var body: some View {
        VStack(spacing: 20) {
            Image(systemName: "person.circle")
                .imageScale(.large)
                .foregroundStyle(.tint)
                .font(.system(size: 48))

            Text("Welcome to Status Tacos Alert")
                .font(.title2)
                .fontWeight(.semibold)

            Text("Sign in with your account to continue")
                .font(.body)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)

            if let errorMessage = authService.errorMessage {
                Text(errorMessage)
                    .foregroundColor(.red)
                    .font(.caption)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal)
            }

            Button(action: {
                authService.login()
            }) {
                HStack {
                    if authService.isLoading {
                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle(tint: .white))
                            .scaleEffect(0.8)
                    } else {
                        Image(systemName: "arrow.right.circle.fill")
                    }
                    Text(authService.isLoading ? "Signing In..." : "Sign In")
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 8)
            }
            .buttonStyle(.borderedProminent)

            if authService.isLoading {
                // The sign-in continues in the browser. A click on the button starts it again.
                Text("Continue in your browser.")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }

            Button(action: onShowConnectionSettings) {
                Label(settings.serverURL.host() ?? settings.serverURL.absoluteString, systemImage: "server.rack")
                    .font(.caption)
            }
            .buttonStyle(.link)
            .help("Change the server")
        }
        .padding()
    }
}

struct UserInfoView: View {
    let userInfo: UserInfo?
    let connectionProblem: String?
    let onLogout: () -> Void

    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: "person.crop.circle.fill")
                .imageScale(.large)
                .foregroundStyle(.green)
                .font(.system(size: 48))

            VStack(spacing: 8) {
                if let name = userInfo?.name {
                    Text(name)
                        .font(.title2)
                        .fontWeight(.semibold)
                }

                if let email = userInfo?.email {
                    Text(email)
                        .font(.body)
                        .foregroundColor(.secondary)
                }

                if let username = userInfo?.preferredUsername {
                    Text("@\(username)")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }

                if let subject = userInfo?.sub {
                    Text("ID: \(subject)")
                        .font(.caption2)
//                        .foregroundColor(.tertiary)
                        .lineLimit(1)
                        .truncationMode(.middle)
                }
            }

            if let connectionProblem {
                Label(connectionProblem, systemImage: "exclamationmark.triangle.fill")
                    .font(.caption)
                    .foregroundColor(.red)
                    .multilineTextAlignment(.center)
            }

            Button("Sign Out") {
                onLogout()
            }
            .buttonStyle(.bordered)
        }
        .padding()
    }
}

struct ConnectionSettingsView: View {
    @ObservedObject var settings: AppSettings
    let onDone: () -> Void

    @State private var serverURL = ""
    @State private var issuerURL = ""
    @State private var clientID = ""
    @State private var validationMessage: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Connection")
                .font(.title2)
                .fontWeight(.semibold)

            Text("Change these settings only if you run your own Status Tacos backend. To use tacos.oglimmer.com, keep the defaults.")
                .font(.caption)
                .foregroundColor(.secondary)
                .fixedSize(horizontal: false, vertical: true)

            Form {
                TextField("Server", text: $serverURL, prompt: Text("https://tacos.oglimmer.com"))
                TextField("Issuer URL", text: $issuerURL)
                TextField("Client ID", text: $clientID)
                LabeledContent("Redirect URI", value: AppSettings.redirectURI)
                    .textSelection(.enabled)
            }

            if let validationMessage {
                Text(validationMessage)
                    .font(.caption)
                    .foregroundColor(.red)
            }

            HStack {
                Button("Reset to Defaults") {
                    load(serverURL: AppSettings.defaultServerURL, issuerURL: AppSettings.defaultIssuerURL,
                         clientID: AppSettings.defaultClientID)
                }
                Spacer()
                Button("Cancel", action: onDone)
                    .keyboardShortcut(.cancelAction)
                Button("Save", action: save)
                    .keyboardShortcut(.defaultAction)
            }
        }
        .onAppear {
            load(serverURL: settings.serverURL, issuerURL: settings.issuerURL, clientID: settings.clientID)
        }
    }

    private func load(serverURL: URL, issuerURL: URL, clientID: String) {
        self.serverURL = serverURL.absoluteString
        self.issuerURL = issuerURL.absoluteString
        self.clientID = clientID
        validationMessage = nil
    }

    private func save() {
        guard let server = AppSettings.normalizedURL(serverURL) else {
            validationMessage = "The server address must start with https:// or http://."
            return
        }
        guard let issuer = AppSettings.normalizedURL(issuerURL) else {
            validationMessage = "The issuer URL must start with https:// or http://."
            return
        }
        guard let client = AppSettings.normalizedClientID(clientID) else {
            validationMessage = "Enter a client ID."
            return
        }
        settings.serverURL = server
        settings.issuerURL = issuer
        settings.clientID = client
        onDone()
    }
}

#Preview {
    ContentView(
        authService: AuthenticationService.shared,
        alertsService: AlertsService(authService: AuthenticationService.shared, settings: AppSettings.shared),
        settings: AppSettings.shared,
        onClose: {}
    )
}
