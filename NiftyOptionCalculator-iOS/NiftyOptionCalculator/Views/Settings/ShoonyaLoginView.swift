import SwiftUI

/// Login view for Shoonya broker authentication
struct ShoonyaLoginView: View {
    @ObservedObject var shoonyaService = ShoonyaAPIService.shared
    @Environment(\.dismiss) private var dismiss

    @State private var userId: String = ""
    @State private var password: String = ""
    @State private var totp: String = ""
    @State private var showPassword: Bool = false
    @State private var isLoading: Bool = false
    @State private var errorMessage: String?

    @FocusState private var focusedField: Field?

    enum Field: Hashable {
        case userId, password, totp
    }

    var body: some View {
        NavigationView {
            ZStack {
                Theme.backgroundGradient.ignoresSafeArea()

                ScrollView {
                    VStack(spacing: 24) {
                        // Header
                        headerView

                        // Login Form
                        loginFormView

                        // Login Button
                        loginButton

                        // Error Message
                        if let error = errorMessage {
                            errorView(error)
                        }

                        // Help Section
                        helpSection

                        Spacer(minLength: 40)
                    }
                    .padding(24)
                }
            }
            .navigationTitle("Connect Shoonya")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") {
                        dismiss()
                    }
                    .foregroundColor(Theme.textSecondary)
                }
            }
        }
    }

    // MARK: - Header View

    private var headerView: some View {
        VStack(spacing: 12) {
            // Shoonya Logo
            ZStack {
                Circle()
                    .fill(
                        LinearGradient(
                            colors: [Color(hex: "1E88E5"), Color(hex: "1565C0")],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
                    .frame(width: 72, height: 72)

                Image(systemName: "s.circle.fill")
                    .font(.system(size: 36, weight: .bold))
                    .foregroundColor(.white)
            }

            Text("Shoonya by Finvasia")
                .font(.system(size: 22, weight: .bold))
                .foregroundColor(Theme.textPrimary)

            Text("Zero brokerage trading with live market data")
                .font(.system(size: 14))
                .foregroundColor(Theme.textSecondary)
                .multilineTextAlignment(.center)
        }
        .padding(.bottom, 8)
    }

    // MARK: - Login Form

    private var loginFormView: some View {
        VStack(spacing: 16) {
            // User ID
            VStack(alignment: .leading, spacing: 8) {
                Text("User ID")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundColor(Theme.textSecondary)

                TextField("e.g., FA12345", text: $userId)
                    .textContentType(.username)
                    .autocapitalization(.allCharacters)
                    .disableAutocorrection(true)
                    .focused($focusedField, equals: .userId)
                    .padding(14)
                    .background(Theme.surface)
                    .cornerRadius(12)
                    .overlay(
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(focusedField == .userId ? Color(hex: "1E88E5") : Theme.card, lineWidth: 1)
                    )
            }

            // Password
            VStack(alignment: .leading, spacing: 8) {
                Text("Password")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundColor(Theme.textSecondary)

                HStack {
                    if showPassword {
                        TextField("Enter password", text: $password)
                    } else {
                        SecureField("Enter password", text: $password)
                    }

                    Button(action: { showPassword.toggle() }) {
                        Image(systemName: showPassword ? "eye.slash" : "eye")
                            .foregroundColor(Theme.textMuted)
                    }
                }
                .textContentType(.password)
                .focused($focusedField, equals: .password)
                .padding(14)
                .background(Theme.surface)
                .cornerRadius(12)
                .overlay(
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(focusedField == .password ? Color(hex: "1E88E5") : Theme.card, lineWidth: 1)
                )
            }

            // TOTP
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    Text("TOTP / OTP")
                        .font(.system(size: 13, weight: .medium))
                        .foregroundColor(Theme.textSecondary)

                    Spacer()

                    Button(action: {}) {
                        Text("How to get?")
                            .font(.system(size: 11, weight: .medium))
                            .foregroundColor(Theme.accentBlue)
                    }
                }

                TextField("6-digit code", text: $totp)
                    .keyboardType(.numberPad)
                    .textContentType(.oneTimeCode)
                    .focused($focusedField, equals: .totp)
                    .padding(14)
                    .background(Theme.surface)
                    .cornerRadius(12)
                    .overlay(
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(focusedField == .totp ? Color(hex: "1E88E5") : Theme.card, lineWidth: 1)
                    )
                    .onChange(of: totp) { _, newValue in
                        // Limit to 6 digits
                        if newValue.count > 6 {
                            totp = String(newValue.prefix(6))
                        }
                    }
            }
        }
    }

    // MARK: - Login Button

    private var loginButton: some View {
        Button(action: performLogin) {
            HStack {
                if isLoading {
                    ProgressView()
                        .progressViewStyle(CircularProgressViewStyle(tint: .white))
                        .scaleEffect(0.9)
                } else {
                    Text("Connect Account")
                        .fontWeight(.semibold)
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 16)
            .background(isFormValid ? Color(hex: "1E88E5") : Theme.textMuted)
            .foregroundColor(.white)
            .cornerRadius(14)
        }
        .disabled(!isFormValid || isLoading)
    }

    // MARK: - Error View

    private func errorView(_ message: String) -> some View {
        HStack(spacing: 8) {
            Image(systemName: "exclamationmark.triangle.fill")
                .foregroundColor(Theme.loss)

            Text(message)
                .font(.system(size: 13))
                .foregroundColor(Theme.loss)
        }
        .padding(12)
        .frame(maxWidth: .infinity)
        .background(Theme.loss.opacity(0.1))
        .cornerRadius(12)
    }

    // MARK: - Help Section

    private var helpSection: some View {
        VStack(spacing: 16) {
            Divider()
                .background(Theme.card)

            VStack(spacing: 12) {
                Text("Need help?")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(Theme.textPrimary)

                VStack(alignment: .leading, spacing: 8) {
                    helpItem(icon: "1.circle.fill", text: "Open Shoonya app or website")
                    helpItem(icon: "2.circle.fill", text: "Go to Profile > API Settings")
                    helpItem(icon: "3.circle.fill", text: "Enable TOTP and copy the 6-digit code")
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                Link(destination: URL(string: "https://shoonya.com/api-documentation")!) {
                    HStack {
                        Image(systemName: "doc.text")
                        Text("API Documentation")
                    }
                    .font(.system(size: 13, weight: .medium))
                    .foregroundColor(Theme.accentBlue)
                }
            }
            .padding(16)
            .background(Theme.surface)
            .cornerRadius(16)
        }
    }

    private func helpItem(icon: String, text: String) -> some View {
        HStack(spacing: 10) {
            Image(systemName: icon)
                .foregroundColor(Color(hex: "1E88E5"))
                .frame(width: 24)

            Text(text)
                .font(.system(size: 13))
                .foregroundColor(Theme.textSecondary)
        }
    }

    // MARK: - Validation

    private var isFormValid: Bool {
        !userId.isEmpty &&
        !password.isEmpty &&
        totp.count == 6
    }

    // MARK: - Actions

    private func performLogin() {
        isLoading = true
        errorMessage = nil

        Task {
            do {
                try await shoonyaService.authenticate(
                    userId: userId.uppercased(),
                    password: password,
                    totp: totp
                )
                await MainActor.run {
                    dismiss()
                }
            } catch let error as ProviderError {
                await MainActor.run {
                    errorMessage = error.localizedDescription
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
}

#Preview {
    ShoonyaLoginView()
}
