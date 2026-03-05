import SwiftUI

// MARK: - App Color Scheme

enum AppColorScheme: String, CaseIterable, Identifiable {
    case system = "system"
    case light = "light"
    case dark = "dark"

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .system: return L.themeSystemDefault
        case .light: return L.themeLight
        case .dark: return L.themeDark
        }
    }

    var icon: String {
        switch self {
        case .system: return "circle.lefthalf.filled"
        case .light: return "sun.max.fill"
        case .dark: return "moon.fill"
        }
    }

    func colorScheme(for systemScheme: ColorScheme) -> ColorScheme {
        switch self {
        case .system: return systemScheme
        case .light: return .light
        case .dark: return .dark
        }
    }
}

// MARK: - Theme Configuration

final class ThemeConfiguration: ObservableObject {
    static let shared = ThemeConfiguration()

    @Published var preset: ThemePreset {
        didSet {
            UserDefaults.standard.set(preset.rawValue, forKey: "selectedThemePreset")
            version += 1
        }
    }

    @Published var colorScheme: AppColorScheme {
        didSet {
            UserDefaults.standard.set(colorScheme.rawValue, forKey: "appColorScheme")
            // Update isDarkMode immediately for explicit light/dark choices
            switch colorScheme {
            case .light:
                isDarkMode = false
            case .dark:
                isDarkMode = true
            case .system:
                // For system, we need to check actual system scheme
                // This will be updated by the app's onChange handler
                break
            }
            version += 1
        }
    }

    @Published var version: Int = 0

    /// Tracks whether the app is currently displaying in dark mode
    @Published var isDarkMode: Bool = false

    private init() {
        let savedPreset = UserDefaults.standard.string(forKey: "selectedThemePreset") ?? ""
        self.preset = ThemePreset(rawValue: savedPreset) ?? .classicGreen

        let savedScheme = UserDefaults.standard.string(forKey: "appColorScheme") ?? ""
        self.colorScheme = AppColorScheme(rawValue: savedScheme) ?? .light
    }

    /// Update the isDarkMode flag based on system color scheme
    func updateColorScheme(systemScheme: ColorScheme) {
        let effectiveScheme = colorScheme.colorScheme(for: systemScheme)
        isDarkMode = (effectiveScheme == .dark)
        version += 1
    }

    /// Get the preferred ColorScheme for SwiftUI
    func preferredColorScheme(systemScheme: ColorScheme) -> ColorScheme {
        return colorScheme.colorScheme(for: systemScheme)
    }
}
