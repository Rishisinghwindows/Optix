import Foundation
import UserNotifications
import UIKit
import os.log

private let logger = Logger(subsystem: "com.optix.app", category: "PushNotifications")

// MARK: - Push Notification Service

class PushNotificationService: NSObject, ObservableObject, UNUserNotificationCenterDelegate {
    static let shared = PushNotificationService()

    @Published var isPermissionGranted = false
    @Published var deviceToken: String?

    private let session: URLSession

    private var baseURL: String {
        return AppEnvironment.current.apiBaseURL
    }

    private override init() {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 30
        config.timeoutIntervalForResource = 60
        self.session = URLSession(configuration: config)
        super.init()
    }

    // MARK: - Setup

    /// Call this early in the app lifecycle to set up the notification delegate and check permission status
    func setup() {
        UNUserNotificationCenter.current().delegate = self
        checkPermissionStatus()
    }

    // MARK: - Permission

    /// Request notification permission from the user
    func requestPermission() {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { [weak self] granted, error in
            DispatchQueue.main.async {
                self?.isPermissionGranted = granted
                if granted {
                    logger.info("Push notification permission granted")
                    self?.registerForRemoteNotifications()
                } else if let error = error {
                    logger.error("Push notification permission error: \(error.localizedDescription)")
                } else {
                    logger.info("Push notification permission denied")
                }
            }
        }
    }

    /// Check current permission status without prompting
    func checkPermissionStatus() {
        UNUserNotificationCenter.current().getNotificationSettings { [weak self] settings in
            DispatchQueue.main.async {
                self?.isPermissionGranted = settings.authorizationStatus == .authorized
                if settings.authorizationStatus == .authorized {
                    self?.registerForRemoteNotifications()
                }
            }
        }
    }

    /// Register for remote (APNs) notifications on the main thread
    private func registerForRemoteNotifications() {
        DispatchQueue.main.async {
            UIApplication.shared.registerForRemoteNotifications()
        }
    }

    // MARK: - Token Registration

    /// Called from AppDelegate when APNs token is received
    func registerToken(_ deviceToken: Data) {
        let token = deviceToken.map { String(format: "%02.2hhx", $0) }.joined()
        self.deviceToken = token
        logger.info("APNs device token received: \(token.prefix(16))...")

        Task {
            await sendTokenToBackend(token)
        }
    }

    /// Send the APNs token to the backend for push notification delivery
    func sendTokenToBackend(_ token: String) async {
        guard let authToken = await MainActor.run(body: { AuthManager.shared.accessToken }) else {
            logger.warning("Cannot register device token: user not authenticated")
            return
        }

        guard let url = URL(string: "\(baseURL)/devices/register") else {
            logger.error("Invalid URL for device registration")
            return
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("Bearer \(authToken)", forHTTPHeaderField: "Authorization")

        let body: [String: Any] = [
            "token": token,
            "platform": "ios",
            "device_name": await MainActor.run { UIDevice.current.name }
        ]

        do {
            request.httpBody = try JSONSerialization.data(withJSONObject: body)
            let (_, response) = try await session.data(for: request)

            if let httpResponse = response as? HTTPURLResponse {
                if (200...299).contains(httpResponse.statusCode) {
                    logger.info("Device token registered with backend successfully")
                } else {
                    logger.error("Device token registration failed: HTTP \(httpResponse.statusCode)")
                }
            }
        } catch {
            logger.error("Device token registration error: \(error.localizedDescription)")
        }
    }

    // MARK: - UNUserNotificationCenterDelegate

    /// Handle notification when app is in foreground
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        let userInfo = notification.request.content.userInfo
        logger.info("Notification received in foreground: \(userInfo)")

        // Show banner, sound, and badge even when app is in foreground
        completionHandler([.banner, .sound, .badge])
    }

    /// Handle notification tap (app opened from notification)
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let userInfo = response.notification.request.content.userInfo
        logger.info("Notification tapped: \(userInfo)")

        handleNotificationDeepLink(userInfo: userInfo)

        completionHandler()
    }

    // MARK: - Deep Linking

    private func handleNotificationDeepLink(userInfo: [AnyHashable: Any]) {
        // Extract deep link type from notification payload
        guard let type = userInfo["type"] as? String else {
            logger.info("No deep link type in notification")
            return
        }

        DispatchQueue.main.async {
            switch type {
            case "alert":
                // Navigate to alerts screen
                NotificationCenter.default.post(
                    name: .pushNotificationDeepLink,
                    object: nil,
                    userInfo: ["destination": "alerts"]
                )
            case "paper_trade":
                // Navigate to paper trading screen
                NotificationCenter.default.post(
                    name: .pushNotificationDeepLink,
                    object: nil,
                    userInfo: ["destination": "paper_trading"]
                )
            case "journal":
                // Navigate to trade journal
                NotificationCenter.default.post(
                    name: .pushNotificationDeepLink,
                    object: nil,
                    userInfo: ["destination": "journal"]
                )
            case "ai_insight":
                // Navigate to AI insights
                NotificationCenter.default.post(
                    name: .pushNotificationDeepLink,
                    object: nil,
                    userInfo: ["destination": "ai_insights"]
                )
            default:
                logger.info("Unknown deep link type: \(type)")
            }
        }
    }

    // MARK: - Badge Management

    /// Clear the app badge count
    func clearBadge() {
        DispatchQueue.main.async {
            UIApplication.shared.applicationIconBadgeNumber = 0
        }
    }
}

// MARK: - Notification Name Extension

extension Notification.Name {
    static let pushNotificationDeepLink = Notification.Name("pushNotificationDeepLink")
}
