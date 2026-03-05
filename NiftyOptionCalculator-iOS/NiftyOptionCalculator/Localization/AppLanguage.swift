import Foundation

// MARK: - App Language

enum AppLanguage: String, CaseIterable, Identifiable {
    case english
    case hindi
    case kannada
    case tamil
    case telugu
    case bengali
    case malayalam
    case punjabi
    case odia

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .english: return "English"
        case .hindi: return "\u{0939}\u{093F}\u{0928}\u{094D}\u{0926}\u{0940}"
        case .kannada: return "\u{0C95}\u{0CA8}\u{0CCD}\u{0CA8}\u{0CA1}"
        case .tamil: return "\u{0BA4}\u{0BAE}\u{0BBF}\u{0BB4}\u{0BCD}"
        case .telugu: return "\u{0C24}\u{0C46}\u{0C32}\u{0C41}\u{0C17}\u{0C41}"
        case .bengali: return "\u{09AC}\u{09BE}\u{0982}\u{09B2}\u{09BE}"
        case .malayalam: return "\u{0D2E}\u{0D32}\u{0D2F}\u{0D3E}\u{0D33}\u{0D02}"
        case .punjabi: return "\u{0A2A}\u{0A70}\u{0A1C}\u{0A3E}\u{0A2C}\u{0A40}"
        case .odia: return "\u{0B13}\u{0B21}\u{0B3F}\u{0B06}"
        }
    }

    var englishName: String {
        switch self {
        case .english: return "English"
        case .hindi: return "Hindi"
        case .kannada: return "Kannada"
        case .tamil: return "Tamil"
        case .telugu: return "Telugu"
        case .bengali: return "Bengali"
        case .malayalam: return "Malayalam"
        case .punjabi: return "Punjabi"
        case .odia: return "Odia"
        }
    }

    var strings: LocalizedStrings {
        switch self {
        case .english: return EnglishStrings()
        case .hindi: return HindiStrings()
        case .kannada: return KannadaStrings()
        case .tamil: return TamilStrings()
        case .telugu: return TeluguStrings()
        case .bengali: return BengaliStrings()
        case .malayalam: return MalayalamStrings()
        case .punjabi: return PunjabiStrings()
        case .odia: return OdiaStrings()
        }
    }
}
