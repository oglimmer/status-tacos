import AuthenticationServices
import SwiftUI

struct LoginView: View {
    @Environment(AuthStore.self) private var auth
    @Environment(AppSettings.self) private var settings
    @Environment(\.webAuthenticationSession) private var webAuthenticationSession
    @State private var showsConnection = false

    var body: some View {
        VStack(spacing: 24) {
            Spacer()

            Image(.logo)
                .resizable()
                .frame(width: 120, height: 120)
                .clipShape(.rect(cornerRadius: 27))
                .accessibilityHidden(true)
            VStack(spacing: 8) {
                Text("Status Tacos")
                    .font(.largeTitle.weight(.bold))
                Text("See if your services are up.")
                    .foregroundStyle(.secondary)
            }

            Spacer()

            if let notice = auth.noticeMessage {
                Label(notice, systemImage: "checkmark.circle.fill")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            }

            if let error = auth.errorMessage {
                Label(error, systemImage: "exclamationmark.triangle.fill")
                    .font(.subheadline)
                    .foregroundStyle(.red)
                    .multilineTextAlignment(.center)
            }

            Button {
                Task { await signIn() }
            } label: {
                Group {
                    if auth.phase == .signingIn {
                        ProgressView()
                    } else {
                        Text("Sign In")
                    }
                }
                .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)
            .disabled(auth.phase == .signingIn)

            Button {
                showsConnection = true
            } label: {
                Label(settings.serverURL.host() ?? settings.serverURL.absoluteString, systemImage: "server.rack")
                    .font(.footnote)
            }
            .disabled(auth.phase == .signingIn)
            .accessibilityLabel("Connection settings")
            .accessibilityValue(settings.serverURL.absoluteString)

            HStack(spacing: 16) {
                Link("Privacy Policy", destination: LegalLinks.privacyPolicy)
                Link("Terms of Service", destination: LegalLinks.termsOfService)
            }
            .font(.footnote)
            .foregroundStyle(.secondary)
        }
        .padding(24)
        .frame(maxWidth: 480)
        .sheet(isPresented: $showsConnection) { ConnectionSettingsView() }
    }

    private func signIn() async {
        await auth.signIn { url, callbackScheme in
            try await webAuthenticationSession.authenticate(using: url, callbackURLScheme: callbackScheme)
        }
    }
}
