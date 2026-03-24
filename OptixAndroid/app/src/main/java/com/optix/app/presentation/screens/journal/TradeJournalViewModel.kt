package com.optix.app.presentation.screens.journal

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.optix.app.core.util.Resource
import com.optix.app.domain.model.*
import com.optix.app.domain.repository.AuthRepository
import com.optix.app.domain.repository.JournalRepository
import com.optix.app.presentation.theme.Loss
import com.optix.app.presentation.theme.Profit
import com.optix.app.presentation.theme.TextMutedDark
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlin.math.abs

// ============== UI State ==============

enum class JournalTab(val title: String) {
    ENTRIES("Entries"),
    STATS("Stats")
}

data class TradeJournalState(
    // Auth
    val isLoggedIn: Boolean = false,

    // Tab
    val activeTab: JournalTab = JournalTab.ENTRIES,

    // Entries
    val entries: List<JournalEntry> = emptyList(),
    val totalEntries: Int = 0,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,

    // Stats
    val stats: JournalStats? = null,

    // Filters
    val filterOutcome: TradeOutcome? = null,
    val filterSymbol: String = "",
    val filterTag: JournalTag? = null,

    // Form visibility
    val showForm: Boolean = false,
    val editingEntry: JournalEntry? = null,

    // Delete confirmation
    val entryToDelete: JournalEntry? = null,

    // Form fields
    val formSymbol: String = "NIFTY",
    val formStrikePrice: String = "",
    val formOptionType: String = "CE",
    val formDirection: String = "buy",
    val formEntryPrice: String = "",
    val formExitPrice: String = "",
    val formQuantity: String = "1",
    val formLotSize: String = "75",
    val formEntryDate: Date = Date(),
    val formExitDate: Date = Date(),
    val formHasExitDate: Boolean = false,
    val formExpiryDate: Date = Date(),
    val formHasExpiryDate: Boolean = true,
    val formTitle: String = "",
    val formNotes: String = "",
    val formSelectedTags: Set<JournalTag> = emptySet(),
    val formMood: TradeMood? = null,
    val formMarketCondition: MarketCondition? = null,
    val formOutcome: TradeOutcome? = null
)

@HiltViewModel
class TradeJournalViewModel @Inject constructor(
    private val repository: JournalRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    companion object {
        val AVAILABLE_SYMBOLS = listOf("NIFTY", "BANKNIFTY", "FINNIFTY", "MIDCPNIFTY", "SENSEX", "BANKEX")
        val LOT_SIZES = mapOf(
            "NIFTY" to 75,
            "BANKNIFTY" to 30,
            "FINNIFTY" to 25,
            "MIDCPNIFTY" to 50,
            "SENSEX" to 10,
            "BANKEX" to 15
        )
    }

    private val _state = MutableStateFlow(TradeJournalState())
    val state: StateFlow<TradeJournalState> = _state.asStateFlow()

    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    init {
        observeAuthState()
    }

    private fun observeAuthState() {
        viewModelScope.launch {
            authRepository.authState.collect { authState ->
                val isLoggedIn = authState is AuthState.Authenticated
                _state.update { it.copy(isLoggedIn = isLoggedIn) }
                if (isLoggedIn) {
                    loadEntries()
                    loadStats()
                } else {
                    _state.update {
                        TradeJournalState(isLoggedIn = false)
                    }
                }
            }
        }
    }

    // ============== Data Loading ==============

    fun loadEntries() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            val currentState = _state.value
            when (val result = repository.getEntries(
                outcome = currentState.filterOutcome?.apiValue,
                symbol = currentState.filterSymbol.ifEmpty { null },
                tag = currentState.filterTag?.apiValue,
                limit = 100
            )) {
                is Resource.Success -> {
                    val (entries, total) = result.data!!
                    _state.update {
                        it.copy(
                            entries = entries,
                            totalEntries = total,
                            isLoading = false
                        )
                    }
                }
                is Resource.Error -> {
                    _state.update {
                        it.copy(
                            errorMessage = result.message,
                            isLoading = false
                        )
                    }
                }
                is Resource.Loading -> {}
            }
        }
    }

    fun loadStats() {
        viewModelScope.launch {
            when (val result = repository.getStats()) {
                is Resource.Success -> {
                    _state.update { it.copy(stats = result.data) }
                }
                is Resource.Error -> {
                    _state.update { it.copy(errorMessage = result.message) }
                }
                is Resource.Loading -> {}
            }
        }
    }

    // ============== Tab ==============

    fun setActiveTab(tab: JournalTab) {
        _state.update { it.copy(activeTab = tab) }
    }

    // ============== Filters ==============

    fun setFilterOutcome(outcome: TradeOutcome?) {
        _state.update { it.copy(filterOutcome = outcome) }
        loadEntries()
    }

    fun setFilterSymbol(symbol: String) {
        _state.update { it.copy(filterSymbol = symbol) }
        loadEntries()
    }

    fun setFilterTag(tag: JournalTag?) {
        _state.update { it.copy(filterTag = tag) }
        loadEntries()
    }

    fun clearFilters() {
        _state.update {
            it.copy(filterOutcome = null, filterSymbol = "", filterTag = null)
        }
        loadEntries()
    }

    // ============== Form ==============

    fun startNewEntry() {
        _state.update {
            it.copy(
                showForm = true,
                editingEntry = null,
                formSymbol = "NIFTY",
                formStrikePrice = "",
                formOptionType = "CE",
                formDirection = "buy",
                formEntryPrice = "",
                formExitPrice = "",
                formQuantity = "1",
                formLotSize = "75",
                formEntryDate = Date(),
                formExitDate = Date(),
                formHasExitDate = false,
                formExpiryDate = Date(),
                formHasExpiryDate = true,
                formTitle = "",
                formNotes = "",
                formSelectedTags = emptySet(),
                formMood = null,
                formMarketCondition = null,
                formOutcome = null
            )
        }
    }

    fun startEditEntry(entry: JournalEntry) {
        _state.update {
            it.copy(
                showForm = true,
                editingEntry = entry,
                formSymbol = entry.symbol,
                formStrikePrice = String.format("%.0f", entry.strikePrice),
                formOptionType = entry.optionType,
                formDirection = entry.direction,
                formEntryPrice = String.format("%.2f", entry.entryPrice),
                formExitPrice = entry.exitPrice?.let { p -> String.format("%.2f", p) } ?: "",
                formQuantity = entry.quantity.toString(),
                formLotSize = entry.lotSize.toString(),
                formEntryDate = parseDate(entry.entryDate) ?: Date(),
                formExitDate = entry.exitDate?.let { parseDate(it) } ?: Date(),
                formHasExitDate = entry.exitDate != null,
                formExpiryDate = entry.expiryDate?.let { parseDate(it) } ?: Date(),
                formHasExpiryDate = entry.expiryDate != null,
                formTitle = entry.title ?: "",
                formNotes = entry.notes ?: "",
                formSelectedTags = entry.tags?.split(",")
                    ?.mapNotNull { JournalTag.fromApiValue(it.trim()) }
                    ?.toSet() ?: emptySet(),
                formMood = entry.mood?.let { TradeMood.fromApiValue(it) },
                formMarketCondition = entry.marketCondition?.let { MarketCondition.fromApiValue(it) },
                formOutcome = entry.outcome?.let { TradeOutcome.fromApiValue(it) }
            )
        }
    }

    fun dismissForm() {
        _state.update { it.copy(showForm = false, editingEntry = null) }
    }

    // Form field updaters
    fun updateFormSymbol(value: String) {
        val lotSize = LOT_SIZES[value] ?: 75
        _state.update { it.copy(formSymbol = value, formLotSize = lotSize.toString()) }
    }

    fun updateFormStrikePrice(value: String) = _state.update { it.copy(formStrikePrice = value) }
    fun updateFormOptionType(value: String) = _state.update { it.copy(formOptionType = value) }
    fun updateFormDirection(value: String) = _state.update { it.copy(formDirection = value) }
    fun updateFormEntryPrice(value: String) = _state.update { it.copy(formEntryPrice = value) }
    fun updateFormExitPrice(value: String) = _state.update { it.copy(formExitPrice = value) }
    fun updateFormQuantity(value: String) = _state.update { it.copy(formQuantity = value) }
    fun updateFormLotSize(value: String) = _state.update { it.copy(formLotSize = value) }
    fun updateFormEntryDate(value: Date) = _state.update { it.copy(formEntryDate = value) }
    fun updateFormExitDate(value: Date) = _state.update { it.copy(formExitDate = value) }
    fun updateFormHasExitDate(value: Boolean) = _state.update { it.copy(formHasExitDate = value) }
    fun updateFormTitle(value: String) = _state.update { it.copy(formTitle = value) }
    fun updateFormNotes(value: String) = _state.update { it.copy(formNotes = value) }
    fun updateFormMood(value: TradeMood?) = _state.update { it.copy(formMood = value) }
    fun updateFormMarketCondition(value: MarketCondition?) = _state.update { it.copy(formMarketCondition = value) }
    fun updateFormOutcome(value: TradeOutcome?) = _state.update { it.copy(formOutcome = value) }

    fun toggleFormTag(tag: JournalTag) {
        _state.update {
            val newTags = it.formSelectedTags.toMutableSet()
            if (newTags.contains(tag)) newTags.remove(tag) else newTags.add(tag)
            it.copy(formSelectedTags = newTags)
        }
    }

    // ============== CRUD ==============

    fun saveEntry() {
        val s = _state.value
        // Validate
        if (s.formStrikePrice.isEmpty() || s.formStrikePrice.toDoubleOrNull() == null) {
            _state.update { it.copy(errorMessage = "Please enter a valid strike price") }
            return
        }
        if (s.formEntryPrice.isEmpty() || s.formEntryPrice.toDoubleOrNull() == null) {
            _state.update { it.copy(errorMessage = "Please enter a valid entry price") }
            return
        }
        if (s.formQuantity.isEmpty() || (s.formQuantity.toIntOrNull() ?: 0) <= 0) {
            _state.update { it.copy(errorMessage = "Please enter a valid quantity") }
            return
        }

        val tagsString = s.formSelectedTags.takeIf { it.isNotEmpty() }
            ?.joinToString(",") { it.apiValue }

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }

            if (s.editingEntry != null) {
                // Update
                when (val result = repository.updateEntry(
                    id = s.editingEntry.id,
                    exitPrice = s.formExitPrice.toDoubleOrNull(),
                    exitDate = if (s.formHasExitDate) dateFormatter.format(s.formExitDate) else null,
                    title = s.formTitle.ifEmpty { null },
                    notes = s.formNotes.ifEmpty { null },
                    tags = tagsString,
                    marketCondition = s.formMarketCondition?.apiValue,
                    mood = s.formMood?.apiValue,
                    outcome = s.formOutcome?.apiValue
                )) {
                    is Resource.Success -> {
                        val updated = result.data!!
                        _state.update { state ->
                            state.copy(
                                entries = state.entries.map { if (it.id == updated.id) updated else it },
                                showForm = false,
                                editingEntry = null,
                                isLoading = false
                            )
                        }
                    }
                    is Resource.Error -> {
                        _state.update { it.copy(errorMessage = result.message, isLoading = false) }
                    }
                    is Resource.Loading -> {}
                }
            } else {
                // Create
                when (val result = repository.createEntry(
                    symbol = s.formSymbol,
                    strikePrice = s.formStrikePrice.toDouble(),
                    optionType = s.formOptionType,
                    direction = s.formDirection,
                    entryPrice = s.formEntryPrice.toDouble(),
                    exitPrice = s.formExitPrice.toDoubleOrNull(),
                    quantity = s.formQuantity.toInt(),
                    lotSize = s.formLotSize.toIntOrNull() ?: 75,
                    entryDate = dateFormatter.format(s.formEntryDate),
                    exitDate = if (s.formHasExitDate) dateFormatter.format(s.formExitDate) else null,
                    expiryDate = if (s.formHasExpiryDate) dateFormatter.format(s.formExpiryDate) else null,
                    title = s.formTitle.ifEmpty { null },
                    notes = s.formNotes.ifEmpty { null },
                    tags = tagsString,
                    marketCondition = s.formMarketCondition?.apiValue,
                    mood = s.formMood?.apiValue,
                    outcome = s.formOutcome?.apiValue,
                    paperPositionId = null
                )) {
                    is Resource.Success -> {
                        val created = result.data!!
                        _state.update { state ->
                            state.copy(
                                entries = listOf(created) + state.entries,
                                totalEntries = state.totalEntries + 1,
                                showForm = false,
                                isLoading = false
                            )
                        }
                    }
                    is Resource.Error -> {
                        _state.update { it.copy(errorMessage = result.message, isLoading = false) }
                    }
                    is Resource.Loading -> {}
                }
            }
        }
    }

    fun requestDeleteEntry(entry: JournalEntry) {
        _state.update { it.copy(entryToDelete = entry) }
    }

    fun dismissDeleteConfirmation() {
        _state.update { it.copy(entryToDelete = null) }
    }

    fun confirmDeleteEntry() {
        val entry = _state.value.entryToDelete ?: return
        viewModelScope.launch {
            when (val result = repository.deleteEntry(entry.id)) {
                is Resource.Success -> {
                    _state.update { state ->
                        state.copy(
                            entries = state.entries.filter { it.id != entry.id },
                            totalEntries = state.totalEntries - 1,
                            entryToDelete = null
                        )
                    }
                }
                is Resource.Error -> {
                    _state.update { it.copy(errorMessage = result.message, entryToDelete = null) }
                }
                is Resource.Loading -> {}
            }
        }
    }

    fun dismissError() {
        _state.update { it.copy(errorMessage = null) }
    }

    // ============== Computed Helpers ==============

    fun computePnl(entry: JournalEntry): Double? {
        if (entry.realizedPnl != null) return entry.realizedPnl
        val exitPrice = entry.exitPrice ?: return null
        val multiplier = if (entry.direction == "buy") 1.0 else -1.0
        return (exitPrice - entry.entryPrice) * multiplier * entry.quantity * entry.lotSize
    }

    fun pnlColor(entry: JournalEntry): Color {
        val pnl = computePnl(entry) ?: return TextMutedDark
        return when {
            pnl > 0 -> Profit
            pnl < 0 -> Loss
            else -> TextMutedDark
        }
    }

    private fun parseDate(dateString: String): Date? {
        return try {
            dateFormatter.parse(dateString)
        } catch (e: Exception) {
            // Try ISO format
            try {
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).parse(dateString)
            } catch (e2: Exception) {
                null
            }
        }
    }
}

/**
 * Format currency in Indian style
 */
fun formatIndianCurrency(value: Double): String {
    val absValue = abs(value)
    val sign = if (value < 0) "-" else if (value > 0) "+" else ""
    if (value == 0.0) return "\u20B90"
    return when {
        absValue >= 1_00_00_000 -> "$sign\u20B9${String.format("%.2f", absValue / 1_00_00_000)} Cr"
        absValue >= 1_00_000 -> "$sign\u20B9${String.format("%.2f", absValue / 1_00_000)}L"
        else -> "$sign\u20B9${String.format("%.0f", absValue)}"
    }
}
