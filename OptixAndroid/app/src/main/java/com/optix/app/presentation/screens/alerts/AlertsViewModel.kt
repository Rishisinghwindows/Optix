package com.optix.app.presentation.screens.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.optix.app.core.util.Resource
import com.optix.app.domain.model.Alert
import com.optix.app.domain.repository.AlertRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AlertsState(
    val alertsState: Resource<List<Alert>> = Resource.Loading(),
    val activeCount: Int = 0,
    val triggeredCount: Int = 0,
    val ipoState: IPOState = IPOState()
)

@HiltViewModel
class AlertsViewModel @Inject constructor(
    private val repository: AlertRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AlertsState())
    val state: StateFlow<AlertsState> = _state.asStateFlow()

    init {
        loadAlerts()
        loadIPOs()
    }

    fun refresh() {
        loadAlerts()
    }

    fun refreshIPOs() {
        loadIPOs()
    }

    fun setIPOFilter(status: IPOStatus?) {
        _state.update {
            it.copy(ipoState = it.ipoState.copy(selectedStatus = status))
        }
    }

    fun setIPOTypeFilter(type: IPOType?) {
        _state.update {
            it.copy(ipoState = it.ipoState.copy(selectedType = type))
        }
    }

    fun setIPOSearch(query: String) {
        _state.update {
            it.copy(ipoState = it.ipoState.copy(searchQuery = query))
        }
    }

    fun selectIPO(ipo: IPO) {
        _state.update {
            it.copy(ipoState = it.ipoState.copy(
                selectedIPO = ipo,
                showDetailSheet = true
            ))
        }
    }

    fun dismissIPODetail() {
        _state.update {
            it.copy(ipoState = it.ipoState.copy(
                showDetailSheet = false,
                selectedIPO = null
            ))
        }
    }

    fun analyzeIPO(ipo: IPO) {
        viewModelScope.launch {
            _state.update {
                it.copy(ipoState = it.ipoState.copy(analyzingIPO = ipo.id))
            }

            // Simulate AI analysis delay
            delay(2000)

            // Generate AI analysis result
            val verdict = when {
                (ipo.gmp?.listingGainPct ?: 0.0) > 15 -> IPOVerdict.SUBSCRIBE
                (ipo.gmp?.listingGainPct ?: 0.0) < 0 -> IPOVerdict.AVOID
                else -> IPOVerdict.NEUTRAL
            }

            val analysis = when (verdict) {
                IPOVerdict.SUBSCRIBE -> "Strong fundamentals with healthy GMP indicating positive market sentiment. The company has good growth prospects and reasonable valuations."
                IPOVerdict.AVOID -> "Weak market sentiment reflected in negative GMP. Consider waiting for better entry points post-listing."
                IPOVerdict.NEUTRAL -> "Mixed signals with moderate GMP. Consider your risk appetite and investment horizon before applying."
            }

            val keyPositives = listOf(
                "Strong brand presence in ${ipo.sector}",
                "Consistent revenue growth over last 3 years",
                "Experienced management team",
                "Healthy profit margins"
            )

            val keyRisks = listOf(
                "High competition in the sector",
                "Dependence on key customers",
                "Regulatory risks",
                "Market volatility"
            )

            // Update the IPO with analysis
            val updatedIPO = ipo.copy(
                aiVerdict = verdict,
                aiAnalysis = analysis,
                keyPositives = keyPositives,
                keyRisks = keyRisks
            )

            // Update the IPO in the list
            val updatedIPOs = _state.value.ipoState.ipos.map {
                if (it.id == ipo.id) updatedIPO else it
            }

            _state.update {
                it.copy(ipoState = it.ipoState.copy(
                    ipos = updatedIPOs,
                    analyzingIPO = null,
                    selectedIPO = if (it.ipoState.selectedIPO?.id == ipo.id) updatedIPO else it.ipoState.selectedIPO
                ))
            }
        }
    }

    private fun loadIPOs() {
        viewModelScope.launch {
            _state.update {
                it.copy(ipoState = it.ipoState.copy(isLoading = true))
            }

            // Simulate network delay
            delay(500)

            // Demo IPO data with GMP
            val demoIPOs = listOf(
                IPO(
                    companyName = "Hexaware Technologies Ltd",
                    type = IPOType.MAINBOARD,
                    status = IPOStatus.OPEN,
                    priceBandLow = 674.0,
                    priceBandHigh = 708.0,
                    lotSize = 21,
                    issueSizeCr = "₹8,750 Cr",
                    minInvestment = 14868.0,
                    exchange = "NSE",
                    openDate = "Feb 12",
                    closeDate = "Feb 14",
                    listingDate = "Feb 19",
                    gmp = GMPData(
                        gmpValue = 85.0,
                        estimatedListingPrice = 793.0,
                        listingGainPct = 12.0
                    ),
                    sector = "Information Technology",
                    description = "Global IT services company specializing in digital transformation, automation, and cloud solutions for enterprises."
                ),
                IPO(
                    companyName = "Dr Agarwal's Health Care Ltd",
                    type = IPOType.MAINBOARD,
                    status = IPOStatus.OPEN,
                    priceBandLow = 382.0,
                    priceBandHigh = 402.0,
                    lotSize = 37,
                    issueSizeCr = "₹3,027 Cr",
                    minInvestment = 14874.0,
                    exchange = "NSE",
                    openDate = "Feb 12",
                    closeDate = "Feb 14",
                    listingDate = "Feb 19",
                    gmp = GMPData(
                        gmpValue = 45.0,
                        estimatedListingPrice = 447.0,
                        listingGainPct = 11.2
                    ),
                    sector = "Healthcare",
                    description = "Leading eye care hospital chain in India with pan-India presence and comprehensive ophthalmic services."
                ),
                IPO(
                    companyName = "Quality Power Electrical Equipments Ltd",
                    type = IPOType.SME,
                    status = IPOStatus.UPCOMING,
                    priceBandLow = 401.0,
                    priceBandHigh = 425.0,
                    lotSize = 35,
                    issueSizeCr = "₹858 Cr",
                    minInvestment = 14875.0,
                    exchange = "NSE SME",
                    openDate = "Feb 17",
                    closeDate = "Feb 19",
                    listingDate = "Feb 24",
                    gmp = GMPData(
                        gmpValue = 120.0,
                        estimatedListingPrice = 545.0,
                        listingGainPct = 28.2
                    ),
                    sector = "Manufacturing",
                    description = "Manufacturer of electrical equipment for power transmission and distribution infrastructure."
                ),
                IPO(
                    companyName = "Ajax Engineering Ltd",
                    type = IPOType.MAINBOARD,
                    status = IPOStatus.UPCOMING,
                    priceBandLow = 610.0,
                    priceBandHigh = 629.0,
                    lotSize = 23,
                    issueSizeCr = "₹1,250 Cr",
                    minInvestment = 14467.0,
                    exchange = "NSE",
                    openDate = "Feb 18",
                    closeDate = "Feb 20",
                    listingDate = "Feb 25",
                    gmp = GMPData(
                        gmpValue = 55.0,
                        estimatedListingPrice = 684.0,
                        listingGainPct = 8.7
                    ),
                    sector = "Industrial",
                    description = "Leading manufacturer of concrete equipment and construction machinery for infrastructure projects."
                ),
                IPO(
                    companyName = "Carraro India Ltd",
                    type = IPOType.MAINBOARD,
                    status = IPOStatus.CLOSED,
                    priceBandLow = 668.0,
                    priceBandHigh = 704.0,
                    lotSize = 21,
                    issueSizeCr = "₹1,250 Cr",
                    minInvestment = 14784.0,
                    exchange = "NSE",
                    openDate = "Feb 5",
                    closeDate = "Feb 7",
                    listingDate = "Feb 12",
                    gmp = GMPData(
                        gmpValue = 72.0,
                        estimatedListingPrice = 776.0,
                        listingGainPct = 10.2
                    ),
                    sector = "Auto Components",
                    description = "Manufacturer of transmission systems for tractors and construction equipment vehicles."
                ),
                IPO(
                    companyName = "Enviro Infra Engineers Ltd",
                    type = IPOType.MAINBOARD,
                    status = IPOStatus.CLOSED,
                    priceBandLow = 140.0,
                    priceBandHigh = 148.0,
                    lotSize = 101,
                    issueSizeCr = "₹650 Cr",
                    minInvestment = 14948.0,
                    exchange = "NSE",
                    openDate = "Feb 5",
                    closeDate = "Feb 7",
                    listingDate = "Feb 12",
                    gmp = GMPData(
                        gmpValue = 35.0,
                        estimatedListingPrice = 183.0,
                        listingGainPct = 23.6
                    ),
                    sector = "Infrastructure",
                    description = "Infrastructure company focused on water and wastewater treatment projects across India."
                ),
                IPO(
                    companyName = "Bluestone Jewellery Ltd",
                    type = IPOType.MAINBOARD,
                    status = IPOStatus.LISTED,
                    priceBandLow = 1150.0,
                    priceBandHigh = 1212.0,
                    lotSize = 12,
                    issueSizeCr = "₹1,500 Cr",
                    minInvestment = 14544.0,
                    exchange = "NSE",
                    openDate = "Jan 28",
                    closeDate = "Jan 30",
                    listingDate = "Feb 4",
                    gmp = GMPData(
                        gmpValue = 180.0,
                        estimatedListingPrice = 1392.0,
                        listingGainPct = 14.9
                    ),
                    sector = "Retail",
                    description = "Leading online jewellery retailer with omnichannel presence and pan-India delivery network."
                ),
                IPO(
                    companyName = "Adroit Infotech Ltd",
                    type = IPOType.SME,
                    status = IPOStatus.LISTED,
                    priceBandLow = 78.0,
                    priceBandHigh = 82.0,
                    lotSize = 1600,
                    issueSizeCr = "₹45 Cr",
                    minInvestment = 131200.0,
                    exchange = "NSE SME",
                    openDate = "Jan 25",
                    closeDate = "Jan 29",
                    listingDate = "Feb 3",
                    gmp = GMPData(
                        gmpValue = 25.0,
                        estimatedListingPrice = 107.0,
                        listingGainPct = 30.5
                    ),
                    sector = "Information Technology",
                    description = "IT services company providing software solutions and digital services for SME sector."
                ),
                IPO(
                    companyName = "Ola Electric Mobility Ltd",
                    type = IPOType.MAINBOARD,
                    status = IPOStatus.ALLOTMENT,
                    priceBandLow = 72.0,
                    priceBandHigh = 76.0,
                    lotSize = 195,
                    issueSizeCr = "₹6,146 Cr",
                    minInvestment = 14820.0,
                    exchange = "NSE",
                    openDate = "Feb 8",
                    closeDate = "Feb 10",
                    listingDate = "Feb 15",
                    gmp = GMPData(
                        gmpValue = -5.0,
                        estimatedListingPrice = 71.0,
                        listingGainPct = -6.6
                    ),
                    sector = "Electric Vehicles",
                    description = "India's leading electric two-wheeler manufacturer with integrated manufacturing ecosystem."
                ),
                IPO(
                    companyName = "Indegene Ltd",
                    type = IPOType.MAINBOARD,
                    status = IPOStatus.ALLOTMENT,
                    priceBandLow = 430.0,
                    priceBandHigh = 452.0,
                    lotSize = 33,
                    issueSizeCr = "₹1,842 Cr",
                    minInvestment = 14916.0,
                    exchange = "NSE",
                    openDate = "Feb 8",
                    closeDate = "Feb 10",
                    listingDate = "Feb 15",
                    gmp = GMPData(
                        gmpValue = 95.0,
                        estimatedListingPrice = 547.0,
                        listingGainPct = 21.0
                    ),
                    sector = "Healthcare Technology",
                    description = "Digital-first healthcare solutions company serving life sciences and healthcare sectors globally."
                )
            )

            _state.update {
                it.copy(ipoState = it.ipoState.copy(
                    ipos = demoIPOs,
                    isLoading = false
                ))
            }
        }
    }

    private fun loadAlerts() {
        viewModelScope.launch {
            _state.update { it.copy(alertsState = Resource.Loading()) }

            when (val result = repository.getAlerts()) {
                is Resource.Success -> {
                    val alerts = result.data ?: emptyList()
                    val activeCount = alerts.count { it.isActive && !it.isTriggered }
                    val triggeredCount = alerts.count { it.isTriggered }

                    _state.update {
                        it.copy(
                            alertsState = Resource.Success(alerts),
                            activeCount = activeCount,
                            triggeredCount = triggeredCount
                        )
                    }
                }
                is Resource.Error -> {
                    _state.update {
                        it.copy(alertsState = Resource.Error(result.message ?: "Unknown error"))
                    }
                }
                is Resource.Loading -> {}
            }
        }
    }

    fun createAlert(alert: Alert) {
        viewModelScope.launch {
            repository.createAlert(alert)
            loadAlerts()
        }
    }

    fun toggleAlert(alertId: String) {
        viewModelScope.launch {
            repository.toggleAlert(alertId)
            loadAlerts()
        }
    }

    fun deleteAlert(alertId: String) {
        viewModelScope.launch {
            repository.deleteAlert(alertId)
            loadAlerts()
        }
    }
}
