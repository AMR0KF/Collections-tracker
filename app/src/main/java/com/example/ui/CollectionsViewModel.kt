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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    // --- Export & Backup Utilities ---
    suspend fun generateCashBoxCsv(): String {
        val txs = repository.getAllCashTransactionsDirect()
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val sb = StringBuilder()
        sb.append("ID,Date,Type,Category,Amount,Description,RelatedPaymentID\n")
        txs.forEach { tx ->
            val dateStr = sdf.format(Date(tx.date))
            val descEsc = (tx.description ?: "").replace("\"", "\"\"")
            sb.append("\"${tx.id}\",\"$dateStr\",\"${tx.type}\",\"${tx.category}\",${tx.amount},\"$descEsc\",\"${tx.relatedPaymentId ?: ""}\"\n")
        }
        return sb.toString()
    }

    suspend fun generateCollectionsCsv(): String {
        val payersList = payers.value
        val itemsList = collectionItems.value
        val paymentsList = paymentRecords.value
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val sb = StringBuilder()
        sb.append("Payer Name,Phone,Item Title,Type,Frequency,Amount,Start Date,End Date,Total Paid\n")

        payersList.forEach { payer ->
            val payerItems = itemsList.filter { it.payerId == payer.id }
            if (payerItems.isEmpty()) {
                val nameEsc = payer.name.replace("\"", "\"\"")
                val phoneEsc = (payer.phone ?: "").replace("\"", "\"\"")
                sb.append("\"$nameEsc\",\"$phoneEsc\",\"N/A\",\"N/A\",\"N/A\",0, \"N/A\",\"N/A\",0\n")
            } else {
                payerItems.forEach { item ->
                    val nameEsc = payer.name.replace("\"", "\"\"")
                    val phoneEsc = (payer.phone ?: "").replace("\"", "\"\"")
                    val titleEsc = (item.customTypeLabel ?: item.type).replace("\"", "\"\"")
                    val startStr = sdf.format(Date(item.startDate))
                    val endStr = item.endDate?.let { sdf.format(Date(it)) } ?: "N/A"
                    val itemPaid = paymentsList.filter { it.collectionItemId == item.id }.sumOf { it.amount }
                    sb.append("\"$nameEsc\",\"$phoneEsc\",\"$titleEsc\",\"${item.type}\",\"${item.frequency}\",${item.amount},\"$startStr\",\"$endStr\",$itemPaid\n")
                }
            }
        }
        return sb.toString()
    }

    suspend fun generateFullBackupJson(): String {
        val root = org.json.JSONObject()

        val payersArray = org.json.JSONArray()
        payers.value.forEach { p ->
            val pObj = org.json.JSONObject()
            pObj.put("id", p.id)
            pObj.put("name", p.name)
            pObj.put("phone", p.phone)
            pObj.put("notes", p.notes)
            pObj.put("createdAt", p.createdAt)
            pObj.put("updatedAt", p.updatedAt)
            payersArray.put(pObj)
        }
        root.put("payers", payersArray)

        val itemsArray = org.json.JSONArray()
        collectionItems.value.forEach { item ->
            val iObj = org.json.JSONObject()
            iObj.put("id", item.id)
            iObj.put("payerId", item.payerId)
            iObj.put("type", item.type)
            iObj.put("customTypeLabel", item.customTypeLabel)
            iObj.put("amount", item.amount)
            iObj.put("frequency", item.frequency)
            iObj.put("intervalDays", item.intervalDays)
            iObj.put("startDate", item.startDate)
            iObj.put("endDate", item.endDate)
            iObj.put("createdAt", item.createdAt)
            iObj.put("updatedAt", item.updatedAt)
            itemsArray.put(iObj)
        }
        root.put("collectionItems", itemsArray)

        val paymentsArray = org.json.JSONArray()
        paymentRecords.value.forEach { pr ->
            val prObj = org.json.JSONObject()
            prObj.put("id", pr.id)
            prObj.put("collectionItemId", pr.collectionItemId)
            prObj.put("cycleIndex", pr.cycleIndex)
            prObj.put("amount", pr.amount)
            prObj.put("date", pr.date)
            prObj.put("method", pr.method)
            prObj.put("notes", pr.notes)
            prObj.put("createdAt", pr.createdAt)
            prObj.put("updatedAt", pr.updatedAt)
            paymentsArray.put(prObj)
        }
        root.put("paymentRecords", paymentsArray)

        val cashArray = org.json.JSONArray()
        repository.getAllCashTransactionsDirect().forEach { tx ->
            val cObj = org.json.JSONObject()
            cObj.put("id", tx.id)
            cObj.put("type", tx.type)
            cObj.put("amount", tx.amount)
            cObj.put("category", tx.category)
            cObj.put("description", tx.description)
            cObj.put("date", tx.date)
            cObj.put("relatedPaymentId", tx.relatedPaymentId)
            cObj.put("createdAt", tx.createdAt)
            cObj.put("updatedAt", tx.updatedAt)
            cashArray.put(cObj)
        }
        root.put("cashTransactions", cashArray)

        return root.toString(2)
    }

    fun restoreFullBackupJson(jsonStr: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val root = org.json.JSONObject(jsonStr)
                if (root.has("payers")) {
                    val payersArray = root.getJSONArray("payers")
                    for (i in 0 until payersArray.length()) {
                        val pObj = payersArray.getJSONObject(i)
                        val payer = Payer(
                            id = pObj.getString("id"),
                            name = pObj.getString("name"),
                            phone = if (pObj.isNull("phone")) null else pObj.getString("phone"),
                            notes = if (pObj.isNull("notes")) null else pObj.getString("notes"),
                            createdAt = pObj.getLong("createdAt"),
                            updatedAt = pObj.getLong("updatedAt")
                        )
                        repository.insertPayer(payer)
                    }
                }
                if (root.has("collectionItems")) {
                    val itemsArray = root.getJSONArray("collectionItems")
                    for (i in 0 until itemsArray.length()) {
                        val iObj = itemsArray.getJSONObject(i)
                        val item = CollectionItem(
                            id = iObj.getString("id"),
                            payerId = iObj.getString("payerId"),
                            type = iObj.getString("type"),
                            customTypeLabel = if (iObj.isNull("customTypeLabel")) null else iObj.getString("customTypeLabel"),
                            amount = iObj.getDouble("amount"),
                            frequency = iObj.getString("frequency"),
                            intervalDays = if (iObj.isNull("intervalDays")) null else iObj.getInt("intervalDays"),
                            startDate = iObj.getLong("startDate"),
                            endDate = if (iObj.isNull("endDate")) null else iObj.getLong("endDate"),
                            createdAt = iObj.getLong("createdAt"),
                            updatedAt = iObj.getLong("updatedAt")
                        )
                        repository.insertCollectionItem(item)
                    }
                }
                if (root.has("paymentRecords")) {
                    val paymentsArray = root.getJSONArray("paymentRecords")
                    for (i in 0 until paymentsArray.length()) {
                        val prObj = paymentsArray.getJSONObject(i)
                        val pr = PaymentRecord(
                            id = prObj.getString("id"),
                            collectionItemId = prObj.getString("collectionItemId"),
                            cycleIndex = prObj.getInt("cycleIndex"),
                            amount = prObj.getDouble("amount"),
                            date = prObj.getLong("date"),
                            method = prObj.getString("method"),
                            notes = if (prObj.isNull("notes")) null else prObj.getString("notes"),
                            createdAt = prObj.getLong("createdAt"),
                            updatedAt = prObj.getLong("updatedAt")
                        )
                        repository.insertPaymentRecord(pr, enqueue = false)
                    }
                }
                if (root.has("cashTransactions")) {
                    val cashArray = root.getJSONArray("cashTransactions")
                    for (i in 0 until cashArray.length()) {
                        val cObj = cashArray.getJSONObject(i)
                        val tx = CashTransaction(
                            id = cObj.getString("id"),
                            type = cObj.getString("type"),
                            amount = cObj.getDouble("amount"),
                            category = cObj.getString("category"),
                            description = if (cObj.isNull("description")) null else cObj.getString("description"),
                            date = cObj.getLong("date"),
                            relatedPaymentId = if (cObj.isNull("relatedPaymentId")) null else cObj.getString("relatedPaymentId"),
                            createdAt = cObj.getLong("createdAt"),
                            updatedAt = cObj.getLong("updatedAt")
                        )
                        repository.insertCashTransaction(tx)
                    }
                }
                onResult(true)
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(false)
            }
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
