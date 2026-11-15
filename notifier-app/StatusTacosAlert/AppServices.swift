//
//  AppServices.swift
//  StatusTacosAlert
//

import Combine
import Foundation

/// The services of the app, made once at launch. The popover and the window get the same objects,
/// so they show the same data and make no extra requests.
@MainActor
final class AppServices {
    let settings: AppSettings
    let authService: AuthenticationService
    let api: APIClient
    let alertsService: AlertsService
    let monitorsStore: MonitorsStore

    private var cancellables = Set<AnyCancellable>()

    init(settings: AppSettings = .shared, authService: AuthenticationService = .shared) {
        self.settings = settings
        self.authService = authService
        api = APIClient(authService: authService, settings: settings)
        alertsService = AlertsService(api: api, settings: settings)
        monitorsStore = MonitorsStore(api: api)

        // @Published sends the new value before the property changes, so use the value from the sink.
        authService.$isAuthenticated
            .removeDuplicates()
            .sink { [weak self] isAuthenticated in
                guard let self else { return }
                if isAuthenticated {
                    alertsService.startPolling()
                } else {
                    alertsService.stopPolling()
                }
                monitorsStore.setSignedIn(isAuthenticated)
            }
            .store(in: &cancellables)
    }
}
