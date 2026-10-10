import SwiftUI

@main
struct StatusTacosApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate
    @State private var settings: AppSettings
    @State private var auth: AuthStore
    @State private var api: APIClient
    @State private var push: PushStore
    @State private var router: NotificationRouter

    init() {
        Brand.registerFonts()
        let settings = AppSettings()
        let tokenStore = KeychainTokenStore()
        AuthStore.clearTokensAfterInstall(tokenStore)
        let auth = AuthStore(settings: settings, tokenStore: tokenStore)
        let api = APIClient(settings: settings, auth: auth)
        let push = PushStore(api: api)
        let router = NotificationRouter()
        AppDelegate.push = push
        AppDelegate.router = router
        _settings = State(initialValue: settings)
        _auth = State(initialValue: auth)
        _api = State(initialValue: api)
        _push = State(initialValue: push)
        _router = State(initialValue: router)
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(settings)
                .environment(auth)
                .environment(api)
                .environment(push)
                .environment(router)
        }
    }
}

struct RootView: View {
    @Environment(AuthStore.self) private var auth
    @Environment(APIClient.self) private var api
    @Environment(AppSettings.self) private var settings
    @Environment(PushStore.self) private var push
    @Environment(NotificationRouter.self) private var router
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        @Bindable var router = router
        Group {
            switch auth.phase {
            case .signedIn:
                NavigationStack(path: $router.path) {
                    MonitorListView(api: api)
                }
                // A new server or user gets a new list.
                .id(settings.serverURL)
            case .signedOut, .signingIn:
                LoginView()
            }
        }
        .task(id: auth.phase == .signedIn && scenePhase == .active) {
            // Registers this device for push alerts after sign-in and on every return to the app.
            if auth.phase == .signedIn && scenePhase == .active {
                await push.start()
            }
        }
        .onChange(of: auth.phase) { _, phase in
            if phase == .signedOut {
                push.reset()
                router.path = []
            }
        }
    }
}
