import SwiftUI

/// Login view for Shoonya broker authentication
struct ShoonyaLoginView: View {
    @StateObject private var manager = ShoonyaManager.shared
    @Environment(\.dismiss) private var dismiss

    @State private var userId: String = ""
    @State private var password: String = ""
    @State private var totp: String = ""
    @State private var vendorCode: String = ""
    @State private var apiKey: String = ""
    @State private var showAdvancedSettings: Bool = false
    @State private var showPassword: Bool = false
    @State private var isFirstTimeSetup: Bool = true

    @FocusState private var focusedField: Field?

    enum Field: Hashable {
        case userId, password, totp, vendorCode, apiKey
    }

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(spacing: 24) {
                    // Header
                    headerView

                    // Login Form
                    loginFormView

                    // Advanced Settings
                    if isFirstTimeSetup || showAdvancedSettings {
                        advancedSettingsView
                    }

                    // Login Button
                    loginButton

                    // Error Message
                    if let error = manager.errorMessage {
                        errorView(error)
                    }

                    // Help Section
                    helpSection

                    Spacer(minLength: 40)
                }
                .padding(24)
            }
            .background(Color(.systemGroupedBackground))
            .navigationTitle("Connect Broker")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") {
                        dismiss()
                    }
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
                    .fill(LinearGradient(
                        colors: [Color.green, Color.blue],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    ))
                    .frame(width: 72, height: 72)

                Text("S")
                    .font(.system(size: 32, weight: .bold))
                    .foregroundColor(.white)
            }

            Text("Shoonya by Finvasia")
                .font(.title2)
                .fontWeight(.semibold)

            Text("Connect your broker account to get live market data")
                .font(.subheadline)
                .foregroundColor(.secondary)
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
                    .font(.subheadline)
                    .fontWeight(.medium)
                    .foregroundColor(.secondary)

                TextField("e.g., FA12345", text: $userId)
                    .textContentType(.username)
                    .autocapitalization(.allCharacters)
                    .disableAutocorrection(true)
                    .focused($focusedField, equals: .userId)
                    .padding()
                    .background(Color(.systemBackground))
                    .cornerRadius(12)
                    .overlay(
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(focusedField == .userId ? Color.green : Color(.separator), lineWidth: 1)
                    )
            }

            // Password
            VStack(alignment: .leading, spacing: 8) {
                Text("Password")
                    .font(.subheadline)
                    .fontWeight(.medium)
                    .foregroundColor(.secondary)

                HStack {
                    if showPassword {
                        TextField("Enter password", text: $password)
                    } else {
                        SecureField("Enter password", text: $password)
                    }

                    Button(action: { showPassword.toggle() }) {
                        Image(systemName: showPassword ? "eye.slash" : "eye")
                            .foregroundColor(.secondary)
                    }
                }
                .textContentType(.password)
                .focused($focusedField, equals: .password)
                .padding()
                .background(Color(.systemBackground))
                .cornerRadius(12)
                .overlay(
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(focusedField == .password ? Color.green : Color(.separator), lineWidth: 1)
                )
            }

            // TOTP
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    Text("TOTP / OTP")
                        .font(.subheadline)
                        .fontWeight(.medium)
                        .foregroundColor(.secondary)

                    Spacer()

                    Button(action: {}) {
                        Text("How to get?")
                            .font(.caption)
                            .foregroundColor(.blue)
                    }
                }

                TextField("6-digit code", text: $totp)
                    .keyboardType(.numberPad)
                    .textContentType(.oneTimeCode)
                    .focused($focusedField, equals: .totp)
                    .padding()
                    .background(Color(.systemBackground))
                    .cornerRadius(12)
                    .overlay(
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(focusedField == .totp ? Color.green : Color(.separator), lineWidth: 1)
                    )
                    .onChange(of: totp) { newValue in
                        // Limit to 6 digits
                        if newValue.count > 6 {
                            totp = String(newValue.prefix(6))
                        }
                    }
            }
        }
    }

    // MARK: - Advanced Settings

    private var advancedSettingsView: some View {
        VStack(spacing: 16) {
            if !isFirstTimeSetup {
                Divider()
            }

            // Toggle for showing advanced settings
            if !isFirstTimeSetup {
                Button(action: { withAnimation { showAdvancedSettings.toggle() } }) {
                    HStack {
                        Text("API Settings")
                            .font(.subheadline)
                            .fontWeight(.medium)
                        Spacer()
                        Image(systemName: showAdvancedSettings ? "chevron.up" : "chevron.down")
                    }
                    .foregroundColor(.secondary)
                }
            }

            // Vendor Code
            VStack(alignment: .leading, spacing: 8) {
                Text("Vendor Code")
                    .font(.subheadline)
                    .fontWeight(.medium)
                    .foregroundColor(.secondary)

                TextField("Provided by Shoonya", text: $vendorCode)
                    .autocapitalization(.allCharacters)
                    .disableAutocorrection(true)
                    .focused($focusedField, equals: .vendorCode)
                    .padding()
                    .background(Color(.systemBackground))
                    .cornerRadius(12)
                    .overlay(
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(focusedField == .vendorCode ? Color.green : Color(.separator), lineWidth: 1)
                    )
            }

            // API Key
            VStack(alignment: .leading, spacing: 8) {
                Text("API Key")
                    .font(.subheadline)
                    .fontWeight(.medium)
                    .foregroundColor(.secondary)

                TextField("Your API key", text: $apiKey)
                    .disableAutocorrection(true)
                    .focused($focusedField, equals: .apiKey)
                    .padding()
                    .background(Color(.systemBackground))
                    .cornerRadius(12)
                    .overlay(
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(focusedField == .apiKey ? Color.green : Color(.separator), lineWidth: 1)
                    )
            }

            // Info text
            Text("You only need to enter API settings once. They will be saved securely.")
                .font(.caption)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
        }
        .padding()
        .background(Color(.secondarySystemGroupedBackground))
        .cornerRadius(16)
    }

    // MARK: - Login Button

    private var loginButton: some View {
        Button(action: performLogin) {
            HStack {
                if manager.isLoading {
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
            .background(isFormValid ? Color.green : Color.gray)
            .foregroundColor(.white)
            .cornerRadius(14)
        }
        .disabled(!isFormValid || manager.isLoading)
    }

    // MARK: - Error View

    private func errorView(_ message: String) -> some View {
        HStack(spacing: 8) {
            Image(systemName: "exclamationmark.triangle.fill")
                .foregroundColor(.red)

            Text(message)
                .font(.subheadline)
                .foregroundColor(.red)
        }
        .padding()
        .frame(maxWidth: .infinity)
        .background(Color.red.opacity(0.1))
        .cornerRadius(12)
    }

    // MARK: - Help Section

    private var helpSection: some View {
        VStack(spacing: 16) {
            Divider()

            VStack(spacing: 12) {
                Text("Need help?")
                    .font(.subheadline)
                    .fontWeight(.medium)

                VStack(alignment: .leading, spacing: 8) {
                    helpItem(icon: "1.circle.fill", text: "Open Shoonya app")
                    helpItem(icon: "2.circle.fill", text: "Go to Profile > Security > TOTP")
                    helpItem(icon: "3.circle.fill", text: "Copy the 6-digit code")
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                Link(destination: URL(string: "https://shoonya.com/api-documentation")!) {
                    HStack {
                        Image(systemName: "doc.text")
                        Text("API Documentation")
                    }
                    .font(.subheadline)
                    .foregroundColor(.blue)
                }
            }
            .padding()
            .background(Color(.secondarySystemGroupedBackground))
            .cornerRadius(16)
        }
    }

    private func helpItem(icon: String, text: String) -> some View {
        HStack(spacing: 10) {
            Image(systemName: icon)
                .foregroundColor(.green)
                .frame(width: 24)

            Text(text)
                .font(.subheadline)
                .foregroundColor(.secondary)
        }
    }

    // MARK: - Validation

    private var isFormValid: Bool {
        !userId.isEmpty &&
        !password.isEmpty &&
        totp.count == 6 &&
        (isFirstTimeSetup ? (!vendorCode.isEmpty && !apiKey.isEmpty) : true)
    }

    // MARK: - Actions

    private func performLogin() {
        Task {
            do {
                try await manager.login(
                    userId: userId.uppercased(),
                    password: password,
                    totp: totp,
                    vendorCode: vendorCode.isEmpty ? nil : vendorCode,
                    apiKey: apiKey.isEmpty ? nil : apiKey
                )
                dismiss()
            } catch {
                // Error is handled by manager
            }
        }
    }
}

// MARK: - Preview

struct ShoonyaLoginView_Previews: PreviewProvider {
    static var previews: some View {
        ShoonyaLoginView()
    }
}

// MARK: - Shoonya Connection Status View

struct ShoonyaConnectionStatus: View {
    @ObservedObject var manager = ShoonyaManager.shared

    var body: some View {
        HStack(spacing: 12) {
            // Status indicator
            Circle()
                .fill(manager.isLoggedIn ? Color.green : Color.red)
                .frame(width: 10, height: 10)

            VStack(alignment: .leading, spacing: 2) {
                Text(manager.isLoggedIn ? "Connected" : "Not Connected")
                    .font(.subheadline)
                    .fontWeight(.medium)

                if manager.isLoggedIn, let name = manager.userName {
                    Text(name)
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
            }

            Spacer()

            if manager.isLoggedIn {
                Button("Disconnect") {
                    Task {
                        await manager.logout()
                    }
                }
                .font(.subheadline)
                .foregroundColor(.red)
            } else {
                Button("Connect") {
                    manager.showLoginSheet = true
                }
                .font(.subheadline)
                .foregroundColor(.blue)
            }
        }
        .padding()
        .background(Color(.secondarySystemGroupedBackground))
        .cornerRadius(12)
        .sheet(isPresented: $manager.showLoginSheet) {
            ShoonyaLoginView()
        }
    }
}
