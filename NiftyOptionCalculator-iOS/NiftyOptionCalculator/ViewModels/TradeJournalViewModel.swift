import Foundation
import SwiftUI
import Combine
import os.log

private let logger = Logger(subsystem: "com.optix.app", category: "TradeJournalVM")

// MARK: - Journal Tab

enum JournalTab: String, CaseIterable {
    case entries = "Entries"
    case stats = "Stats"
}

// MARK: - Trade Journal ViewModel

@MainActor
final class TradeJournalViewModel: ObservableObject {

    // MARK: - Published Properties

    @Published var entries: [JournalEntry] = []
    @Published var stats: JournalStats?
    @Published var isLoading: Bool = false
    @Published var errorMessage: String?
    @Published var showError: Bool = false
    @Published var activeTab: JournalTab = .entries
    @Published var showForm: Bool = false
    @Published var editingEntry: JournalEntry?
    @Published var totalEntries: Int = 0

    // Filter state
    @Published var filterOutcome: TradeOutcome?
    @Published var filterSymbol: String = ""
    @Published var filterTag: JournalTag?

    // MARK: - Form Fields

    @Published var formSymbol: String = "NIFTY"
    @Published var formStrikePrice: String = ""
    @Published var formOptionType: String = "CE"
    @Published var formDirection: String = "buy"
    @Published var formEntryPrice: String = ""
    @Published var formExitPrice: String = ""
    @Published var formQuantity: String = "1"
    @Published var formLotSize: String = "75"
    @Published var formEntryDate: Date = Date()
    @Published var formExitDate: Date = Date()
    @Published var formHasExitDate: Bool = false
    @Published var formExpiryDate: Date = Date()
    @Published var formHasExpiryDate: Bool = true
    @Published var formTitle: String = ""
    @Published var formNotes: String = ""
    @Published var formSelectedTags: Set<JournalTag> = []
    @Published var formMood: TradeMood?
    @Published var formMarketCondition: MarketCondition?
    @Published var formOutcome: TradeOutcome?
    @Published var formPaperPositionId: String?

    // MARK: - Private Properties

    private let apiService = TradeJournalAPIService.shared
    private var cancellables = Set<AnyCancellable>()

    // Available symbols for the picker
    let availableSymbols = ["NIFTY", "BANKNIFTY", "FINNIFTY", "MIDCPNIFTY", "SENSEX", "BANKEX"]

    // Lot sizes per symbol
    let lotSizes: [String: Int] = [
        "NIFTY": 75,
        "BANKNIFTY": 30,
        "FINNIFTY": 25,
        "MIDCPNIFTY": 50,
        "SENSEX": 10,
        "BANKEX": 15
    ]

    // MARK: - Date Formatter

    private let dateFormatter: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd"
        return f
    }()

    // MARK: - Initialization

    init() {
        // Auto-update lot size when symbol changes
        $formSymbol
            .sink { [weak self] symbol in
                if let lotSize = self?.lotSizes[symbol] {
                    self?.formLotSize = "\(lotSize)"
                }
            }
            .store(in: &cancellables)
    }

    // MARK: - Data Loading

    func loadEntries() async {
        guard AuthManager.shared.isLoggedIn else {
            entries = []
            return
        }

        isLoading = true
        defer { isLoading = false }

        do {
            let response = try await apiService.getEntries(
                outcome: filterOutcome?.rawValue,
                symbol: filterSymbol.isEmpty ? nil : filterSymbol,
                tag: filterTag?.rawValue,
                limit: 100
            )
            entries = response.entries
            totalEntries = response.total
            logger.info("Loaded \(response.entries.count) journal entries")
        } catch {
            logger.error("Failed to load entries: \(error.localizedDescription)")
            errorMessage = error.localizedDescription
            showError = true
        }
    }

    func loadStats() async {
        guard AuthManager.shared.isLoggedIn else {
            stats = nil
            return
        }

        isLoading = true
        defer { isLoading = false }

        do {
            stats = try await apiService.getStats()
            logger.info("Loaded journal stats")
        } catch {
            logger.error("Failed to load stats: \(error.localizedDescription)")
            errorMessage = error.localizedDescription
            showError = true
        }
    }

    // MARK: - CRUD Operations

    func createEntry() async {
        guard validateForm() else { return }

        isLoading = true
        defer { isLoading = false }

        let tagsString = formSelectedTags.isEmpty ? nil : formSelectedTags.map(\.rawValue).joined(separator: ",")

        let request = JournalCreateRequest(
            symbol: formSymbol,
            strike_price: Double(formStrikePrice) ?? 0,
            option_type: formOptionType,
            direction: formDirection,
            entry_price: Double(formEntryPrice) ?? 0,
            exit_price: formExitPrice.isEmpty ? nil : Double(formExitPrice),
            quantity: Int(formQuantity) ?? 1,
            lot_size: Int(formLotSize) ?? 75,
            entry_date: dateFormatter.string(from: formEntryDate),
            exit_date: formHasExitDate ? dateFormatter.string(from: formExitDate) : nil,
            expiry_date: formHasExpiryDate ? dateFormatter.string(from: formExpiryDate) : nil,
            title: formTitle.isEmpty ? nil : formTitle,
            notes: formNotes.isEmpty ? nil : formNotes,
            tags: tagsString,
            market_condition: formMarketCondition?.rawValue,
            mood: formMood?.rawValue,
            outcome: formOutcome?.rawValue,
            paper_position_id: formPaperPositionId
        )

        do {
            let entry = try await apiService.createEntry(request)
            entries.insert(entry, at: 0)
            totalEntries += 1
            showForm = false
            resetForm()
            logger.info("Created journal entry: \(entry.id)")
        } catch {
            logger.error("Failed to create entry: \(error.localizedDescription)")
            errorMessage = error.localizedDescription
            showError = true
        }
    }

    func updateEntry() async {
        guard let entry = editingEntry else { return }

        isLoading = true
        defer { isLoading = false }

        let tagsString = formSelectedTags.isEmpty ? nil : formSelectedTags.map(\.rawValue).joined(separator: ",")

        let request = JournalUpdateRequest(
            exit_price: formExitPrice.isEmpty ? nil : Double(formExitPrice),
            exit_date: formHasExitDate ? dateFormatter.string(from: formExitDate) : nil,
            title: formTitle.isEmpty ? nil : formTitle,
            notes: formNotes.isEmpty ? nil : formNotes,
            tags: tagsString,
            market_condition: formMarketCondition?.rawValue,
            mood: formMood?.rawValue,
            outcome: formOutcome?.rawValue
        )

        do {
            let updated = try await apiService.updateEntry(id: entry.id, request)
            if let index = entries.firstIndex(where: { $0.id == entry.id }) {
                entries[index] = updated
            }
            showForm = false
            editingEntry = nil
            resetForm()
            logger.info("Updated journal entry: \(entry.id)")
        } catch {
            logger.error("Failed to update entry: \(error.localizedDescription)")
            errorMessage = error.localizedDescription
            showError = true
        }
    }

    func deleteEntry(_ entry: JournalEntry) async {
        do {
            try await apiService.deleteEntry(id: entry.id)
            entries.removeAll { $0.id == entry.id }
            totalEntries -= 1
            logger.info("Deleted journal entry: \(entry.id)")
        } catch {
            logger.error("Failed to delete entry: \(error.localizedDescription)")
            errorMessage = error.localizedDescription
            showError = true
        }
    }

    // MARK: - Form Helpers

    func resetForm() {
        formSymbol = "NIFTY"
        formStrikePrice = ""
        formOptionType = "CE"
        formDirection = "buy"
        formEntryPrice = ""
        formExitPrice = ""
        formQuantity = "1"
        formLotSize = "75"
        formEntryDate = Date()
        formExitDate = Date()
        formHasExitDate = false
        formExpiryDate = Date()
        formHasExpiryDate = true
        formTitle = ""
        formNotes = ""
        formSelectedTags = []
        formMood = nil
        formMarketCondition = nil
        formOutcome = nil
        formPaperPositionId = nil
        editingEntry = nil
    }

    func populateFormForEditing(_ entry: JournalEntry) {
        editingEntry = entry
        formSymbol = entry.symbol
        formStrikePrice = String(format: "%.0f", entry.strike_price)
        formOptionType = entry.option_type
        formDirection = entry.direction
        formEntryPrice = String(format: "%.2f", entry.entry_price)
        formExitPrice = entry.exit_price.map { String(format: "%.2f", $0) } ?? ""
        formQuantity = "\(entry.quantity)"
        formLotSize = "\(entry.lot_size)"

        // Parse dates
        if let date = dateFormatter.date(from: entry.entry_date) {
            formEntryDate = date
        }
        if let exitDateStr = entry.exit_date, let date = dateFormatter.date(from: exitDateStr) {
            formExitDate = date
            formHasExitDate = true
        } else {
            formHasExitDate = false
        }
        if let expiryStr = entry.expiry_date, let date = dateFormatter.date(from: expiryStr) {
            formExpiryDate = date
            formHasExpiryDate = true
        } else {
            formHasExpiryDate = false
        }

        formTitle = entry.title ?? ""
        formNotes = entry.notes ?? ""

        // Parse tags
        if let tagsStr = entry.tags {
            formSelectedTags = Set(tagsStr.split(separator: ",").compactMap { JournalTag(rawValue: String($0).trimmingCharacters(in: .whitespaces)) })
        } else {
            formSelectedTags = []
        }

        formMood = entry.mood.flatMap { TradeMood(rawValue: $0) }
        formMarketCondition = entry.market_condition.flatMap { MarketCondition(rawValue: $0) }
        formOutcome = entry.outcome.flatMap { TradeOutcome(rawValue: $0) }
        formPaperPositionId = entry.paper_position_id

        showForm = true
    }

    func startNewEntry() {
        resetForm()
        editingEntry = nil
        showForm = true
    }

    // MARK: - Validation

    private func validateForm() -> Bool {
        guard !formStrikePrice.isEmpty, Double(formStrikePrice) != nil else {
            errorMessage = "Please enter a valid strike price"
            showError = true
            return false
        }
        guard !formEntryPrice.isEmpty, Double(formEntryPrice) != nil else {
            errorMessage = "Please enter a valid entry price"
            showError = true
            return false
        }
        guard !formQuantity.isEmpty, let qty = Int(formQuantity), qty > 0 else {
            errorMessage = "Please enter a valid quantity"
            showError = true
            return false
        }
        return true
    }

    // MARK: - Computed Helpers

    /// Compute P&L for an entry that has an exit price
    func computePnL(for entry: JournalEntry) -> Double? {
        guard let exitPrice = entry.exit_price else { return entry.realized_pnl }
        if let pnl = entry.realized_pnl { return pnl }
        let multiplier: Double = entry.direction == "buy" ? 1.0 : -1.0
        return (exitPrice - entry.entry_price) * multiplier * Double(entry.quantity) * Double(entry.lot_size)
    }

    func pnlColor(for entry: JournalEntry) -> Color {
        guard let pnl = computePnL(for: entry) else { return Theme.textMuted }
        if pnl > 0 { return Theme.profit }
        if pnl < 0 { return Theme.loss }
        return Theme.textMuted
    }
}
