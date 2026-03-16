package com.optix.app.presentation.screens.ipo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

enum class IPOStatus {
    OPEN, UPCOMING, CLOSED, LISTED
}

enum class IPOType {
    MAINBOARD, SME
}

data class IPO(
    val id: String = UUID.randomUUID().toString(),
    val companyName: String,
    val type: IPOType,
    val status: IPOStatus,
    val priceRange: String,
    val issueSize: String,
    val lotSize: Int,
    val openDate: String,
    val closeDate: String,
    val listingDate: String?,
    val gmp: Int?, // Grey Market Premium
    val subscription: Double?, // Subscription times
    val rating: Int, // 1-5
    val sector: String,
    val description: String
)

data class IPOState(
    val ipos: List<IPO> = emptyList(),
    val selectedFilter: IPOStatus? = null,
    val selectedType: IPOType? = null,
    val isLoading: Boolean = true,
    val lastUpdated: Long = System.currentTimeMillis()
)

@HiltViewModel
class IPOViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(IPOState())
    val state: StateFlow<IPOState> = _state.asStateFlow()

    init {
        loadIPOs()
    }

    fun refresh() {
        loadIPOs()
    }

    fun setFilter(status: IPOStatus?) {
        _state.update { it.copy(selectedFilter = status) }
    }

    fun setTypeFilter(type: IPOType?) {
        _state.update { it.copy(selectedType = type) }
    }

    private fun loadIPOs() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            delay(500) // Simulate network delay

            val demoIPOs = listOf(
                IPO(
                    companyName = "Hexaware Technologies",
                    type = IPOType.MAINBOARD,
                    status = IPOStatus.OPEN,
                    priceRange = "674-708",
                    issueSize = "8,750 Cr",
                    lotSize = 21,
                    openDate = "12 Feb 2025",
                    closeDate = "14 Feb 2025",
                    listingDate = "19 Feb 2025",
                    gmp = 45,
                    subscription = 2.5,
                    rating = 4,
                    sector = "IT Services",
                    description = "Leading IT services company with strong global presence"
                ),
                IPO(
                    companyName = "Dr Agarwals Eye Hospital",
                    type = IPOType.MAINBOARD,
                    status = IPOStatus.OPEN,
                    priceRange = "382-402",
                    issueSize = "3,027 Cr",
                    lotSize = 37,
                    openDate = "12 Feb 2025",
                    closeDate = "14 Feb 2025",
                    listingDate = "19 Feb 2025",
                    gmp = 28,
                    subscription = 1.8,
                    rating = 4,
                    sector = "Healthcare",
                    description = "One of India's largest eye care chains"
                ),
                IPO(
                    companyName = "Vikram Solar",
                    type = IPOType.MAINBOARD,
                    status = IPOStatus.UPCOMING,
                    priceRange = "350-380",
                    issueSize = "1,500 Cr",
                    lotSize = 39,
                    openDate = "18 Feb 2025",
                    closeDate = "20 Feb 2025",
                    listingDate = null,
                    gmp = 15,
                    subscription = null,
                    rating = 3,
                    sector = "Solar Energy",
                    description = "Leading solar module manufacturer"
                ),
                IPO(
                    companyName = "Quadrant Televentures",
                    type = IPOType.SME,
                    status = IPOStatus.UPCOMING,
                    priceRange = "130-140",
                    issueSize = "45 Cr",
                    lotSize = 1000,
                    openDate = "17 Feb 2025",
                    closeDate = "19 Feb 2025",
                    listingDate = null,
                    gmp = 8,
                    subscription = null,
                    rating = 2,
                    sector = "Telecom",
                    description = "Telecom infrastructure provider"
                ),
                IPO(
                    companyName = "Zomato",
                    type = IPOType.MAINBOARD,
                    status = IPOStatus.LISTED,
                    priceRange = "72-76",
                    issueSize = "9,375 Cr",
                    lotSize = 195,
                    openDate = "14 Jul 2021",
                    closeDate = "16 Jul 2021",
                    listingDate = "23 Jul 2021",
                    gmp = null,
                    subscription = 38.25,
                    rating = 5,
                    sector = "Food Tech",
                    description = "India's leading food delivery platform"
                ),
                IPO(
                    companyName = "Paytm (One97)",
                    type = IPOType.MAINBOARD,
                    status = IPOStatus.LISTED,
                    priceRange = "2080-2150",
                    issueSize = "18,300 Cr",
                    lotSize = 6,
                    openDate = "8 Nov 2021",
                    closeDate = "10 Nov 2021",
                    listingDate = "18 Nov 2021",
                    gmp = null,
                    subscription = 1.89,
                    rating = 3,
                    sector = "Fintech",
                    description = "Digital payments and financial services"
                ),
                IPO(
                    companyName = "Afcons Infrastructure",
                    type = IPOType.MAINBOARD,
                    status = IPOStatus.CLOSED,
                    priceRange = "440-463",
                    issueSize = "5,430 Cr",
                    lotSize = 32,
                    openDate = "5 Feb 2025",
                    closeDate = "7 Feb 2025",
                    listingDate = "12 Feb 2025",
                    gmp = 35,
                    subscription = 12.5,
                    rating = 4,
                    sector = "Infrastructure",
                    description = "Major infrastructure construction company"
                )
            )

            _state.update {
                it.copy(
                    ipos = demoIPOs,
                    isLoading = false,
                    lastUpdated = System.currentTimeMillis()
                )
            }
        }
    }

    fun getFilteredIPOs(): List<IPO> {
        val currentState = _state.value
        return currentState.ipos.filter { ipo ->
            (currentState.selectedFilter == null || ipo.status == currentState.selectedFilter) &&
            (currentState.selectedType == null || ipo.type == currentState.selectedType)
        }
    }
}
