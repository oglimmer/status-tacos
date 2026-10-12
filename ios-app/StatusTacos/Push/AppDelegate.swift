import Observation
import UIKit
import UserNotifications

/// Opens a monitor when the user taps one of its notifications.
@MainActor
@Observable
final class NotificationRouter {
    /// The navigation path of the monitor list: monitor IDs.
    var path: [Int] = []

    func open(monitorID: Int) {
        path = [monitorID]
    }
}

/// Gets the APNs token and the taps on notifications from iOS.
final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    /// Set by the app at start.
    static var push: PushStore?
    static var router: NotificationRouter?

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        return true
    }

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        Task { await Self.push?.didRegister(deviceToken: deviceToken) }
    }

    func application(_ application: UIApplication, didFailToRegisterForRemoteNotificationsWithError error: Error) {
        Self.push?.didFailToRegister(error)
    }

    /// Also show notifications while the app is open.
    nonisolated func userNotificationCenter(
        _ center: UNUserNotificationCenter, willPresent notification: UNNotification
    ) async -> UNNotificationPresentationOptions {
        [.banner, .list, .sound]
    }

    /// Not the async variant: it calls the completion handler off the main thread, and UIKit then
    /// stops the app with an assertion.
    nonisolated func userNotificationCenter(
        _ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping @Sendable () -> Void
    ) {
        let monitorID = PushPayload.monitorID(from: response.notification.request.content.userInfo)
        DispatchQueue.main.async {
            if let monitorID {
                Self.router?.open(monitorID: monitorID)
            }
            completionHandler()
        }
    }
}
