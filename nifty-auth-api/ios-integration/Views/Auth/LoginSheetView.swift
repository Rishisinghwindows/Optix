import SwiftUI
import AuthenticationServices

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
                        .onChange(of: otp) { newValue in
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
        .onAppear {
            startTimers()
        }
    }

    // MARK: - Social Login View

    private var socialLoginView: some View {
        VStack(spacing: 12) {
            // Apple Sign In
            SignInWithAppleButton(.signIn) { request in
                request.requestedScopes = [.email, .fullName]
            } onCompletion: { result in
                handleAppleSignIn(result)
            }
            .signInWithAppleButtonStyle(.black)
            .frame(height: 50)
            .cornerRadius(12)

            // Google Sign In
            Button(action: signInWithGoogle) {
                HStack {
                    Image("google-logo") // Add this asset
                        .resizable()
                        .frame(width: 24, height: 24)
                    Text("Continue with Google")
                        .fontWeight(.medium)
                }
                .frame(maxWidth: .infinity)
                .padding()
                .background(Color(.systemBackground))
                .overlay(
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(Color.gray.opacity(0.3), lineWidth: 1)
                )
            }
            .foregroundColor(.primary)

            // Facebook Sign In
            Button(action: signInWithFacebook) {
                HStack {
                    Image("facebook-logo") // Add this asset
                        .resizable()
                        .frame(width: 24, height: 24)
                    Text("Continue with Facebook")
                        .fontWeight(.medium)
                }
                .frame(maxWidth: .infinity)
                .padding()
                .background(Color(red: 0.23, green: 0.35, blue: 0.60))
                .foregroundColor(.white)
                .cornerRadius(12)
            }
        }
        .padding(.horizontal)
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

    private func signInWithGoogle() {
        isLoading = true
        errorMessage = nil

        Task {
            do {
                try await authManager.loginWithGoogle()
                await MainActor.run {
                    isLoading = false
                }
                await authManager.onLoginSuccess()
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

    private func signInWithFacebook() {
        isLoading = true
        errorMessage = nil

        Task {
            do {
                try await authManager.loginWithFacebook()
                await MainActor.run {
                    isLoading = false
                }
                await authManager.onLoginSuccess()
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

    // MARK: - Helpers

    private func startTimers() {
        // OTP expiry timer
        Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { timer in
            if otpExpiresIn > 0 {
                otpExpiresIn -= 1
            } else {
                timer.invalidate()
            }
        }

        // Resend cooldown timer
        Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { timer in
            if resendAfter > 0 {
                resendAfter -= 1
            } else {
                timer.invalidate()
            }
        }
    }

    private func formatTime(_ seconds: Int) -> String {
        let minutes = seconds / 60
        let secs = seconds % 60
        return String(format: "%d:%02d", minutes, secs)
    }
}

#Preview {
    LoginSheetView()
        .environmentObject(AuthManager.shared)
}
