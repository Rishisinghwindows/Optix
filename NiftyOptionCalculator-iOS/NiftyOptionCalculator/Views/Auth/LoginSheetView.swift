import SwiftUI
import AuthenticationServices
#if canImport(GoogleSignIn)
import GoogleSignIn
#endif

struct LoginSheetView: View {
    @EnvironmentObject var authManager: AuthManager
    @Environment(\.dismiss) var dismiss

    @State private var selectedTab: LoginTab = .phone
    @State private var phoneNumber: String = ""
    @State private var otp: String = ""
    @State private var showOTPField: Bool = false
    @State private var isLoading: Bool = false
    @State private var errorMessage: String?
    @State private var otpExpiresIn: Int = 0
    @State private var resendAfter: Int = 0
    @State private var expiryTimer: Timer?
    @State private var cooldownTimer: Timer?

    enum LoginTab {
        case phone
        case social
    }

    var body: some View {
        NavigationView {
            VStack(spacing: 24) {
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
                .padding(.top, 20)

                // Tab Picker
                Picker("Login Method", selection: $selectedTab) {
                    Text("Phone").tag(LoginTab.phone)
                    Text("Social").tag(LoginTab.social)
                }
                .pickerStyle(.segmented)
                .padding(.horizontal)

                // Content
                if selectedTab == .phone {
                    phoneLoginView
                } else {
                    socialLoginView
                }

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
        .onDisappear {
            expiryTimer?.invalidate()
            cooldownTimer?.invalidate()
        }
    }

    // MARK: - Phone Login View

    private var phoneLoginView: some View {
        VStack(spacing: 16) {
            if !showOTPField {
                // Phone Input
                VStack(alignment: .leading, spacing: 8) {
                    Text("Phone Number")
                        .font(.caption)
                        .foregroundColor(.secondary)

                    HStack {
                        Text("+91")
                            .foregroundColor(.secondary)
                            .padding(.leading, 12)

                        TextField("9876543210", text: $phoneNumber)
                            .keyboardType(.phonePad)
                            .textContentType(.telephoneNumber)
                    }
                    .padding(.vertical, 12)
                    .background(Color(.systemGray6))
                    .cornerRadius(10)
                }
                .padding(.horizontal)

                Button(action: sendOTP) {
                    Text("Send OTP")
                        .fontWeight(.semibold)
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(phoneNumber.count >= 10 ? Color.blue : Color.gray)
                        .foregroundColor(.white)
                        .cornerRadius(12)
                }
                .disabled(phoneNumber.count < 10)
                .padding(.horizontal)
            } else {
                // OTP Input
                VStack(alignment: .leading, spacing: 8) {
                    HStack {
                        Text("Enter OTP sent to +91 \(phoneNumber)")
                            .font(.caption)
                            .foregroundColor(.secondary)

                        Spacer()

                        Button("Change") {
                            withAnimation {
                                showOTPField = false
                                otp = ""
                            }
                        }
                        .font(.caption)
                    }

                    TextField("6-digit OTP", text: $otp)
                        .keyboardType(.numberPad)
                        .textContentType(.oneTimeCode)
                        .multilineTextAlignment(.center)
                        .font(.title2.monospacedDigit())
                        .padding(.vertical, 12)
                        .background(Color(.systemGray6))
                        .cornerRadius(10)
                        .onChange(of: otp) { _, newValue in
                            // Auto-submit when 6 digits entered
                            if newValue.count == 6 {
                                verifyOTP()
                            }
                        }

                    HStack {
                        if otpExpiresIn > 0 {
                            Text("Expires in \(formatTime(otpExpiresIn))")
                                .font(.caption)
                                .foregroundColor(.orange)
                        }

                        Spacer()

                        if resendAfter > 0 {
                            Text("Resend in \(resendAfter)s")
                                .font(.caption)
                                .foregroundColor(.secondary)
                        } else {
                            Button("Resend OTP") {
                                sendOTP()
                            }
                            .font(.caption)
                        }
                    }
                }
                .padding(.horizontal)

                Button(action: verifyOTP) {
                    Text("Verify & Login")
                        .fontWeight(.semibold)
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(otp.count == 6 ? Color.blue : Color.gray)
                        .foregroundColor(.white)
                        .cornerRadius(12)
                }
                .disabled(otp.count != 6)
                .padding(.horizontal)
            }
        }
    }

    // MARK: - Social Login View

    private var socialLoginView: some View {
        VStack(spacing: 16) {
            // Google Sign In Only
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

            Text("Sign in securely with your Google account")
                .font(.caption)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
        }
        .padding(.horizontal)
        .padding(.top, 20)
    }

    // MARK: - Actions

    private func sendOTP() {
        guard !phoneNumber.isEmpty else { return }

        isLoading = true
        errorMessage = nil

        Task {
            do {
                let formattedPhone = "+91\(phoneNumber)"
                let response = try await authManager.sendOTP(phone: formattedPhone)

                await MainActor.run {
                    otpExpiresIn = response.expiresIn
                    resendAfter = response.resendAfter
                    withAnimation {
                        showOTPField = true
                    }
                    isLoading = false
                    startTimers()
                }
            } catch let error as AuthError {
                await MainActor.run {
                    errorMessage = error.errorDescription
                    isLoading = false
                }
            } catch {
                await MainActor.run {
                    errorMessage = error.localizedDescription
                    isLoading = false
                }
            }
        }
    }

    private func verifyOTP() {
        guard otp.count == 6 else { return }

        isLoading = true
        errorMessage = nil

        Task {
            do {
                let formattedPhone = "+91\(phoneNumber)"
                try await authManager.verifyOTP(phone: formattedPhone, otp: otp)

                await MainActor.run {
                    isLoading = false
                }

                await authManager.onLoginSuccess()
                dismiss()
            } catch let error as AuthError {
                await MainActor.run {
                    errorMessage = error.errorDescription
                    isLoading = false
                }
            } catch {
                await MainActor.run {
                    errorMessage = error.localizedDescription
                    isLoading = false
                }
            }
        }
    }

    private func handleAppleSignIn(_ result: Result<ASAuthorization, Error>) {
        switch result {
        case .success(let authorization):
            isLoading = true
            errorMessage = nil

            Task {
                do {
                    try await authManager.loginWithApple(authorization: authorization)
                    await MainActor.run {
                        isLoading = false
                    }
                    await authManager.onLoginSuccess()
                    dismiss()
                } catch let error as AuthError {
                    await MainActor.run {
                        errorMessage = error.errorDescription
                        isLoading = false
                    }
                } catch {
                    await MainActor.run {
                        errorMessage = error.localizedDescription
                        isLoading = false
                    }
                }
            }

        case .failure(let error):
            errorMessage = error.localizedDescription
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
        // GoogleSignIn SDK not installed - show message
        errorMessage = "Google Sign-In requires adding the GoogleSignIn SDK to your project"
        #endif
    }

    // MARK: - Helpers

    private func startTimers() {
        expiryTimer?.invalidate()
        cooldownTimer?.invalidate()

        // OTP expiry timer
        expiryTimer = Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { _ in
            if otpExpiresIn > 0 {
                otpExpiresIn -= 1
            } else {
                expiryTimer?.invalidate()
            }
        }

        // Resend cooldown timer
        cooldownTimer = Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { _ in
            if resendAfter > 0 {
                resendAfter -= 1
            } else {
                cooldownTimer?.invalidate()
            }
        }
    }

    private func formatTime(_ seconds: Int) -> String {
        let minutes = seconds / 60
        let secs = seconds % 60
        return String(format: "%d:%02d", minutes, secs)
    }
}

// MARK: - Google Logo View

struct GoogleLogoView: View {
    var body: some View {
        ZStack {
            // G letter made with colored segments
            Circle()
                .stroke(lineWidth: 3)
                .foregroundColor(.clear)

            // Use a simple G representation with Google colors
            Text("G")
                .font(.system(size: 16, weight: .bold, design: .rounded))
                .foregroundStyle(
                    LinearGradient(
                        colors: [
                            Color(red: 0.91, green: 0.26, blue: 0.21), // Red
                            Color(red: 0.98, green: 0.74, blue: 0.18), // Yellow
                            Color(red: 0.20, green: 0.66, blue: 0.33), // Green
                            Color(red: 0.26, green: 0.52, blue: 0.96)  // Blue
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
