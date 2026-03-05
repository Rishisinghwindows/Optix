import SwiftUI

enum ThemePreset: String, CaseIterable, Identifiable {
    case classicGreen = "classicGreen"
    case professionalBlue = "professionalBlue"
    case dhanPremium = "dhanPremium"

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .classicGreen: return L.themeClassicGreen
        case .professionalBlue: return L.themeProfessionalBlue
        case .dhanPremium: return L.themeDhanPremium
        }
    }

    var description: String {
        switch self {
        case .classicGreen: return L.themeClassicGreenActualDesc
        case .professionalBlue: return L.themeProfessionalBlueDesc
        case .dhanPremium: return L.themeDhanPremiumDesc
        }
    }

    // MARK: - Helper to check if dark mode

    private var isDark: Bool {
        ThemeConfiguration.shared.isDarkMode
    }

    // MARK: - Core Background Colors

    var background: Color {
        if isDark {
            switch self {
            case .classicGreen: return Color(hex: "0D0D0D")
            case .professionalBlue: return Color(hex: "121212")
            case .dhanPremium: return Color(hex: "0A0E1A")
            }
        } else {
            switch self {
            case .classicGreen: return Color(hex: "F5F5F5")
            case .professionalBlue: return Color(hex: "F8FAFC")
            case .dhanPremium: return Color(hex: "F0F4F8")
            }
        }
    }

    var surface: Color {
        if isDark {
            switch self {
            case .classicGreen: return Color(hex: "1A1A1A")
            case .professionalBlue: return Color(hex: "1E1E1E")
            case .dhanPremium: return Color(hex: "111827")
            }
        } else {
            switch self {
            case .classicGreen: return Color(hex: "FFFFFF")
            case .professionalBlue: return Color(hex: "FFFFFF")
            case .dhanPremium: return Color(hex: "FFFFFF")
            }
        }
    }

    var surfaceElevated: Color {
        if isDark {
            switch self {
            case .classicGreen: return Color(hex: "242424")
            case .professionalBlue: return Color(hex: "2A2A2A")
            case .dhanPremium: return Color(hex: "1F2937")
            }
        } else {
            switch self {
            case .classicGreen: return Color(hex: "FAFAFA")
            case .professionalBlue: return Color(hex: "F1F5F9")
            case .dhanPremium: return Color(hex: "E8EDF4")
            }
        }
    }

    var card: Color {
        if isDark {
            switch self {
            case .classicGreen: return Color(hex: "2D2D2D")
            case .professionalBlue: return Color(hex: "333333")
            case .dhanPremium: return Color(hex: "283040")
            }
        } else {
            switch self {
            case .classicGreen: return Color(hex: "FFFFFF")
            case .professionalBlue: return Color(hex: "FFFFFF")
            case .dhanPremium: return Color(hex: "FFFFFF")
            }
        }
    }

    var cardHover: Color {
        if isDark {
            switch self {
            case .classicGreen: return Color(hex: "3A3A3A")
            case .professionalBlue: return Color(hex: "404040")
            case .dhanPremium: return Color(hex: "354050")
            }
        } else {
            switch self {
            case .classicGreen: return Color(hex: "F0F0F0")
            case .professionalBlue: return Color(hex: "F1F5F9")
            case .dhanPremium: return Color(hex: "E8EDF4")
            }
        }
    }

    // MARK: - Primary Colors

    var primaryBlue: Color {
        switch self {
        case .classicGreen: return Color(hex: "00C805")
        case .professionalBlue: return Color(hex: "3B82F6")
        case .dhanPremium: return Color(hex: "458BE6")
        }
    }

    var primaryBlueLight: Color {
        switch self {
        case .classicGreen: return Color(hex: "32D74B")
        case .professionalBlue: return Color(hex: "60A5FA")
        case .dhanPremium: return Color(hex: "6BA3F0")
        }
    }

    // MARK: - Semantic Colors

    var profit: Color {
        switch self {
        case .classicGreen: return Color(hex: "00C805")
        case .professionalBlue: return Color(hex: "00B386")
        case .dhanPremium: return Color(hex: "00B386")
        }
    }

    var profitLight: Color {
        switch self {
        case .classicGreen: return Color(hex: "32D74B")
        case .professionalBlue: return Color(hex: "34D399")
        case .dhanPremium: return Color(hex: "34D399")
        }
    }

    var loss: Color { Color(hex: "FF3B30") }
    var lossLight: Color { Color(hex: "FF6961") }

    // MARK: - Accent Colors (same across all themes)

    var accentGreen: Color { profit }
    var accentGreenLight: Color { profitLight }
    var accentRed: Color { Color(hex: "FF3B30") }
    var accentRedLight: Color { Color(hex: "FF6961") }
    var accentBlue: Color { Color(hex: "007AFF") }
    var accentPurple: Color { Color(hex: "BF5AF2") }
    var accentOrange: Color { Color(hex: "FF9F0A") }
    var accentCyan: Color { Color(hex: "64D2FF") }
    var accentYellow: Color { Color(hex: "FFD60A") }
    var accentPink: Color { Color(hex: "FF375F") }

    // MARK: - Text Colors

    var textPrimary: Color {
        isDark ? Color(hex: "FFFFFF") : Color(hex: "1A1A1A")
    }

    var textSecondary: Color {
        isDark ? Color(hex: "A0A0A0") : Color(hex: "6B7280")
    }

    var textMuted: Color {
        isDark ? Color(hex: "6E6E6E") : Color(hex: "9CA3AF")
    }

    var textDisabled: Color {
        isDark ? Color(hex: "404040") : Color(hex: "D1D5DB")
    }

    // MARK: - Border Colors

    var border: Color {
        isDark ? Color(hex: "333333") : Color(hex: "E5E7EB")
    }

    var borderLight: Color {
        isDark ? Color(hex: "404040") : Color(hex: "F3F4F6")
    }

    // MARK: - Shadow

    var shadowColor: Color {
        isDark ? Color.black.opacity(0.3) : Color.black.opacity(0.08)
    }

    // MARK: - Glass Effects

    var glassBorder: LinearGradient {
        if isDark {
            return LinearGradient(
                colors: [Color.white.opacity(0.15), Color.white.opacity(0.05)],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        } else {
            return LinearGradient(
                colors: [Color.black.opacity(0.08), Color.black.opacity(0.02)],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        }
    }

    var glassOverlay: Color {
        isDark ? Color.white.opacity(0.05) : Color.black.opacity(0.02)
    }

    // MARK: - Gradients

    var backgroundGradient: LinearGradient {
        if isDark {
            switch self {
            case .classicGreen:
                return LinearGradient(
                    colors: [Color(hex: "0D0D0D"), Color(hex: "000000")],
                    startPoint: .top,
                    endPoint: .bottom
                )
            case .professionalBlue:
                return LinearGradient(
                    colors: [Color(hex: "121212"), Color(hex: "0A0A0A")],
                    startPoint: .top,
                    endPoint: .bottom
                )
            case .dhanPremium:
                return LinearGradient(
                    colors: [Color(hex: "0A0E1A"), Color(hex: "060810")],
                    startPoint: .top,
                    endPoint: .bottom
                )
            }
        } else {
            switch self {
            case .classicGreen:
                return LinearGradient(
                    colors: [Color(hex: "F5F5F5"), Color(hex: "EBEBEB")],
                    startPoint: .top,
                    endPoint: .bottom
                )
            case .professionalBlue:
                return LinearGradient(
                    colors: [Color(hex: "F8FAFC"), Color(hex: "F1F5F9")],
                    startPoint: .top,
                    endPoint: .bottom
                )
            case .dhanPremium:
                return LinearGradient(
                    colors: [Color(hex: "F0F4F8"), Color(hex: "E8EDF4")],
                    startPoint: .top,
                    endPoint: .bottom
                )
            }
        }
    }

    var profitGradient: LinearGradient {
        LinearGradient(
            colors: [profit, profitLight],
            startPoint: .leading,
            endPoint: .trailing
        )
    }

    var lossGradient: LinearGradient {
        LinearGradient(
            colors: [loss, lossLight],
            startPoint: .leading,
            endPoint: .trailing
        )
    }

    var blueGradient: LinearGradient {
        LinearGradient(
            colors: [primaryBlue, primaryBlueLight],
            startPoint: .leading,
            endPoint: .trailing
        )
    }

    var purpleGradient: LinearGradient {
        LinearGradient(
            colors: [accentPurple, Color(hex: "B388FF")],
            startPoint: .leading,
            endPoint: .trailing
        )
    }

    var orangeGradient: LinearGradient {
        LinearGradient(
            colors: [accentOrange, Color(hex: "FFAB40")],
            startPoint: .leading,
            endPoint: .trailing
        )
    }

    var cardGradient: LinearGradient {
        LinearGradient(
            colors: [surface.opacity(0.9), card.opacity(0.7)],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )
    }

    var borderGradient: LinearGradient {
        if isDark {
            return LinearGradient(
                colors: [Color.white.opacity(0.08), Color.white.opacity(0.02)],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        } else {
            return LinearGradient(
                colors: [Color.black.opacity(0.06), Color.black.opacity(0.02)],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        }
    }

    var shimmerGradient: LinearGradient {
        if isDark {
            return LinearGradient(
                colors: [Color.white.opacity(0.0), Color.white.opacity(0.08), Color.white.opacity(0.0)],
                startPoint: .leading,
                endPoint: .trailing
            )
        } else {
            return LinearGradient(
                colors: [Color.black.opacity(0.0), Color.black.opacity(0.04), Color.black.opacity(0.0)],
                startPoint: .leading,
                endPoint: .trailing
            )
        }
    }

    var premiumBackgroundGradient: RadialGradient {
        RadialGradient(
            colors: [primaryBlue.opacity(isDark ? 0.08 : 0.05), background],
            center: .center,
            startRadius: 0,
            endRadius: 400
        )
    }

    // MARK: - Preview Swatch Colors

    var swatchColors: [Color] {
        [primaryBlue, profit, loss, background, surface]
    }
}
