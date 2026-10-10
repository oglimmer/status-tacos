import AuthenticationServices
import SwiftUI

/// The first screen: the look of the website, then sign-in.
struct LoginView: View {
    @Environment(AuthStore.self) private var auth
    @Environment(AppSettings.self) private var settings
    @Environment(\.webAuthenticationSession) private var webAuthenticationSession
    @State private var showsConnection = false

    /// How far the strip reaches up into the teal band.
    private static let overlap: CGFloat = 40
    private static let tacoWidth: CGFloat = 130

    var body: some View {
        GeometryReader { geometry in
            ScrollView {
                VStack(spacing: 0) {
                    band(topInset: geometry.safeAreaInsets.top)
                    strip
                    Spacer(minLength: 24)
                    signIn
                }
                .frame(minHeight: geometry.size.height + geometry.safeAreaInsets.top)
            }
            .scrollBounceBehavior(.basedOnSize)
            .modifier(HiddenTopScrollEdge())
            .ignoresSafeArea(edges: .top)
        }
        .background(Brand.background)
        .sheet(isPresented: $showsConnection) { ConnectionSettingsView() }
    }

    /// The teal band of the logo, with the headline painted on it like a shop sign.
    private func band(topInset: CGFloat) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 10) {
                Image(.logo)
                    .resizable()
                    .frame(width: 36, height: 36)
                    .clipShape(.rect(cornerRadius: 9))
                    .overlay(RoundedRectangle(cornerRadius: 9).strokeBorder(Brand.inkOnColor, lineWidth: 2))
                    .accessibilityHidden(true)
                Text("Status Tacos")
                    .font(Brand.sign(16, relativeTo: .headline))
            }
            .accessibilityElement(children: .combine)

            Text("Is it up?")
                .font(Brand.sign(58, relativeTo: .largeTitle))
                .shadow(color: Brand.masa, radius: 0, x: 3.5, y: 3.5)
                .minimumScaleFactor(0.6)
                .lineLimit(2)
                .padding(.top, 32)
                .accessibilityAddTraits(.isHeader)

            Text("Status Tacos checks your URLs every 15 seconds. This app shows what is down and sends you a push alert when it happens.")
                .font(.body)
                .padding(.top, 14)
                .fixedSize(horizontal: false, vertical: true)
        }
        .foregroundStyle(Brand.inkOnColor)
        .frame(maxWidth: 520, alignment: .leading)
        .padding(.horizontal, 20)
        .padding(.top, topInset + 12)
        // Room for the taco, which stands on the strip, and for the strip itself.
        .padding(.bottom, Self.overlap + Self.tacoWidth * 0.69 + 12)
        .frame(maxWidth: .infinity)
        .background(Brand.teal)
    }

    private var strip: some View {
        CheckStrip()
            .overlay(alignment: .topTrailing) {
                Image(.taco)
                    .resizable()
                    .scaledToFit()
                    .frame(width: Self.tacoWidth)
                    .alignmentGuide(.top) { $0[.bottom] - 2 }
                    .padding(.trailing, 20)
                    .accessibilityHidden(true)
            }
            .frame(maxWidth: 520)
            .padding(.horizontal, 20)
            .padding(.top, -Self.overlap)
    }

    private var signIn: some View {
        VStack(spacing: 14) {
            if let notice = auth.noticeMessage {
                message(notice, systemImage: "checkmark.circle.fill", color: Brand.ink)
            }

            if let error = auth.errorMessage {
                message(error, systemImage: "exclamationmark.triangle.fill", color: Brand.down)
            }

            Button {
                Task { await signIn() }
            } label: {
                Group {
                    if auth.phase == .signingIn {
                        ProgressView().tint(Brand.inkOnColor)
                    } else {
                        Text("Sign in")
                    }
                }
                .frame(maxWidth: .infinity)
            }
            .buttonStyle(MasaButtonStyle())
            .disabled(auth.phase == .signingIn)

            Text("New here? Signing in creates your account.")
                .font(.footnote)
                .foregroundStyle(Brand.inkSoft)

            Button {
                showsConnection = true
            } label: {
                Label(settings.serverURL.host() ?? settings.serverURL.absoluteString, systemImage: "server.rack")
                    .font(.footnote)
                    .foregroundStyle(Brand.inkSoft)
            }
            .disabled(auth.phase == .signingIn)
            .accessibilityLabel("Connection settings")
            .accessibilityValue(settings.serverURL.absoluteString)
            .padding(.top, 6)

            HStack(spacing: 18) {
                Link("Privacy", destination: LegalLinks.privacyPolicy)
                Link("Terms", destination: LegalLinks.termsOfService)
                Link("Imprint", destination: LegalLinks.imprint)
            }
            .font(.footnote)
            .foregroundStyle(Brand.ink)
            .underline()
        }
        .frame(maxWidth: 520)
        .padding(.horizontal, 20)
        .padding(.bottom, 12)
    }

    private func message(_ text: String, systemImage: String, color: Color) -> some View {
        Label(text, systemImage: systemImage)
            .font(.subheadline)
            .foregroundStyle(color)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(12)
            .background(Brand.paper, in: .rect(cornerRadius: 10))
            .overlay(RoundedRectangle(cornerRadius: 10).strokeBorder(color, lineWidth: 2))
    }

    private func signIn() async {
        await auth.signIn { url, callbackScheme in
            try await webAuthenticationSession.authenticate(using: url, callbackURLScheme: callbackScheme)
        }
    }
}

/// The teal band runs under the status bar. The scroll edge effect of iOS 26 would darken it there.
private struct HiddenTopScrollEdge: ViewModifier {
    func body(content: Content) -> some View {
        if #available(iOS 26, *) {
            content.scrollEdgeEffectHidden(true, for: .top)
        } else {
            content
        }
    }
}
