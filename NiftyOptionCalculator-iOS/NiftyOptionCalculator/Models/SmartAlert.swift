import Foundation
import SwiftUI

// MARK: - Smart Alert Type

enum SmartAlertType: String, Codable, CaseIterable, Identifiable {
    case volumeSpike
    case oiSurge
    case largePremium
    case pcrShift
    case vixSpike
    case supportResistanceBreak
    case maxPainDrift

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .volumeSpike: return "Volume Spike"
        case .oiSurge: return "OI Surge"
        case .largePremium: return "Large Premium"
        case .pcrShift: return "PCR Shift"
        case .vixSpike: return "VIX Spike"
        case .supportResistanceBreak: return "S/R Break"
        case .maxPainDrift: return "Max Pain Drift"
        }
    }

    var icon: String {
        switch self {
        case .volumeSpike: return "chart.bar.fill"
        case .oiSurge: return "arrow.up.right.circle.fill"
        case .largePremium: return "indianrupeesign.circle.fill"
        case .pcrShift: return "arrow.left.arrow.right.circle.fill"
        case .vixSpike: return "waveform.path.ecg"
        case .supportResistanceBreak: return "arrow.up.arrow.down.circle.fill"
        case .maxPainDrift: return "target"
        }
    }

    var color: Color {
        switch self {
        case .volumeSpike: return Theme.accentCyan
        case .oiSurge: return Theme.accentPurple
        case .largePremium: return Theme.accentOrange
        case .pcrShift: return Theme.accentBlue
        case .vixSpike: return Theme.accentRed
        case .supportResistanceBreak: return Theme.accentYellow
        case .maxPainDrift: return Theme.accentPink
        }
    }

    /// Cooldown in seconds before another alert of the same type can fire
    var cooldownDuration: TimeInterval {
        switch self {
        case .volumeSpike: return 120
        case .oiSurge: return 180
        case .largePremium: return 120
        case .pcrShift: return 300
        case .vixSpike: return 300
        case .supportResistanceBreak: return 600
        case .maxPainDrift: return 300
        }
    }

    /// Display priority (higher = shown first)
    var displayPriority: Int {
        switch self {
        case .supportResistanceBreak: return 100
        case .vixSpike: return 90
        case .volumeSpike: return 80
        case .oiSurge: return 70
        case .largePremium: return 60
        case .pcrShift: return 50
        case .maxPainDrift: return 40
        }
    }

    var settingsKey: String {
        "alert_\(rawValue)_enabled"
    }
}

// MARK: - Alert Severity

enum AlertSeverity: String, Codable, Comparable {
    case low
    case medium
    case high

    static func < (lhs: AlertSeverity, rhs: AlertSeverity) -> Bool {
        lhs.numericValue < rhs.numericValue
    }

    private var numericValue: Int {
        switch self {
        case .low: return 0
        case .medium: return 1
        case .high: return 2
        }
    }

    var color: Color {
        switch self {
        case .low: return Theme.accentCyan
        case .medium: return Theme.accentOrange
        case .high: return Theme.accentRed
        }
    }

    var label: String {
        switch self {
        case .low: return "LOW"
        case .medium: return "MED"
        case .high: return "HIGH"
        }
    }
}

// MARK: - Alert Sensitivity

enum AlertSensitivity: String, CaseIterable, Identifiable {
    case conservative
    case normal
    case aggressive

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .conservative: return "Conservative"
        case .normal: return "Normal"
        case .aggressive: return "Aggressive"
        }
    }

    var description: String {
        switch self {
        case .conservative: return "Fewer alerts, only major events"
        case .normal: return "Balanced alert frequency"
        case .aggressive: return "More alerts, catch smaller moves"
        }
    }

    /// Multiplier applied to detection thresholds (higher = harder to trigger)
    var thresholdMultiplier: Double {
        switch self {
        case .conservative: return 1.5
        case .normal: return 1.0
        case .aggressive: return 0.7
        }
    }
}

// MARK: - Smart Alert

struct SmartAlert: Identifiable, Codable, Equatable {
    let id: UUID
    let type: SmartAlertType
    let title: String
    let message: String
    let strike: Double?
    let optionType: String? // "CE" or "PE"
    let severity: AlertSeverity
    let timestamp: Date
    let actionHint: String?
    var isRead: Bool

    init(
        type: SmartAlertType,
        title: String,
        message: String,
        strike: Double? = nil,
        optionType: String? = nil,
        severity: AlertSeverity,
        actionHint: String? = nil
    ) {
        self.id = UUID()
        self.type = type
        self.title = title
        self.message = message
        self.strike = strike
        self.optionType = optionType
        self.severity = severity
        self.timestamp = Date()
        self.actionHint = actionHint
        self.isRead = false
    }

    /// Composite dedup key
    var dedupKey: String {
        "\(type.rawValue)_\(strike.map { String(format: "%.0f", $0) } ?? "nil")_\(optionType ?? "")"
    }

    static func == (lhs: SmartAlert, rhs: SmartAlert) -> Bool {
        lhs.id == rhs.id
    }
}
