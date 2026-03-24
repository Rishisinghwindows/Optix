import SwiftUI

struct SettingsView: View {
    @EnvironmentObject var themeConfig: ThemeConfiguration
    @EnvironmentObject var authManager: AuthManager
    @ObservedObject var viewModel: OptionChainViewModel
    @ObservedObject private var localization = LocalizationManager.shared

    @AppStorage("spotPriceInterval") private var spotPriceInterval: Double = 2.0
    @AppStorage("optionChainInterval") private var optionChainInterval: Double = 5.0
    @AppStorage("defaultIndex") private var defaultIndex: String = TradingIndex.nifty50.rawValue
    @AppStorage("hapticFeedbackEnabled") private var hapticFeedbackEnabled: Bool = true

    @State private var showDeleteConfirmation = false
    @State private var isDeletingAccount = false
    @State private var deleteError: String?

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(spacing: 24) {
                    // Account Section
                    accountSection

                    // Data Refresh Section
                    dataRefreshSection

                    // Default Index Section
                    defaultIndexSection

                    // Preferences Section
                    preferencesSection

                    // Smart Alerts Section
                    smartAlertsSection

                    // Education Section
                    educationSection

                    // Language Selection
                    languageSection

                    // Appearance Mode (Light/Dark)
                    appearanceSection

                    // Theme Selection (existing)
                    themeSection

                    // Privacy & AI Data Section
                    privacySection

                    // About Section (existing + developer attribution)
                    aboutSection

                    // Account Deletion (App Store guideline 5.1.1v)
                    if authManager.isLoggedIn {
                        accountDeletionSection
                    }

                    // Disclaimer Section
                    disclaimerSection
                }
                .padding(20)
            }
            .background(Theme.background.ignoresSafeArea())
            .navigationTitle(L.settingsTitle)
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Theme.surface, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
        }
    }

    // MARK: - Account Section

    private var accountSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(L.settingsAccount)
                .font(.system(size: 12, weight: .bold))
                .foregroundColor(Theme.textMuted)
                .padding(.horizontal, 4)

            VStack(spacing: 0) {
                // Connection status row
                HStack(spacing: 12) {
                    Image(systemName: viewModel.isUpstoxAuthenticated ? "checkmark.seal.fill" : "xmark.seal.fill")
                        .font(.system(size: 22))
                        .foregroundColor(viewModel.isUpstoxAuthenticated ? Theme.profit : Theme.loss)

                    VStack(alignment: .leading, spacing: 2) {
                        Text("Upstox")
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundColor(Theme.textPrimary)

                        Text(viewModel.isUpstoxAuthenticated ? L.upstoxConnected : L.upstoxNotConnected)
                            .font(.system(size: 12))
                            .foregroundColor(viewModel.isUpstoxAuthenticated ? Theme.profit : Theme.textSecondary)
                    }

                    Spacer()

                    // Data source badge
                    Text(viewModel.dataSource.displayName)
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(viewModel.isUpstoxAuthenticated ? Theme.profit : Theme.accentOrange)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 5)
                        .background {
                            Capsule()
                                .fill((viewModel.isUpstoxAuthenticated ? Theme.profit : Theme.accentOrange).opacity(0.15))
                        }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)

                // WebSocket status (if authenticated)
                if viewModel.isUpstoxAuthenticated {
                    Divider().background(Color.white.opacity(0.06))

                    HStack(spacing: 10) {
                        Image(systemName: viewModel.isWebSocketConnected ? "antenna.radiowaves.left.and.right" : "antenna.radiowaves.left.and.right.slash")
                            .font(.system(size: 14))
                            .foregroundColor(viewModel.isWebSocketConnected ? Theme.profit : Theme.textMuted)

                        VStack(alignment: .leading, spacing: 1) {
                            Text(L.upstoxRealTimeStream)
                                .font(.system(size: 13, weight: .medium))
                                .foregroundColor(Theme.textPrimary)

                            Text(viewModel.webSocketStatus)
                                .font(.system(size: 11))
                                .foregroundColor(viewModel.isWebSocketConnected ? Theme.profit : Theme.textSecondary)
                        }

                        Spacer()
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 12)
                }

                Divider().background(Color.white.opacity(0.06))

                // Login/Logout button
                Button {
                    if hapticFeedbackEnabled {
                        let impact = UIImpactFeedbackGenerator(style: .medium)
                        impact.impactOccurred()
                    }
                    if viewModel.isUpstoxAuthenticated {
                        viewModel.logoutFromUpstox()
                    } else {
                        viewModel.loginToUpstox()
                    }
                } label: {
                    HStack {
                        Image(systemName: viewModel.isUpstoxAuthenticated ? "rectangle.portrait.and.arrow.right" : "person.badge.key.fill")
                            .font(.system(size: 14))
                        Text(viewModel.isUpstoxAuthenticated ? L.settingsLogoutUpstox : L.upstoxLoginButton)
                            .font(.system(size: 14, weight: .semibold))
                    }
                    .foregroundColor(viewModel.isUpstoxAuthenticated ? Theme.loss : Theme.primaryBlue)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                }
            }
            .background {
                RoundedRectangle(cornerRadius: 12)
                    .fill(Theme.surface)
                    .overlay {
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(Color.white.opacity(0.05), lineWidth: 1)
                    }
            }
        }
    }

    // MARK: - Data Refresh Section

    private var dataRefreshSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(L.settingsDataRefresh)
                .font(.system(size: 12, weight: .bold))
                .foregroundColor(Theme.textMuted)
                .padding(.horizontal, 4)

            VStack(spacing: 0) {
                // Spot price interval
                VStack(spacing: 8) {
                    HStack {
                        Text(L.settingsSpotPriceInterval)
                            .font(.system(size: 14))
                            .foregroundColor(Theme.textPrimary)
                        Spacer()
                        Text(String(format: "%.0fs", spotPriceInterval))
                            .font(.system(size: 14, weight: .bold, design: .monospaced))
                            .foregroundColor(Theme.primaryBlue)
                    }

                    Slider(value: $spotPriceInterval, in: 1...10, step: 1)
                        .tint(Theme.primaryBlue)
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)

                Divider().background(Color.white.opacity(0.06))

                // Option chain refresh interval
                VStack(spacing: 8) {
                    HStack {
                        Text(L.settingsOptionChainRefresh)
                            .font(.system(size: 14))
                            .foregroundColor(Theme.textPrimary)
                        Spacer()
                        Text(String(format: "%.0fs", optionChainInterval))
                            .font(.system(size: 14, weight: .bold, design: .monospaced))
                            .foregroundColor(Theme.primaryBlue)
                    }

                    Slider(value: $optionChainInterval, in: 3...30, step: 1)
                        .tint(Theme.primaryBlue)
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
            }
            .background {
                RoundedRectangle(cornerRadius: 12)
                    .fill(Theme.surface)
                    .overlay {
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(Color.white.opacity(0.05), lineWidth: 1)
                    }
            }
        }
    }

    // MARK: - Default Index Section

    private var defaultIndexSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(L.settingsDefaultIndex)
                .font(.system(size: 12, weight: .bold))
                .foregroundColor(Theme.textMuted)
                .padding(.horizontal, 4)

            VStack(spacing: 0) {
                ForEach(Array(TradingIndex.allCases.enumerated()), id: \.element.id) { index, tradingIndex in
                    if index > 0 {
                        Divider().background(Color.white.opacity(0.06))
                    }

                    Button {
                        if hapticFeedbackEnabled {
                            let impact = UIImpactFeedbackGenerator(style: .light)
                            impact.impactOccurred()
                        }
                        withAnimation(.spring(response: 0.3, dampingFraction: 0.8)) {
                            defaultIndex = tradingIndex.rawValue
                        }
                    } label: {
                        HStack(spacing: 12) {
                            Image(systemName: tradingIndex.icon)
                                .font(.system(size: 16, weight: .semibold))
                                .foregroundColor(Color(hex: tradingIndex.themeColor))
                                .frame(width: 30)

                            Text(tradingIndex.displayName)
                                .font(.system(size: 14, weight: .medium))
                                .foregroundColor(Theme.textPrimary)

                            Spacer()

                            if defaultIndex == tradingIndex.rawValue {
                                Image(systemName: "checkmark.circle.fill")
                                    .font(.system(size: 20))
                                    .foregroundColor(Color(hex: tradingIndex.themeColor))
                                    .transition(.scale.combined(with: .opacity))
                            }
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 12)
                    }
                    .buttonStyle(.plain)
                }
            }
            .background {
                RoundedRectangle(cornerRadius: 12)
                    .fill(Theme.surface)
                    .overlay {
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(Color.white.opacity(0.05), lineWidth: 1)
                    }
            }
        }
    }

    // MARK: - Preferences Section

    private var preferencesSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(L.settingsPreferences)
                .font(.system(size: 12, weight: .bold))
                .foregroundColor(Theme.textMuted)
                .padding(.horizontal, 4)

            VStack(spacing: 0) {
                HStack {
                    Image(systemName: "hand.tap.fill")
                        .font(.system(size: 16))
                        .foregroundColor(Theme.accentPurple)
                        .frame(width: 30)

                    Text(L.settingsHapticFeedback)
                        .font(.system(size: 14))
                        .foregroundColor(Theme.textPrimary)

                    Spacer()

                    Toggle("", isOn: $hapticFeedbackEnabled)
                        .tint(Theme.primaryBlue)
                        .labelsHidden()
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
            }
            .background {
                RoundedRectangle(cornerRadius: 12)
                    .fill(Theme.surface)
                    .overlay {
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(Color.white.opacity(0.05), lineWidth: 1)
                    }
            }
        }
    }

    // MARK: - Smart Alerts Section

    private var smartAlertsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("SMART ALERTS")
                .font(.system(size: 12, weight: .bold))
                .foregroundColor(Theme.textMuted)
                .padding(.horizontal, 4)

            VStack(spacing: 0) {
                // Master toggle
                HStack(spacing: 12) {
                    Image(systemName: "bell.badge.fill")
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundColor(Theme.accentOrange)
                        .frame(width: 30)

                    Text("Enable Alerts")
                        .font(.system(size: 14))
                        .foregroundColor(Theme.textPrimary)

                    Spacer()

                    Toggle("", isOn: Binding(
                        get: { AlertManager.shared.alertsEnabled },
                        set: { AlertManager.shared.alertsEnabled = $0 }
                    ))
                    .tint(Theme.accentOrange)
                    .labelsHidden()
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)

                Divider().background(Color.white.opacity(0.06))

                // Alert Settings link
                NavigationLink(destination: AlertSettingsView()) {
                    HStack(spacing: 12) {
                        Image(systemName: "slider.horizontal.3")
                            .font(.system(size: 16))
                            .foregroundColor(Theme.accentCyan)
                            .frame(width: 30)

                        Text("Alert Settings")
                            .font(.system(size: 14))
                            .foregroundColor(Theme.textPrimary)

                        Spacer()

                        Image(systemName: "chevron.right")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundColor(Theme.textMuted)
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 12)
                }
                .buttonStyle(.plain)

                Divider().background(Color.white.opacity(0.06))

                // Notifications link
                NavigationLink(destination: AlertHistoryView()) {
                    HStack(spacing: 12) {
                        Image(systemName: "bell.fill")
                            .font(.system(size: 16))
                            .foregroundColor(Theme.accentPurple)
                            .frame(width: 30)

                        Text("Notifications")
                            .font(.system(size: 14))
                            .foregroundColor(Theme.textPrimary)

                        Spacer()

                        // Badge for unread count
                        if AlertManager.shared.unreadCount > 0 {
                            Text("\(AlertManager.shared.unreadCount)")
                                .font(.system(size: 11, weight: .bold))
                                .foregroundColor(.white)
                                .frame(minWidth: 20, minHeight: 20)
                                .background(Circle().fill(Theme.accentOrange))
                        }

                        Image(systemName: "chevron.right")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundColor(Theme.textMuted)
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 12)
                }
                .buttonStyle(.plain)
            }
            .background {
                RoundedRectangle(cornerRadius: 12)
                    .fill(Theme.surface)
                    .overlay {
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(Color.white.opacity(0.05), lineWidth: 1)
                    }
            }
        }
    }

    // MARK: - Education Section

    private var educationSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(L.educationTitle)
                .font(.system(size: 12, weight: .bold))
                .foregroundColor(Theme.textMuted)
                .padding(.horizontal, 4)

            VStack(spacing: 12) {
                // Learn Options
                NavigationLink(destination: EducationTabView()) {
                    HStack(spacing: 14) {
                        // Icon
                        Image(systemName: "book.fill")
                            .font(.system(size: 20, weight: .semibold))
                            .foregroundColor(.white)
                            .frame(width: 44, height: 44)
                            .background(
                                LinearGradient(
                                    colors: [Theme.accentPurple, Theme.accentPurple.opacity(0.7)],
                                    startPoint: .topLeading,
                                    endPoint: .bottomTrailing
                                )
                            )
                            .clipShape(RoundedRectangle(cornerRadius: 10))

                        // Content
                        VStack(alignment: .leading, spacing: 4) {
                            Text(L.educationTitle)
                                .font(.system(size: 15, weight: .semibold))
                                .foregroundColor(Theme.textPrimary)

                            Text(L.educationSubtitle)
                                .font(.system(size: 12))
                                .foregroundColor(Theme.textSecondary)
                                .lineLimit(1)
                        }

                        Spacer()

                        Image(systemName: "chevron.right")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(Theme.textMuted)
                    }
                    .padding(14)
                    .background {
                        RoundedRectangle(cornerRadius: 12)
                            .fill(Theme.surface)
                            .overlay {
                                RoundedRectangle(cornerRadius: 12)
                                    .stroke(Color.white.opacity(0.05), lineWidth: 1)
                            }
                    }
                }
                .buttonStyle(.plain)

                // Quiz
                NavigationLink(destination: QuizTabView()) {
                    HStack(spacing: 14) {
                        // Icon
                        Image(systemName: "questionmark.circle.fill")
                            .font(.system(size: 20, weight: .semibold))
                            .foregroundColor(.white)
                            .frame(width: 44, height: 44)
                            .background(
                                LinearGradient(
                                    colors: [Theme.accentOrange, Theme.accentOrange.opacity(0.7)],
                                    startPoint: .topLeading,
                                    endPoint: .bottomTrailing
                                )
                            )
                            .clipShape(RoundedRectangle(cornerRadius: 10))

                        // Content
                        VStack(alignment: .leading, spacing: 4) {
                            Text(L.educationQuiz)
                                .font(.system(size: 15, weight: .semibold))
                                .foregroundColor(Theme.textPrimary)

                            Text(L.educationTestKnowledge)
                                .font(.system(size: 12))
                                .foregroundColor(Theme.textSecondary)
                                .lineLimit(1)
                        }

                        Spacer()

                        Image(systemName: "chevron.right")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(Theme.textMuted)
                    }
                    .padding(14)
                    .background {
                        RoundedRectangle(cornerRadius: 12)
                            .fill(Theme.surface)
                            .overlay {
                                RoundedRectangle(cornerRadius: 12)
                                    .stroke(Color.white.opacity(0.05), lineWidth: 1)
                            }
                    }
                }
                .buttonStyle(.plain)
            }
        }
    }

    // MARK: - Language Section

    private var languageSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(L.settingsLanguage)
                .font(.system(size: 12, weight: .bold))
                .foregroundColor(Theme.textMuted)
                .padding(.horizontal, 4)

            VStack(spacing: 0) {
                ForEach(Array(AppLanguage.allCases.enumerated()), id: \.element.id) { index, language in
                    if index > 0 {
                        Divider().background(Color.white.opacity(0.06))
                    }

                    Button {
                        if hapticFeedbackEnabled {
                            let impact = UIImpactFeedbackGenerator(style: .light)
                            impact.impactOccurred()
                        }
                        withAnimation(.spring(response: 0.3, dampingFraction: 0.8)) {
                            localization.language = language
                        }
                    } label: {
                        HStack(spacing: 12) {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(language.displayName)
                                    .font(.system(size: 15, weight: .medium))
                                    .foregroundColor(Theme.textPrimary)

                                Text(language.englishName)
                                    .font(.system(size: 12))
                                    .foregroundColor(Theme.textSecondary)
                            }

                            Spacer()

                            if localization.language == language {
                                Image(systemName: "checkmark.circle.fill")
                                    .font(.system(size: 20))
                                    .foregroundColor(Theme.profit)
                                    .transition(.scale.combined(with: .opacity))
                            }
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 12)
                        .background(Theme.surface)
                    }
                    .buttonStyle(.plain)
                }
            }
            .background {
                RoundedRectangle(cornerRadius: 12)
                    .fill(Theme.surface)
                    .overlay {
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(Color.white.opacity(0.05), lineWidth: 1)
                    }
            }
        }
    }

    // MARK: - Appearance Section (Light/Dark Mode)

    private var appearanceSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(L.settingsAppearance)
                .font(.system(size: 12, weight: .bold))
                .foregroundColor(Theme.textMuted)
                .padding(.horizontal, 4)

            VStack(spacing: 0) {
                ForEach(Array(AppColorScheme.allCases.enumerated()), id: \.element.id) { index, scheme in
                    if index > 0 {
                        Divider().background(Theme.border.opacity(0.3))
                    }

                    Button {
                        if hapticFeedbackEnabled {
                            let impact = UIImpactFeedbackGenerator(style: .light)
                            impact.impactOccurred()
                        }
                        withAnimation(.spring(response: 0.3, dampingFraction: 0.8)) {
                            themeConfig.colorScheme = scheme
                        }
                    } label: {
                        HStack(spacing: 12) {
                            Image(systemName: scheme.icon)
                                .font(.system(size: 18, weight: .medium))
                                .foregroundColor(themeConfig.colorScheme == scheme ? Theme.primaryBlue : Theme.textSecondary)
                                .frame(width: 30)

                            Text(scheme.displayName)
                                .font(.system(size: 15, weight: .medium))
                                .foregroundColor(Theme.textPrimary)

                            Spacer()

                            if themeConfig.colorScheme == scheme {
                                Image(systemName: "checkmark.circle.fill")
                                    .font(.system(size: 20))
                                    .foregroundColor(Theme.primaryBlue)
                                    .transition(.scale.combined(with: .opacity))
                            }
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 14)
                    }
                    .buttonStyle(.plain)
                }
            }
            .background {
                RoundedRectangle(cornerRadius: 12)
                    .fill(Theme.surface)
                    .overlay {
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(Theme.border.opacity(0.5), lineWidth: 1)
                    }
            }
        }
    }

    // MARK: - Theme Section (existing)

    private var themeSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(L.settingsTheme)
                .font(.system(size: 12, weight: .bold))
                .foregroundColor(Theme.textMuted)
                .padding(.horizontal, 4)

            ForEach(ThemePreset.allCases) { preset in
                ThemePresetCard(
                    preset: preset,
                    isSelected: themeConfig.preset == preset
                ) {
                    withAnimation(.spring(response: 0.3, dampingFraction: 0.8)) {
                        themeConfig.preset = preset
                    }
                }
            }
        }
    }

    // MARK: - About Section (existing + developer attribution)

    private var aboutSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(L.settingsAbout)
                .font(.system(size: 12, weight: .bold))
                .foregroundColor(Theme.textMuted)
                .padding(.horizontal, 4)

            VStack(spacing: 0) {
                SettingsInfoRow(label: L.settingsVersion, value: Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0")
                Divider().background(Color.white.opacity(0.06))
                SettingsInfoRow(label: L.settingsBuild, value: Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "1")
                Divider().background(Color.white.opacity(0.06))
                SettingsInfoRow(label: L.settingsDeveloper, value: "Rishi")
            }
            .background {
                RoundedRectangle(cornerRadius: 12)
                    .fill(Theme.surface)
                    .overlay {
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(Color.white.opacity(0.05), lineWidth: 1)
                    }
            }
        }
    }

    // MARK: - Privacy & AI Data Section (App Store guidelines 5.1.1/5.1.2)

    private var privacySection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("PRIVACY & AI DATA")
                .font(.system(size: 12, weight: .bold))
                .foregroundColor(Theme.textMuted)
                .padding(.horizontal, 4)

            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 8) {
                    Image(systemName: "brain.head.profile")
                        .font(.system(size: 14))
                        .foregroundColor(Theme.accentPurple)
                    Text("AI Analysis Data Disclosure")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundColor(Theme.textPrimary)
                }

                VStack(alignment: .leading, spacing: 8) {
                    Text("When you use AI Insights, the following **market data** is sent to our server for analysis:")
                        .font(.system(size: 12))
                        .foregroundColor(Theme.textSecondary)

                    VStack(alignment: .leading, spacing: 4) {
                        DisclaimerPoint(text: "Option chain data (strike prices, premiums, open interest, volume)")
                        DisclaimerPoint(text: "Market indicators (spot price, PCR, VIX, Greeks)")
                        DisclaimerPoint(text: "Index name and expiry date")
                    }

                    Text("Our server uses **Google Gemini AI** to generate trade suggestions. **No personal data** (name, email, phone, device ID) is sent to the AI service — only anonymized market data.")
                        .font(.system(size: 12))
                        .foregroundColor(Theme.textSecondary)

                    Text("By using AI Insights, you consent to this data processing. You can use the app without AI features at any time.")
                        .font(.system(size: 12, weight: .medium))
                        .foregroundColor(Theme.textPrimary)
                }

                Divider().background(Color.white.opacity(0.06))

                // Privacy Policy link
                Link(destination: URL(string: "https://optix.d23ai.in/privacy")!) {
                    HStack(spacing: 8) {
                        Image(systemName: "lock.shield.fill")
                            .font(.system(size: 14))
                            .foregroundColor(Theme.primaryBlue)
                        Text("Privacy Policy")
                            .font(.system(size: 14, weight: .medium))
                            .foregroundColor(Theme.primaryBlue)
                        Spacer()
                        Image(systemName: "arrow.up.right")
                            .font(.system(size: 12))
                            .foregroundColor(Theme.textMuted)
                    }
                }

                Link(destination: URL(string: "https://optix.d23ai.in/terms")!) {
                    HStack(spacing: 8) {
                        Image(systemName: "doc.text.fill")
                            .font(.system(size: 14))
                            .foregroundColor(Theme.primaryBlue)
                        Text("Terms of Service")
                            .font(.system(size: 14, weight: .medium))
                            .foregroundColor(Theme.primaryBlue)
                        Spacer()
                        Image(systemName: "arrow.up.right")
                            .font(.system(size: 12))
                            .foregroundColor(Theme.textMuted)
                    }
                }
            }
            .padding(16)
            .background {
                RoundedRectangle(cornerRadius: 12)
                    .fill(Theme.surface)
                    .overlay {
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(Theme.accentPurple.opacity(0.2), lineWidth: 1)
                    }
            }
        }
    }

    // MARK: - Account Deletion Section (App Store guideline 5.1.1v)

    private var accountDeletionSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("DANGER ZONE")
                .font(.system(size: 12, weight: .bold))
                .foregroundColor(Theme.loss)
                .padding(.horizontal, 4)

            VStack(spacing: 0) {
                Button {
                    showDeleteConfirmation = true
                } label: {
                    HStack(spacing: 12) {
                        Image(systemName: "trash.fill")
                            .font(.system(size: 16))
                            .foregroundColor(Theme.loss)
                            .frame(width: 30)

                        VStack(alignment: .leading, spacing: 2) {
                            Text("Delete Account")
                                .font(.system(size: 15, weight: .semibold))
                                .foregroundColor(Theme.loss)
                            Text("Permanently delete your account and all data")
                                .font(.system(size: 12))
                                .foregroundColor(Theme.textSecondary)
                        }

                        Spacer()

                        if isDeletingAccount {
                            ProgressView()
                                .tint(Theme.loss)
                        } else {
                            Image(systemName: "chevron.right")
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundColor(Theme.textMuted)
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 14)
                }
                .disabled(isDeletingAccount)
            }
            .background {
                RoundedRectangle(cornerRadius: 12)
                    .fill(Theme.surface)
                    .overlay {
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(Theme.loss.opacity(0.3), lineWidth: 1)
                    }
            }

            if let error = deleteError {
                Text(error)
                    .font(.caption)
                    .foregroundColor(Theme.loss)
                    .padding(.horizontal, 4)
            }
        }
        .alert("Delete Account", isPresented: $showDeleteConfirmation) {
            Button("Cancel", role: .cancel) {}
            Button("Delete Permanently", role: .destructive) {
                Task {
                    isDeletingAccount = true
                    deleteError = nil
                    do {
                        try await authManager.deleteAccount()
                        isDeletingAccount = false
                    } catch {
                        deleteError = error.localizedDescription
                        isDeletingAccount = false
                    }
                }
            }
        } message: {
            Text("This will permanently delete your account, trade journal entries, paper trading history, and all associated data. This action cannot be undone.")
        }
    }

    // MARK: - Disclaimer Section

    private var disclaimerSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(L.settingsDisclaimer)
                .font(.system(size: 12, weight: .bold))
                .foregroundColor(Theme.textMuted)
                .padding(.horizontal, 4)

            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 8) {
                    Image(systemName: "exclamationmark.triangle.fill")
                        .font(.system(size: 14))
                        .foregroundColor(Theme.accentOrange)

                    Text(L.settingsImportantNotice)
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundColor(Theme.textPrimary)
                }

                VStack(alignment: .leading, spacing: 10) {
                    DisclaimerPoint(text: L.settingsDisclaimerBullet1)
                    DisclaimerPoint(text: L.settingsDisclaimerBullet2)
                    DisclaimerPoint(text: L.settingsDisclaimerBullet3)
                    DisclaimerPoint(text: L.settingsDisclaimerBullet4)
                }
            }
            .padding(16)
            .background {
                RoundedRectangle(cornerRadius: 12)
                    .fill(Theme.surface)
                    .overlay {
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(Theme.accentOrange.opacity(0.2), lineWidth: 1)
                    }
            }
        }
    }
}

// MARK: - Theme Preset Card

struct ThemePresetCard: View {
    let preset: ThemePreset
    let isSelected: Bool
    let onSelect: () -> Void

    var body: some View {
        Button(action: {
            let impact = UIImpactFeedbackGenerator(style: .light)
            impact.impactOccurred()
            onSelect()
        }) {
            HStack(spacing: 14) {
                // Color swatches
                HStack(spacing: 4) {
                    RoundedRectangle(cornerRadius: 4)
                        .fill(preset.primaryBlue)
                        .frame(width: 20, height: 32)

                    RoundedRectangle(cornerRadius: 4)
                        .fill(preset.profit)
                        .frame(width: 20, height: 32)

                    RoundedRectangle(cornerRadius: 4)
                        .fill(preset.loss)
                        .frame(width: 20, height: 32)

                    RoundedRectangle(cornerRadius: 4)
                        .fill(preset.background)
                        .frame(width: 20, height: 32)
                        .overlay {
                            RoundedRectangle(cornerRadius: 4)
                                .stroke(Color.white.opacity(0.15), lineWidth: 1)
                        }
                }

                // Name and description
                VStack(alignment: .leading, spacing: 3) {
                    Text(preset.displayName)
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundColor(Theme.textPrimary)

                    Text(preset.description)
                        .font(.system(size: 12))
                        .foregroundColor(Theme.textSecondary)
                }

                Spacer()

                // Checkmark
                if isSelected {
                    Image(systemName: "checkmark.circle.fill")
                        .font(.system(size: 22))
                        .foregroundColor(preset.primaryBlue)
                        .transition(.scale.combined(with: .opacity))
                }
            }
            .padding(14)
            .background {
                RoundedRectangle(cornerRadius: 14)
                    .fill(isSelected ? preset.primaryBlue.opacity(0.08) : Theme.surface)
                    .overlay {
                        RoundedRectangle(cornerRadius: 14)
                            .stroke(
                                isSelected ? preset.primaryBlue.opacity(0.4) : Color.white.opacity(0.05),
                                lineWidth: isSelected ? 1.5 : 1
                            )
                    }
            }
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Info Row

struct SettingsInfoRow: View {
    let label: String
    let value: String

    var body: some View {
        HStack {
            Text(label)
                .font(.system(size: 14))
                .foregroundColor(Theme.textSecondary)
            Spacer()
            Text(value)
                .font(.system(size: 14, weight: .medium, design: .monospaced))
                .foregroundColor(Theme.textPrimary)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
    }
}

// MARK: - Disclaimer Point

struct DisclaimerPoint: View {
    let text: String

    var body: some View {
        HStack(alignment: .top, spacing: 8) {
            Circle()
                .fill(Theme.accentOrange)
                .frame(width: 4, height: 4)
                .padding(.top, 6)

            Text(text)
                .font(.system(size: 12))
                .foregroundColor(Theme.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
        }
    }
}
