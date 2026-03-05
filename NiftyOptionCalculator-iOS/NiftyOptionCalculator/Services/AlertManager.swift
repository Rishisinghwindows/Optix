import Foundation
import UIKit
import Combine
import UserNotifications

// MARK: - Alert Manager

@MainActor
final class AlertManager: ObservableObject {
    static let shared = AlertManager()

    // MARK: - Published State

    /// Recent alerts (max 50, newest first)
    @Published var activeAlerts: [SmartAlert] = []

    /// Currently displayed banner alert
    @Published var currentBanner: SmartAlert?

    // MARK: - Combine

    /// Stream for subscribers who want real-time alert events
    let alertSubject = PassthroughSubject<SmartAlert, Never>()

    // MARK: - Preferences (UserDefaults-backed)

    @Published var alertsEnabled: Bool {
        didSet { UserDefaults.standard.set(alertsEnabled, forKey: "alertsEnabled") }
    }

    @Published var alertSoundEnabled: Bool {
        didSet { UserDefaults.standard.set(alertSoundEnabled, forKey: "alertSoundEnabled") }
    }

    // MARK: - Private State

    /// Cooldown tracking: [alertType.rawValue: lastFiredDate]
    private var cooldownTracker: [String: Date] = [:]

    /// Dedup tracking: [dedupKey: lastFiredDate]
    private var dedupTracker: [String: Date] = [:]

    /// Dedup window in seconds
    private let dedupWindow: TimeInterval = 60

    /// Max alerts to keep in history
    private let maxAlerts = 50

    /// Banner auto-dismiss timer
    private var bannerDismissTask: Task<Void, Never>?

    /// Banner queue
    private var bannerQueue: [SmartAlert] = []
    private var isBannerShowing = false

    private init() {
        self.alertsEnabled = UserDefaults.standard.object(forKey: "alertsEnabled") as? Bool ?? true
        self.alertSoundEnabled = UserDefaults.standard.object(forKey: "alertSoundEnabled") as? Bool ?? true
    }

    // MARK: - Process Alerts

    /// Process a batch of alerts from MarketAlertService.
    /// Filters through dedup, cooldown, and user preferences before delivering.
    func process(_ alerts: [SmartAlert]) {
        guard alertsEnabled else { return }

        let now = Date()

        for alert in alerts {
            // Check per-type user preference
            let typeKey = alert.type.settingsKey
            let typeEnabled = UserDefaults.standard.object(forKey: typeKey) as? Bool ?? true
            guard typeEnabled else { continue }

            // Dedup check — same composite key within 60s window
            if let lastDedup = dedupTracker[alert.dedupKey],
               now.timeIntervalSince(lastDedup) < dedupWindow {
                continue
            }

            // Cooldown check — per-type cooldown
            let cooldownKey = alert.type.rawValue
            if let lastCooldown = cooldownTracker[cooldownKey],
               now.timeIntervalSince(lastCooldown) < alert.type.cooldownDuration {
                continue
            }

            // Passed all filters — deliver
            deliver(alert)

            // Update trackers
            dedupTracker[alert.dedupKey] = now
            cooldownTracker[cooldownKey] = now
        }

        // Prune old dedup entries (older than 5 minutes)
        dedupTracker = dedupTracker.filter { now.timeIntervalSince($0.value) < 300 }
    }

    // MARK: - Delivery

    private func deliver(_ alert: SmartAlert) {
        // Add to history
        activeAlerts.insert(alert, at: 0)
        if activeAlerts.count > maxAlerts {
            activeAlerts = Array(activeAlerts.prefix(maxAlerts))
        }

        // Publish to Combine stream
        alertSubject.send(alert)

        // Queue banner
        enqueueBanner(alert)

        // Fire local notification if app is backgrounded
        postLocalNotification(alert)

        // Haptic feedback
        let hapticEnabled = UserDefaults.standard.object(forKey: "hapticFeedbackEnabled") as? Bool ?? true
        if hapticEnabled {
            let generator = UIImpactFeedbackGenerator(style: alert.severity == .high ? .heavy : .medium)
            generator.impactOccurred()
        }
    }

    // MARK: - Banner Management

    private func enqueueBanner(_ alert: SmartAlert) {
        bannerQueue.append(alert)
        showNextBannerIfNeeded()
    }

    private func showNextBannerIfNeeded() {
        guard !isBannerShowing, let next = bannerQueue.first else { return }
        bannerQueue.removeFirst()
        isBannerShowing = true
        currentBanner = next

        // Auto-dismiss after 4 seconds
        bannerDismissTask?.cancel()
        bannerDismissTask = Task { @MainActor in
            try? await Task.sleep(nanoseconds: 4_000_000_000)
            guard !Task.isCancelled else { return }
            dismissBanner()
        }
    }

    func dismissBanner() {
        bannerDismissTask?.cancel()
        currentBanner = nil
        isBannerShowing = false

        // Show next queued banner after a brief delay
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: 300_000_000)
            showNextBannerIfNeeded()
        }
    }

    // MARK: - Local Notifications

    private func postLocalNotification(_ alert: SmartAlert) {
        let content = UNMutableNotificationContent()
        content.title = alert.title
        content.body = alert.message
        if alertSoundEnabled {
            content.sound = .default
        }
        content.categoryIdentifier = "SMART_ALERT"
        content.userInfo = [
            "alertType": alert.type.rawValue,
            "severity": alert.severity.rawValue
        ]

        let request = UNNotificationRequest(
            identifier: alert.id.uuidString,
            content: content,
            trigger: nil // Deliver immediately
        )

        UNUserNotificationCenter.current().add(request)
    }

    // MARK: - History Management

    func clearAllAlerts() {
        activeAlerts.removeAll()
    }

    func removeAlert(_ alert: SmartAlert) {
        activeAlerts.removeAll { $0.id == alert.id }
    }

    // MARK: - Read/Unread Management

    var unreadCount: Int {
        activeAlerts.filter { !$0.isRead }.count
    }

    func markAsRead(_ alert: SmartAlert) {
        if let index = activeAlerts.firstIndex(where: { $0.id == alert.id }) {
            activeAlerts[index].isRead = true
        }
    }

    func markAllAsRead() {
        for index in activeAlerts.indices {
            activeAlerts[index].isRead = true
        }
    }

    // MARK: - Preference Helpers

    func isAlertTypeEnabled(_ type: SmartAlertType) -> Bool {
        UserDefaults.standard.object(forKey: type.settingsKey) as? Bool ?? true
    }

    func setAlertTypeEnabled(_ type: SmartAlertType, enabled: Bool) {
        UserDefaults.standard.set(enabled, forKey: type.settingsKey)
    }
}
