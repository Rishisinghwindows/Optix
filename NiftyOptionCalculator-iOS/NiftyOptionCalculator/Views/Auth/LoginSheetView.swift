import SwiftUI
import AuthenticationServices
#if canImport(GoogleSignIn)
import GoogleSignIn
#endif

struct LoginSheetView: View {
    @EnvironmentObject var authManager: AuthManager
    @Environment(\.dismiss) var dismiss

    @State private var isLoading: Bool = false
    @State private var errorMessage: String?

    var body: some View {
        NavigationView {
            VStack(spacing: 24) {
                Spacer()

                // Header
                VStack(spacing: 8) {
                    Image(systemName: "chart.line.uptrend.xyaxis.circle.fill")
                        .font(.system(size: 60))
                        .foregroundColor(.blue)

                    Text("Welcome to Optix")
                        .font(.title)
                        .fontWeight(.bold)

                    Text("Sign in to start trading options")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                }

                Spacer()

                // Sign in buttons
                VStack(spacing: 12) {
                    // Sign in with Apple (required by App Store guideline 4.8)
                    SignInWithAppleButton(.signIn) { request in
                        request.requestedScopes = [.fullName, .email]
                    } onCompletion: { result in
                        handleAppleSignIn(result: result)
                    }
                    .signInWithAppleButtonStyle(.whiteOutline)
                    .frame(height: 52)
                    .cornerRadius(12)

                    // Google Sign In
                    Button(action: handleGoogleSignIn) {
                        HStack(spacing: 12) {
                            GoogleLogoView()
                                .frame(width: 24, height: 24)
                            Text("Continue with Google")
                                .font(.system(size: 17, weight: .semibold))
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 16)
                        .background(Color(.systemBackground))
                        .overlay(
                            RoundedRectangle(cornerRadius: 12)
                                .stroke(Color.gray.opacity(0.3), lineWidth: 1)
                        )
                        .cornerRadius(12)
                        .shadow(color: .black.opacity(0.05), radius: 4, y: 2)
                    }
                    .foregroundColor(.primary)

                    Text("Sign in securely with your Apple or Google account")
                        .font(.caption)
                        .foregroundColor(.secondary)
                        .multilineTextAlignment(.center)
                }
                .padding(.horizontal)

                Spacer()

                // Error Message
                if let error = errorMessage {
                    Text(error)
                        .font(.caption)
                        .foregroundColor(.red)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal)
                }

                // Terms
                Text("By continuing, you agree to our Terms of Service and Privacy Policy")
                    .font(.caption2)
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal)
                    .padding(.bottom)
            }
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") {
                        authManager.dismissLogin()
                        dismiss()
                    }
                }
            }
            .disabled(isLoading)
            .overlay {
                if isLoading {
                    ProgressView()
                        .scaleEffect(1.5)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .background(Color.black.opacity(0.2))
                }
            }
        }
    }

    // MARK: - Actions

    private func handleAppleSignIn(result: Result<ASAuthorization, Error>) {
        switch result {
        case .success(let authorization):
            isLoading = true
            errorMessage = nil
            Task {
                do {
                    try await authManager.loginWithApple(authorization: authorization)
                    AnalyticsService.logLogin(method: "apple")
                    isLoading = false
                    await authManager.onLoginSuccess()
                    dismiss()
                } catch let error as AuthError {
                    errorMessage = error.errorDescription
                    isLoading = false
                } catch {
                    errorMessage = error.localizedDescription
                    isLoading = false
                }
            }
        case .failure(let error):
            // User cancelled or Apple Sign-In failed
            if (error as NSError).code != ASAuthorizationError.canceled.rawValue {
                errorMessage = error.localizedDescription
            }
        }
    }

    private func handleGoogleSignIn() {
        #if canImport(GoogleSignIn)
        guard let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
              let rootViewController = windowScene.windows.first?.rootViewController else {
            errorMessage = "Unable to find root view controller"
            return
        }

        isLoading = true
        errorMessage = nil

        GIDSignIn.sharedInstance.signIn(withPresenting: rootViewController) { result, error in
            Task { @MainActor in
                if let error = error {
                    self.errorMessage = error.localizedDescription
                    self.isLoading = false
                    return
                }

                guard let user = result?.user,
                      let idToken = user.idToken?.tokenString else {
                    self.errorMessage = "Failed to get Google ID token"
                    self.isLoading = false
                    return
                }

                do {
                    try await authManager.loginWithGoogle(idToken: idToken)
                    // Log after successful auth so the event carries the newly set user ID
                    AnalyticsService.logLogin(method: "google")
                    self.isLoading = false
                    await authManager.onLoginSuccess()
                    dismiss()
                } catch let error as AuthError {
                    self.errorMessage = error.errorDescription
                    self.isLoading = false
                } catch {
                    self.errorMessage = error.localizedDescription
                    self.isLoading = false
                }
            }
        }
        #else
        errorMessage = "Google Sign-In requires adding the GoogleSignIn SDK to your project"
        #endif
    }
}

// MARK: - Google Logo View

struct GoogleLogoView: View {
    var body: some View {
        ZStack {
            Circle()
                .stroke(lineWidth: 3)
                .foregroundColor(.clear)

            Text("G")
                .font(.system(size: 16, weight: .bold, design: .rounded))
                .foregroundStyle(
                    LinearGradient(
                        colors: [
                            Color(red: 0.91, green: 0.26, blue: 0.21),
                            Color(red: 0.98, green: 0.74, blue: 0.18),
                            Color(red: 0.20, green: 0.66, blue: 0.33),
                            Color(red: 0.26, green: 0.52, blue: 0.96)
                        ],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
        }
    }
}

#Preview {
    LoginSheetView()
        .environmentObject(AuthManager.shared)
}
