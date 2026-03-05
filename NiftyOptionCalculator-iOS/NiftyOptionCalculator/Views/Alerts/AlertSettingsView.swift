import SwiftUI

// MARK: - Alert Settings View

struct AlertSettingsView: View {
    @ObservedObject var alertManager = AlertManager.shared

    @AppStorage("alertSensitivity") private var sensitivityRaw: String = AlertSensitivity.normal.rawValue

    private var sensitivity: AlertSensitivity {
        AlertSensitivity(rawValue: sensitivityRaw) ?? .normal
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 24) {
                // Master toggle
                masterToggleSection

                if alertManager.alertsEnabled {
                    // Sensitivity picker
                    sensitivitySection

                    // Sound toggle
                    soundSection

                    // Per-type toggles
                    alertTypesSection

                    // Test Alert (Debug)
                    #if DEBUG
                    testAlertSection
                    #endif
                }
            }
            .padding(20)
        }
        .background(Theme.background.ignoresSafeArea())
        .navigationTitle("Smart Alerts")
        .navigationBarTitleDisplayMode(.inline)
        .toolbarBackground(Theme.surface, for: .navigationBar)
        .toolbarBackground(.visible, for: .navigationBar)
    }

    // MARK: - Master Toggle

    private var masterToggleSection: some View {
        VStack(spacing: 0) {
            HStack(spacing: 12) {
                Image(systemName: "bell.badge.fill")
                    .font(.system(size: 20, weight: .semibold))
                    .foregroundColor(Theme.accentOrange)
                    .frame(width: 30)

                VStack(alignment: .leading, spacing: 2) {
                    Text("Enable Alerts")
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundColor(Theme.textPrimary)

                    Text("Get notified about unusual market activity")
                        .font(.system(size: 12))
                        .foregroundColor(Theme.textSecondary)
                }

                Spacer()

                Toggle("", isOn: $alertManager.alertsEnabled)
                    .tint(Theme.accentOrange)
                    .labelsHidden()
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
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

    // MARK: - Sensitivity Section

    private var sensitivitySection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("SENSITIVITY")
                .font(.system(size: 12, weight: .bold))
                .foregroundColor(Theme.textMuted)
                .padding(.horizontal, 4)

            VStack(spacing: 0) {
                ForEach(Array(AlertSensitivity.allCases.enumerated()), id: \.element.id) { index, level in
                    if index > 0 {
                        Divider().background(Color.white.opacity(0.06))
                    }

                    Button {
                        withAnimation(.spring(response: 0.3, dampingFraction: 0.8)) {
                            sensitivityRaw = level.rawValue
                        }
                    } label: {
                        HStack(spacing: 12) {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(level.displayName)
                                    .font(.system(size: 14, weight: .medium))
                                    .foregroundColor(Theme.textPrimary)

                                Text(level.description)
                                    .font(.system(size: 11))
                                    .foregroundColor(Theme.textSecondary)
                            }

                            Spacer()

                            if sensitivity == level {
                                Image(systemName: "checkmark.circle.fill")
                                    .font(.system(size: 20))
                                    .foregroundColor(Theme.accentOrange)
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

    // MARK: - Sound Section

    private var soundSection: some View {
        VStack(spacing: 0) {
            HStack(spacing: 12) {
                Image(systemName: alertManager.alertSoundEnabled ? "speaker.wave.2.fill" : "speaker.slash.fill")
                    .font(.system(size: 16))
                    .foregroundColor(Theme.accentCyan)
                    .frame(width: 30)

                Text("Alert Sound")
                    .font(.system(size: 14))
                    .foregroundColor(Theme.textPrimary)

                Spacer()

                Toggle("", isOn: $alertManager.alertSoundEnabled)
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

    // MARK: - Alert Types Section

    private var alertTypesSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("ALERT TYPES")
                .font(.system(size: 12, weight: .bold))
                .foregroundColor(Theme.textMuted)
                .padding(.horizontal, 4)

            VStack(spacing: 0) {
                ForEach(Array(SmartAlertType.allCases.enumerated()), id: \.element.id) { index, alertType in
                    if index > 0 {
                        Divider().background(Color.white.opacity(0.06))
                    }

                    AlertTypeToggleRow(alertType: alertType)
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

    // MARK: - Test Alert (Debug Only)

    #if DEBUG
    private var testAlertSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("DEBUG")
                .font(.system(size: 12, weight: .bold))
                .foregroundColor(Theme.accentRed)
                .padding(.horizontal, 4)

            VStack(spacing: 0) {
                ForEach(Array([
                    ("Volume Spike", SmartAlertType.volumeSpike, AlertSeverity.high),
                    ("OI Surge", SmartAlertType.oiSurge, AlertSeverity.medium),
                    ("S/R Break", SmartAlertType.supportResistanceBreak, AlertSeverity.high),
                    ("PCR Shift", SmartAlertType.pcrShift, AlertSeverity.medium),
                ].enumerated()), id: \.offset) { index, item in
                    if index > 0 {
                        Divider().background(Color.white.opacity(0.06))
                    }

                    Button {
                        let alert = SmartAlert(
                            type: item.1,
                            title: "Test: \(item.0)",
                            message: "This is a test \(item.0.lowercased()) alert to verify the banner and notification system.",
                            strike: 81300,
                            optionType: "CE",
                            severity: item.2,
                            actionHint: "Test action hint"
                        )
                        alertManager.process([alert])
                        print("🔔 [Test] Fired test alert: \(item.0)")
                    } label: {
                        HStack(spacing: 12) {
                            Image(systemName: item.1.icon)
                                .font(.system(size: 16))
                                .foregroundColor(item.1.color)
                                .frame(width: 30)

                            Text("Test \(item.0)")
                                .font(.system(size: 14, weight: .medium))
                                .foregroundColor(Theme.textPrimary)

                            Spacer()

                            BadgeView(text: item.2.label, color: item.2.color, size: .small)

                            Image(systemName: "play.circle.fill")
                                .font(.system(size: 18))
                                .foregroundColor(Theme.accentGreen)
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
                            .stroke(Theme.accentRed.opacity(0.3), lineWidth: 1)
                    }
            }
        }
    }
    #endif
}

// MARK: - Alert Type Toggle Row

private struct AlertTypeToggleRow: View {
    let alertType: SmartAlertType
    @State private var isEnabled: Bool

    init(alertType: SmartAlertType) {
        self.alertType = alertType
        _isEnabled = State(initialValue: AlertManager.shared.isAlertTypeEnabled(alertType))
    }

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: alertType.icon)
                .font(.system(size: 16, weight: .medium))
                .foregroundColor(alertType.color)
                .frame(width: 30)

            VStack(alignment: .leading, spacing: 2) {
                Text(alertType.displayName)
                    .font(.system(size: 14, weight: .medium))
                    .foregroundColor(Theme.textPrimary)

                Text(alertTypeDescription(alertType))
                    .font(.system(size: 11))
                    .foregroundColor(Theme.textSecondary)
            }

            Spacer()

            Toggle("", isOn: $isEnabled)
                .tint(alertType.color)
                .labelsHidden()
                .onChange(of: isEnabled) { _, newValue in
                    AlertManager.shared.setAlertTypeEnabled(alertType, enabled: newValue)
                }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
    }

    private func alertTypeDescription(_ type: SmartAlertType) -> String {
        switch type {
        case .volumeSpike: return "Unusually high trading volume"
        case .oiSurge: return "Rapid open interest changes"
        case .largePremium: return "Big money flow detected"
        case .pcrShift: return "Put-call ratio shift"
        case .vixSpike: return "Volatility index jump"
        case .supportResistanceBreak: return "Key level breakout"
        case .maxPainDrift: return "Expiry convergence shift"
        }
    }
}
