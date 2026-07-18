package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.analytics.AnalyticsEngine
import com.example.analytics.AnalyticsSummary
import com.example.data.*
import com.example.sync.SyncManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed class Screen {
    object Dashboard : Screen()
    object Payers : Screen()
    object CashBox : Screen()
    object Settings : Screen()
    data class PayerDetail(val payerId: String) : Screen()
    data class AddCollectionItem(val payerId: String) : Screen()
}

sealed class AnalyticsState {
    object Loading : AnalyticsState()
    data class Success(val summary: AnalyticsSummary) : AnalyticsState()
    data class Error(val message: String) : AnalyticsState()
}

class CollectionsViewModel(
    private val repository: AppRepository,
    private val syncManager: SyncManager
) : ViewModel() {

    // Current screen navigation state
    private val _currentScreen = MutableStateFlow<Screen>(Screen.Dashboard)
    val currentScreen: StateFlow<Screen> = _currentScreen

    fun navigateTo(screen: Screen) {
        _currentScreen.value = screen
    }

    // Language State ("en" or "ar")
    private val _currentLanguage = MutableStateFlow("en")
    val currentLanguage: StateFlow<String> = _currentLanguage

    fun toggleLanguage() {
        _currentLanguage.value = if (_currentLanguage.value == "en") "ar" else "en"
    }

    fun setLanguage(lang: String) {
        _currentLanguage.value = lang
    }

    // Connection and Sync states from SyncManager
    val isOnline: StateFlow<Boolean> = syncManager.isOnline
    val syncState: StateFlow<SyncManager.SyncStatus> = syncManager.syncingState

    // Database Flows
    val payers: StateFlow<List<Payer>> = repository.allPayers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val collectionItems: StateFlow<List<CollectionItem>> = repository.allCollectionItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val paymentRecords: StateFlow<List<PaymentRecord>> = repository.allPaymentRecords
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val cashTransactions: StateFlow<List<CashTransaction>> = repository.allCashTransactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val syncQueue: StateFlow<List<SyncQueueItem>> = repository.syncQueue
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isComputingAnalytics = MutableStateFlow(false)
    val isComputingAnalytics: StateFlow<Boolean> = _isComputingAnalytics

    // Reactively compute Analytics summary on any database or connection updates!
    val analyticsState: StateFlow<AnalyticsState> = combine(
        repository.allPayers,
        repository.allCollectionItems,
        repository.allPaymentRecords,
        syncManager.isOnline
    ) { payersList, itemsList, paymentsList, online ->
        _isComputingAnalytics.value = true
        val apiKey = com.example.BuildConfig.GEMINI_API_KEY
        val summary = AnalyticsEngine.runAnalytics(
            payers = payersList,
            items = itemsList,
            payments = paymentsList,
            isOnline = online,
            apiKey = apiKey
        )
        _isComputingAnalytics.value = false
        AnalyticsState.Success(summary) as AnalyticsState
    }.catch { e ->
        _isComputingAnalytics.value = false
        emit(AnalyticsState.Error(e.message ?: "Failed to compute metrics."))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AnalyticsState.Loading)

    // --- Payer Operations ---
    fun addPayer(name: String, phone: String?, notes: String?) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val payer = Payer(
                id = java.util.UUID.randomUUID().toString(),
                name = name.trim(),
                phone = phone?.trim()?.takeIf { it.isNotEmpty() },
                notes = notes?.trim()?.takeIf { it.isNotEmpty() },
                createdAt = now,
                updatedAt = now
            )
            repository.insertPayer(payer)
        }
    }

    fun deletePayer(payerId: String) {
        viewModelScope.launch {
            repository.deletePayer(payerId)
            // If deleting current payer, navigate back to payers list
            val current = _currentScreen.value
            if (current is Screen.PayerDetail && current.payerId == payerId) {
                _currentScreen.value = Screen.Payers
            } else if (current is Screen.AddCollectionItem && current.payerId == payerId) {
                _currentScreen.value = Screen.Payers
            }
        }
    }

    // --- Collection Item Operations ---
    fun addCollectionItem(
        payerId: String,
        type: String,
        customTypeLabel: String?,
        amount: Double,
        frequency: String,
        intervalDays: Int?,
        startDate: Long,
        endDate: Long?
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val item = CollectionItem(
                id = java.util.UUID.randomUUID().toString(),
                payerId = payerId,
                type = type,
                customTypeLabel = customTypeLabel?.trim()?.takeIf { it.isNotEmpty() },
                amount = amount,
                frequency = frequency,
                intervalDays = intervalDays,
                startDate = startDate,
                endDate = endDate,
                createdAt = now,
                updatedAt = now
            )
            repository.insertCollectionItem(item)
            _currentScreen.value = Screen.PayerDetail(payerId)
        }
    }

    fun deleteCollectionItem(itemId: String) {
        viewModelScope.launch {
            repository.deleteCollectionItem(itemId)
        }
    }

    // --- Payment Record Operations ---
    fun recordPayment(
        collectionItemId: String,
        cycleIndex: Int,
        amount: Double,
        date: Long,
        method: String,
        notes: String?
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val payment = PaymentRecord(
                id = java.util.UUID.randomUUID().toString(),
                collectionItemId = collectionItemId,
                cycleIndex = cycleIndex,
                amount = amount,
                date = date,
                method = method,
                notes = notes?.trim()?.takeIf { it.isNotEmpty() },
                createdAt = now,
                updatedAt = now
            )
            repository.insertPaymentRecord(payment)
        }
    }

    fun deletePaymentRecord(recordId: String) {
        viewModelScope.launch {
            repository.deletePaymentRecord(recordId)
        }
    }

    // --- Cash Box Operations ---
    fun addCashTransaction(
        type: String, // "in" or "out"
        amount: Double,
        category: String,
        description: String?,
        date: Long
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val transaction = CashTransaction(
                id = java.util.UUID.randomUUID().toString(),
                type = type,
                amount = amount,
                category = category,
                description = description?.trim()?.takeIf { it.isNotEmpty() },
                date = date,
                relatedPaymentId = null,
                createdAt = now,
                updatedAt = now
            )
            repository.insertCashTransaction(transaction)
        }
    }

    fun deleteCashTransaction(id: String) {
        viewModelScope.launch {
            repository.deleteCashTransaction(id)
        }
    }

    // --- Connection and Synchronize Operations ---
    fun toggleConnection() {
        syncManager.toggleConnection()
    }

    fun performSync() {
        viewModelScope.launch {
            syncManager.performSync()
        }
    }

    // Factory class
    class Factory(
        private val repository: AppRepository,
        private val syncManager: SyncManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(CollectionsViewModel::class.java)) {
                return CollectionsViewModel(repository, syncManager) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
