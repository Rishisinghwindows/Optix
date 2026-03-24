import Foundation
import UserNotifications
import UIKit
import os.log
import FirebaseMessaging

private let logger = Logger(subsystem: "com.optix.app", category: "PushNotifications")

// MARK: - Push Notification Service

class PushNotificationService: NSObject, ObservableObject, UNUserNotificationCenterDelegate, MessagingDelegate {
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

    // MARK: - MessagingDelegate (FCM Token)

    /// Called by Firebase when FCM token is generated or refreshed
    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        guard let token = fcmToken else {
            logger.warning("Received nil FCM token")
            return
        }
        self.deviceToken = token
        logger.info("FCM token received: \(token.prefix(16))...")

        Task {
            await sendTokenToBackend(token)
        }
    }

    /// Send the FCM token to the backend for push notification delivery.
    ///
    /// Token registration flow:
    /// 1. Firebase generates/refreshes FCM token -> `messaging(_:didReceiveRegistrationToken:)` fires
    /// 2. Token is POSTed to `/devices/register` with optional auth header
    /// 3. Unauthenticated devices still register (receive broadcasts); authenticated ones get user-targeted pushes
    /// 4. On failure, retries up to 3 times with exponential backoff (2^n seconds)
    /// 5. After login, `reRegisterTokenIfNeeded()` re-sends with auth so backend links token to user
    func sendTokenToBackend(_ token: String, retryCount: Int = 0) async {
        guard let url = URL(string: "\(baseURL)/devices/register") else {
            logger.error("Invalid URL for device registration")
            return
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        // Attach auth token if available (optional)
        if let authToken = await MainActor.run(body: { AuthManager.shared.accessToken }) {
            request.setValue("Bearer \(authToken)", forHTTPHeaderField: "Authorization")
        }

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
                    tokenRegisteredSuccessfully = true
                } else {
                    logger.error("Device token registration failed: HTTP \(httpResponse.statusCode)")
                    await retryIfNeeded(token: token, retryCount: retryCount)
                }
            }
        } catch {
            logger.error("Device token registration error: \(error.localizedDescription)")
            await retryIfNeeded(token: token, retryCount: retryCount)
        }
    }

    /// Re-register the current FCM token (e.g., after user login to attach user_id)
    func reRegisterTokenIfNeeded() {
        guard let token = deviceToken else { return }
        Task {
            await sendTokenToBackend(token)
        }
    }

    private var tokenRegisteredSuccessfully = false

    /// Retry token registration with exponential backoff: 1s, 2s, 4s
    private func retryIfNeeded(token: String, retryCount: Int) async {
        let maxRetries = 3
        guard retryCount < maxRetries else {
            logger.error("Device token registration failed after \(maxRetries) retries")
            return
        }
        // Delay doubles each attempt: 2^0=1s, 2^1=2s, 2^2=4s
        let delay = UInt64(pow(2.0, Double(retryCount))) * 1_000_000_000
        logger.info("Retrying token registration in \(Int(pow(2.0, Double(retryCount))))s (attempt \(retryCount + 1)/\(maxRetries))")
        try? await Task.sleep(nanoseconds: delay)
        await sendTokenToBackend(token, retryCount: retryCount + 1)
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

        // Store market monitor signals
        if let type = userInfo["type"] as? String, type == "market_monitor" {
            DispatchQueue.main.async {
                MarketSignalStore.shared.addSignal(from: userInfo)
            }
        }

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

        // Store market monitor signals from background taps too
        if let type = userInfo["type"] as? String, type == "market_monitor" {
            DispatchQueue.main.async {
                MarketSignalStore.shared.addSignal(from: userInfo)
            }
        }

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
            case "market_monitor":
                // Navigate to option chain for the relevant index
                let signal = userInfo["signal"] as? String ?? ""
                let index = userInfo["index"] as? String ?? ""
                NotificationCenter.default.post(
                    name: .pushNotificationDeepLink,
                    object: nil,
                    userInfo: [
                        "destination": "market_monitor",
                        "signal": signal,
                        "index": index,
                    ]
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
