import SwiftUI

// MARK: - Localization Manager

final class LocalizationManager: ObservableObject {
    static let shared = LocalizationManager()

    @Published var language: AppLanguage {
        didSet {
            UserDefaults.standard.set(language.rawValue, forKey: "selectedAppLanguage")
            cachedStrings = language.strings
            version += 1
        }
    }

    @Published var version: Int = 0

    private(set) var cachedStrings: LocalizedStrings

    var strings: LocalizedStrings { cachedStrings }

    private init() {
        let saved = UserDefaults.standard.string(forKey: "selectedAppLanguage") ?? ""
        let lang = AppLanguage(rawValue: saved) ?? .english
        self.language = lang
        self.cachedStrings = lang.strings
    }
}
