import SwiftUI

/// Server and sign-in settings. Only shown before sign-in: tokens are bound to one server
/// and one identity provider, so the connection cannot change while signed in.
struct ConnectionSettingsView: View {
    @Environment(AppSettings.self) private var settings
    @Environment(\.dismiss) private var dismiss

    @State private var serverURL = ""
    @State private var issuerURL = ""
    @State private var clientID = ""
    @State private var validationMessage: String?

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Label {
                        VStack(alignment: .leading, spacing: 6) {
                            Text("For advanced users only")
                                .font(.headline)
                            Text("Change these settings only if you run your own Status Tacos backend on a server you own, for example at home or in your home lab. You also need your own OpenID Connect sign-in service.")
                            Text("To use Status Tacos at tacos.oglimmer.com, keep the defaults.")
                        }
                        .font(.subheadline)
                    } icon: {
                        Image(systemName: "exclamationmark.triangle.fill")
                            .foregroundStyle(.orange)
                    }
                    .padding(.vertical, 4)
                }

                Section {
                    TextField("https://tacos.oglimmer.com", text: $serverURL)
                        .keyboardType(.URL)
                        .textContentType(.URL)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                } header: {
                    Text("Server")
                } footer: {
                    Text("Address of the web app. The app reads the API at /api/v1.")
                }

                Section {
                    TextField("Issuer URL", text: $issuerURL)
                        .keyboardType(.URL)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                    TextField("Client ID", text: $clientID)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                    LabeledContent("Redirect URI", value: AppSettings.redirectURI.absoluteString)
                        .font(.footnote)
                        .textSelection(.enabled)
                } header: {
                    Text("Sign-in (OpenID Connect)")
                } footer: {
                    Text("The client must be a public client with PKCE (S256) and allow the redirect URI.")
                }

                Section {
                    Button("Reset to Defaults") {
                        load(serverURL: AppSettings.defaultServerURL, issuerURL: AppSettings.defaultIssuerURL,
                             clientID: AppSettings.defaultClientID)
                    }
                }

                if let validationMessage {
                    Section {
                        Label(validationMessage, systemImage: "exclamationmark.triangle.fill")
                            .foregroundStyle(.red)
                    }
                }
            }
            .navigationTitle("Connection")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") { save() }
                }
            }
            .onAppear {
                load(serverURL: settings.serverURL, issuerURL: settings.issuerURL, clientID: settings.clientID)
            }
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
        dismiss()
    }
}
