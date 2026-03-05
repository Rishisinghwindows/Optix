package com.niftyoption.calculator.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.niftyoption.calculator.data.api.JournalApiService
import com.niftyoption.calculator.data.models.JournalCreateRequest
import com.niftyoption.calculator.data.models.JournalEntry
import com.niftyoption.calculator.data.models.JournalStats
import com.niftyoption.calculator.data.models.JournalUpdateRequest
import com.niftyoption.calculator.data.models.MarketCondition
import com.niftyoption.calculator.data.models.TradeOutcome
import com.niftyoption.calculator.data.models.TradeMood
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// MARK: - Active Tab

enum class JournalTab(val title: String) {
    ENTRIES("Entries"),
    STATS("Stats")
}

// MARK: - Form State

data class JournalFormState(
    val symbol: String = "NIFTY",
    val strikePrice: String = "",
    val optionType: String = "CE",
    val direction: String = "buy",
    val entryPrice: String = "",
    val exitPrice: String = "",
    val quantity: String = "1",
    val lotSize: String = "75",
    val entryDate: String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
    val exitDate: String = "",
    val expiryDate: String = "",
    val title: String = "",
    val notes: String = "",
    val tags: String = "",
    val marketCondition: MarketCondition? = null,
    val mood: TradeMood? = null,
    val outcome: TradeOutcome? = null,
    val editingEntryId: String? = null
) {
    val isEditing: Boolean get() = editingEntryId != null

    fun toCreateRequest(): JournalCreateRequest = JournalCreateRequest(
        symbol = symbol,
        strike_price = strikePrice.toDoubleOrNull() ?: 0.0,
        option_type = optionType,
        direction = direction,
        entry_price = entryPrice.toDoubleOrNull() ?: 0.0,
        exit_price = exitPrice.toDoubleOrNull(),
        quantity = quantity.toIntOrNull() ?: 1,
        lot_size = lotSize.toIntOrNull() ?: 75,
        entry_date = entryDate,
        exit_date = exitDate.ifBlank { null },
        expiry_date = expiryDate.ifBlank { null },
        title = title.ifBlank { null },
        notes = notes.ifBlank { null },
        tags = tags.ifBlank { null },
        market_condition = marketCondition?.apiValue,
        mood = mood?.apiValue,
        outcome = outcome?.apiValue
    )

    fun toUpdateRequest(): JournalUpdateRequest = JournalUpdateRequest(
        exit_price = exitPrice.toDoubleOrNull(),
        exit_date = exitDate.ifBlank { null },
        title = title.ifBlank { null },
        notes = notes.ifBlank { null },
        tags = tags.ifBlank { null },
        market_condition = marketCondition?.apiValue,
        mood = mood?.apiValue,
        outcome = outcome?.apiValue
    )
}

// MARK: - ViewModel

class TradeJournalViewModel : ViewModel() {

    private val apiService = JournalApiService()

    // MARK: - State

    private val _entries = MutableStateFlow<List<JournalEntry>>(emptyList())
    val entries: StateFlow<List<JournalEntry>> = _entries.asStateFlow()

    private val _stats = MutableStateFlow<JournalStats?>(null)
    val stats: StateFlow<JournalStats?> = _stats.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _activeTab = MutableStateFlow(JournalTab.ENTRIES)
    val activeTab: StateFlow<JournalTab> = _activeTab.asStateFlow()

    private val _formState = MutableStateFlow(JournalFormState())
    val formState: StateFlow<JournalFormState> = _formState.asStateFlow()

    private val _showForm = MutableStateFlow(false)
    val showForm: StateFlow<Boolean> = _showForm.asStateFlow()

    private val _filterOutcome = MutableStateFlow<String?>(null)
    val filterOutcome: StateFlow<String?> = _filterOutcome.asStateFlow()

    init {
        loadEntries()
    }

    // MARK: - Tab

    fun setActiveTab(tab: JournalTab) {
        _activeTab.value = tab
        if (tab == JournalTab.STATS) {
            loadStats()
        }
    }

    // MARK: - Load Data

    fun loadEntries() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val response = apiService.getEntries(outcome = _filterOutcome.value)
                _entries.value = response.entries
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load entries"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadStats() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val stats = apiService.getStats()
                _stats.value = stats
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load stats"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // MARK: - Filter

    fun setFilterOutcome(outcome: String?) {
        _filterOutcome.value = outcome
        loadEntries()
    }

    // MARK: - CRUD

    fun createEntry() {
        val form = _formState.value
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                apiService.createEntry(form.toCreateRequest())
                _showForm.value = false
                _formState.value = JournalFormState()
                loadEntries()
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to create entry"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateEntry() {
        val form = _formState.value
        val entryId = form.editingEntryId ?: return
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                apiService.updateEntry(entryId, form.toUpdateRequest())
                _showForm.value = false
                _formState.value = JournalFormState()
                loadEntries()
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to update entry"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteEntry(id: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                apiService.deleteEntry(id)
                loadEntries()
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to delete entry"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // MARK: - Form Management

    fun showAddForm() {
        _formState.value = JournalFormState()
        _showForm.value = true
    }

    fun showEditForm(entry: JournalEntry) {
        _formState.value = JournalFormState(
            symbol = entry.symbol,
            strikePrice = entry.strike_price.toString(),
            optionType = entry.option_type,
            direction = entry.direction,
            entryPrice = entry.entry_price.toString(),
            exitPrice = entry.exit_price?.toString() ?: "",
            quantity = entry.quantity.toString(),
            lotSize = entry.lot_size.toString(),
            entryDate = entry.entry_date,
            exitDate = entry.exit_date ?: "",
            expiryDate = entry.expiry_date ?: "",
            title = entry.title ?: "",
            notes = entry.notes ?: "",
            tags = entry.tags ?: "",
            marketCondition = entry.market_condition?.let { mc ->
                MarketCondition.entries.find { it.apiValue == mc }
            },
            mood = entry.mood?.let { m ->
                TradeMood.entries.find { it.apiValue == m }
            },
            outcome = entry.outcome?.let { o ->
                TradeOutcome.entries.find { it.apiValue == o }
            },
            editingEntryId = entry.id
        )
        _showForm.value = true
    }

    fun dismissForm() {
        _showForm.value = false
        _formState.value = JournalFormState()
    }

    fun updateFormState(update: (JournalFormState) -> JournalFormState) {
        _formState.value = update(_formState.value)
    }

    fun clearError() {
        _error.value = null
    }

    // MARK: - Auth Token

    fun setAuthToken(token: String) {
        apiService.authToken = token
    }
}
