import Foundation

/// Legal pages of the Status Tacos service. They stay the same when the app uses another server.
enum LegalLinks {
    static let privacyPolicy = URL(string: "https://tacos.oglimmer.com/privacy")!
    static let termsOfService = URL(string: "https://tacos.oglimmer.com/terms")!
    static let imprint = URL(string: "https://tacos.oglimmer.com/imprint")!
}
