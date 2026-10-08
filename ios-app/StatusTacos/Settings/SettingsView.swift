import SwiftUI

/// Settings while signed in. The connection is read-only: to change it, sign out and use the
/// connection settings of the login screen.
struct SettingsView: View {
    @Environment(AppSettings.self) private var settings
    @Environment(AuthStore.self) private var auth
    @Environment(PushStore.self) private var push
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Form {
                Section("Account") {
                    if let name = auth.account?.name {
                        LabeledContent("Name", value: name)
                    }
                    if let email = auth.account?.email {
                        LabeledContent("Email", value: email)
                    }
                    Button("Sign Out", role: .destructive) {
                        Task {
                            // First, while the session is valid: no more alerts on this device.
                            await push.unregisterDevice()
                            await auth.signOut()
                            dismiss()
                        }
                    }
                }

                Section {
                    LabeledContent("Server", value: settings.serverURL.absoluteString)
                    LabeledContent("Issuer", value: settings.issuerURL.absoluteString)
                    LabeledContent("Client ID", value: settings.clientID)
                } header: {
                    Text("Connection")
                } footer: {
                    Text("To change the connection, sign out.")
                }
                .textSelection(.enabled)

                Section {
                    LabeledContent("Version", value: Self.version)
                }
            }
            .navigationTitle("Settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }

    private static var version: String {
        let info = Bundle.main.infoDictionary
        let version = info?["CFBundleShortVersionString"] as? String ?? "?"
        let build = info?["CFBundleVersion"] as? String ?? "?"
        return "\(version) (\(build))"
    }
}
