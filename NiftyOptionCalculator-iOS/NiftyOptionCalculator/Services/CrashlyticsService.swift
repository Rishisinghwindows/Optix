import Foundation
import FirebaseCrashlytics

/// Lightweight wrapper around Firebase Crashlytics for consistent error reporting
enum CrashlyticsService {

    /// Set the current user identifier for crash reports
    static func setUser(_ userId: String?) {
        Crashlytics.crashlytics().setUserID(userId ?? "")
    }

    /// Log a non-fatal error with optional context
    static func record(error: Error, context: [String: Any]? = nil) {
        let crashlytics = Crashlytics.crashlytics()
        if let context = context {
            for (key, value) in context {
                crashlytics.setCustomValue(value, forKey: key)
            }
        }
        crashlytics.record(error: error)
    }

    /// Log a message (appears in crash report breadcrumbs)
    static func log(_ message: String) {
        Crashlytics.crashlytics().log(message)
    }

    /// Set a custom key-value pair for crash context
    static func setCustomValue(_ value: Any, forKey key: String) {
        Crashlytics.crashlytics().setCustomValue(value, forKey: key)
    }
}
