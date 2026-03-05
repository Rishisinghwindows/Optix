import SwiftUI
import UserNotifications
import os.log
#if canImport(GoogleSignIn)
import GoogleSignIn
#endif

@main
struct OptixApp: App {
    @StateObject private var authManager = AuthManager.shared
    @StateObject private var themeConfig = ThemeConfiguration.shared
    @StateObject private var localizationManager = LocalizationManager.shared
    @StateObject private var pushService = PushNotificationService.shared
    @Environment(\.colorScheme) private var systemColorScheme
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(authManager)
                .environmentObject(themeConfig)
                .environmentObject(localizationManager)
                .environmentObject(pushService)
                .preferredColorScheme(preferredScheme)
                .onOpenURL { url in
                    // Handle Google Sign-In callback
                    #if canImport(GoogleSignIn)
                    if GIDSignIn.sharedInstance.handle(url) {
                        return
                    }
                    #endif
                    // Handle Upstox OAuth callback
                    UpstoxAuthHandler.shared.handleCallback(url: url)
                }
                .onChange(of: systemColorScheme) { _, newValue in
                    themeConfig.updateColorScheme(systemScheme: newValue)
                }
                .onChange(of: themeConfig.colorScheme) { _, _ in
                    // When user changes appearance setting, update isDarkMode for system option
                    themeConfig.updateColorScheme(systemScheme: systemColorScheme)
                }
                .onAppear {
                    // Initialize color scheme on launch
                    themeConfig.updateColorScheme(systemScheme: systemColorScheme)

                    // Set up push notification service (delegate + permission request)
                    PushNotificationService.shared.setup()
                    PushNotificationService.shared.requestPermission()
                }
        }
    }

    private var preferredScheme: ColorScheme? {
        switch themeConfig.colorScheme {
        case .system: return nil
        case .light: return .light
        case .dark: return .dark
        }
    }
}

// MARK: - Root View (Splash → Onboarding → Main)

struct RootView: View {
    @State private var showSplash = true
    @State private var hasCompletedOnboarding = UserDefaults.standard.bool(forKey: "hasCompletedOnboarding")

    var body: some View {
        ZStack {
            if showSplash {
                SplashView()
                    .transition(.opacity)
            } else if !hasCompletedOnboarding {
                OnboardingView(hasCompletedOnboarding: $hasCompletedOnboarding)
                    .transition(.asymmetric(
                        insertion: .move(edge: .trailing),
                        removal: .move(edge: .leading)
                    ))
            } else {
                MainTabView()
                    .transition(.asymmetric(
                        insertion: .move(edge: .trailing).combined(with: .opacity),
                        removal: .opacity
                    ))
            }
        }
        .animation(.easeInOut(duration: 0.5), value: showSplash)
        .animation(.easeInOut(duration: 0.4), value: hasCompletedOnboarding)
        .onAppear {
            // Show splash for 2.5 seconds
            DispatchQueue.main.asyncAfter(deadline: .now() + 2.5) {
                withAnimation {
                    showSplash = false
                }
            }
        }
    }
}

// MARK: - App Delegate (APNs Token Handling)

class AppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {
        PushNotificationService.shared.registerToken(deviceToken)
    }

    func application(
        _ application: UIApplication,
        didFailToRegisterForRemoteNotificationsWithError error: Error
    ) {
        // Log the failure but don't crash - push is optional
        let logger = os.Logger(subsystem: "com.optix.app", category: "AppDelegate")
        logger.error("Failed to register for remote notifications: \(error.localizedDescription)")
    }
}

// MARK: - Upstox Auth Handler (Singleton)

@MainActor
class UpstoxAuthHandler: ObservableObject {
    static let shared = UpstoxAuthHandler()

    @Published var isAuthenticating = false
    @Published var authError: String?

    var onAuthSuccess: (() -> Void)?
    var onAuthFailure: ((String) -> Void)?

    private init() {}

    func startAuthentication(onSuccess: @escaping () -> Void, onFailure: @escaping (String) -> Void) {
        guard let authURL = UpstoxAPIService.shared.getAuthorizationURL() else {
            onFailure("Invalid authorization URL")
            return
        }

        self.onAuthSuccess = onSuccess
        self.onAuthFailure = onFailure
        self.isAuthenticating = true

        // Open Safari for authentication
        UIApplication.shared.open(authURL)
    }

    func handleCallback(url: URL) {
        guard url.scheme == "optix" else { return }

        // Extract authorization code
        guard let components = URLComponents(url: url, resolvingAgainstBaseURL: false),
              let code = components.queryItems?.first(where: { $0.name == "code" })?.value else {
            isAuthenticating = false
            onAuthFailure?("No authorization code received")
            return
        }

        // Exchange code for token
        Task {
            do {
                try await UpstoxAPIService.shared.authenticate(withCode: code)
                isAuthenticating = false
                onAuthSuccess?()
            } catch {
                isAuthenticating = false
                onAuthFailure?(error.localizedDescription)
            }
        }
    }
}
