import Foundation
import SwiftUI

@MainActor
final class IPOViewModel: ObservableObject {
    // MARK: - Published Properties

    @Published var ipos: [IPOItem] = []
    @Published var filteredIPOs: [IPOItem] = []
    @Published var selectedStatus: IPOStatus? = .open
    @Published var selectedType: IPOType? = nil
    @Published var isLoading = false
    @Published var isRefreshing = false
    @Published var error: String?
    @Published var lastUpdated: Date?

    // Analysis
    @Published var analysisCache: [String: IPOAnalysisResponse] = [:]
    @Published var analyzingIPO: String? = nil
    @Published var selectedIPO: IPOItem? = nil
    @Published var showAnalysisSheet = false

    // MARK: - Private Properties

    private let service = IPOService.shared

    // MARK: - Computed Properties

    var openIPOs: [IPOItem] {
        ipos.filter { $0.status == .open }
    }

    var upcomingIPOs: [IPOItem] {
        ipos.filter { $0.status == .upcoming }
    }

    var statusCounts: [IPOStatus: Int] {
        Dictionary(grouping: ipos, by: { $0.status })
            .mapValues { $0.count }
    }

    // MARK: - Init

    init() {
        Task {
            await loadIPOs()
        }
    }

    // MARK: - Public Methods

    func loadIPOs(forceRefresh: Bool = false) async {
        guard !isLoading else { return }

        isLoading = true
        error = nil

        do {
            let response = try await service.getIPOList(forceRefresh: forceRefresh)
            ipos = response.ipos
            lastUpdated = Date()
            applyFilters()
        } catch {
            self.error = error.localizedDescription
        }

        isLoading = false
    }

    func refresh() async {
        isRefreshing = true
        await loadIPOs(forceRefresh: true)
        isRefreshing = false
    }

    func applyFilters() {
        var result = ipos

        if let status = selectedStatus {
            result = result.filter { $0.status == status }
        }

        if let type = selectedType {
            result = result.filter { $0.ipoType == type }
        }

        filteredIPOs = result
    }

    func setStatusFilter(_ status: IPOStatus?) {
        selectedStatus = status
        applyFilters()
    }

    func setTypeFilter(_ type: IPOType?) {
        selectedType = type
        applyFilters()
    }

    func selectIPO(_ ipo: IPOItem) {
        selectedIPO = ipo
    }

    func getAnalysis(for ipo: IPOItem) async {
        guard analysisCache[ipo.slug] == nil else { return }

        analyzingIPO = ipo.slug

        do {
            let analysis = try await service.getAIAnalysis(companyName: ipo.companyName)
            analysisCache[ipo.slug] = analysis
        } catch {
            // Analysis failed - don't show error, just keep nil
            print("Failed to get analysis for \(ipo.companyName): \(error)")
        }

        analyzingIPO = nil
    }

    func refreshAnalysis(for ipo: IPOItem) async {
        analyzingIPO = ipo.slug

        do {
            let analysis = try await service.getAIAnalysis(companyName: ipo.companyName, forceRefresh: true)
            analysisCache[ipo.slug] = analysis
        } catch {
            print("Failed to refresh analysis for \(ipo.companyName): \(error)")
        }

        analyzingIPO = nil
    }
}
