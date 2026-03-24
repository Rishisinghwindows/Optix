// AnalyticsService.swift
// Centralized analytics layer wrapping Firebase Analytics.
// All events are enriched with platform, app version, and build number so
// dashboards can segment by OS and release without per-call boilerplate.

import Foundation
import FirebaseAnalytics

/// Lightweight wrapper around Firebase Analytics for consistent event tracking.
///
/// Usage: call static methods directly — `AnalyticsService.logEvent(...)`.
/// Every event automatically includes `platform`, `app_version`, and `build_number`.
enum AnalyticsService {

    /// Base parameters merged into every event for cross-platform segmentation
    private static let platformParams: [String: Any] = [
        "platform": "ios",
        "app_version": Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "unknown",
        "build_number": Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "unknown"
    ]

    // MARK: - Core Methods

    /// Log a custom event with optional parameters — merges caller params over platform defaults
    static func logEvent(_ name: String, parameters: [String: Any]? = nil) {
        var merged = platformParams
        // Caller-supplied values win on key collision
        if let parameters { merged.merge(parameters) { _, new in new } }
        Analytics.logEvent(name, parameters: merged)
    }

    /// Log a screen view event
    static func logScreenView(screenName: String, screenClass: String? = nil) {
        var params = platformParams
        params[AnalyticsParameterScreenName] = screenName
        params[AnalyticsParameterScreenClass] = screenClass ?? screenName
        Analytics.logEvent(AnalyticsEventScreenView, parameters: params)
    }

    /// Set the user ID for all subsequent events.
    /// Also stamps persistent user properties (platform, app_version) so they
    /// appear on every future event without explicit inclusion.
    static func setUserID(_ id: String?) {
        Analytics.setUserID(id)
        Analytics.setUserProperty("ios", forName: "platform")
        if let version = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String {
            Analytics.setUserProperty(version, forName: "app_version")
        }
    }

    /// Set a user property for segmentation
    static func setUserProperty(_ value: String?, forName name: String) {
        Analytics.setUserProperty(value, forName: name)
    }

    // MARK: - Convenience Methods
    // These map domain actions to Firebase's predefined + custom event names.
    // Predefined events (AnalyticsEventLogin, etc.) unlock built-in Firebase reports.

    /// Log a login event (uses Firebase's built-in login event for funnel reports)
    static func logLogin(method: String) {
        logEvent(AnalyticsEventLogin, parameters: [
            AnalyticsParameterMethod: method
        ])
    }

    /// Log a sign-up event
    static func logSignUp(method: String) {
        logEvent(AnalyticsEventSignUp, parameters: [
            AnalyticsParameterMethod: method
        ])
    }

    /// Log when user views an option chain for a specific index
    static func logOptionChainView(index: String) {
        logEvent("option_chain_view", parameters: [
            "index": index
        ])
    }

    /// Log when user views AI insights
    static func logAIInsightView() {
        logEvent("ai_insight_view")
    }

    /// Log a paper trading action
    static func logPaperTrade(action: String, symbol: String) {
        logEvent("paper_trade", parameters: [
            "action": action,
            "symbol": symbol
        ])
    }

    /// Log calculator usage
    static func logCalculatorUse(type: String) {
        logEvent("calculator_use", parameters: [
            "type": type
        ])
    }

    /// Log generic feature usage
    static func logFeatureUsed(feature: String) {
        logEvent("feature_used", parameters: [
            "feature": feature
        ])
    }
}
