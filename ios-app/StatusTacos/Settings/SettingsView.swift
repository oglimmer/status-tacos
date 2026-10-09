import SwiftUI

/// Settings while signed in. The connection is read-only: to change it, sign out and use the
/// connection settings of the login screen.
struct SettingsView: View {
    @Environment(AppSettings.self) private var settings
    @Environment(AuthStore.self) private var auth
    @Environment(PushStore.self) private var push
    @Environment(APIClient.self) private var api
    @Environment(\.dismiss) private var dismiss
    @State private var showsDeleteConfirmation = false
    @State private var isDeleting = false
    @State private var deleteError: String?

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
                    .disabled(isDeleting)
                }

                Section {
                    Button(role: .destructive) {
                        showsDeleteConfirmation = true
                    } label: {
                        HStack {
                            Text("Delete Account")
                            if isDeleting {
                                Spacer()
                                ProgressView()
                            }
                        }
                    }
                    .disabled(isDeleting)
                } footer: {
                    Text("Deletes your login account and every tenant only you belong to, with its monitors and alert contacts.")
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

                Section("Legal") {
                    Link("Privacy Policy", destination: LegalLinks.privacyPolicy)
                    Link("Terms of Service", destination: LegalLinks.termsOfService)
                    Link("Imprint & Support", destination: LegalLinks.imprint)
                }

                Section {
                    LabeledContent("Version", value: Self.version)
                }
            }
            .navigationTitle("Settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                        .disabled(isDeleting)
                }
            }
            .alert("Delete your account?", isPresented: $showsDeleteConfirmation) {
                Button("Delete Account", role: .destructive) {
                    Task { await deleteAccount() }
                }
                Button("Cancel", role: .cancel) {}
            } message: {
                Text("This deletes your login account and every tenant only you belong to, with its monitors, check history and alert contacts. Shared tenants stay with their other members. You cannot undo this.")
            }
            .alert(
                "Account Not Deleted",
                isPresented: Binding(get: { deleteError != nil }, set: { if !$0 { deleteError = nil } })
            ) {
                Button("OK", role: .cancel) {}
            } message: {
                Text(deleteError ?? "")
            }
        }
        .interactiveDismissDisabled(isDeleting)
    }

    private func deleteAccount() async {
        isDeleting = true
        defer { isDeleting = false }
        do {
            let result = try await api.deleteAccount()
            // The server deleted the push devices too.
            push.reset()
            auth.accountWasDeleted(loginAccountDeleted: result.loginAccountDeleted)
            dismiss()
        } catch {
            deleteError = "Nothing was deleted. Please try again later. (\(error.localizedDescription))"
        }
    }

    private static var version: String {
        let info = Bundle.main.infoDictionary
        let version = info?["CFBundleShortVersionString"] as? String ?? "?"
        let build = info?["CFBundleVersion"] as? String ?? "?"
        return "\(version) (\(build))"
    }
}
