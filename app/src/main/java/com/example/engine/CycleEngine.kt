package com.example.engine

import com.example.data.CollectionItem
import com.example.data.PaymentRecord
import com.example.data.Payer
import java.util.Calendar

data class BillingCycle(
    val index: Int,
    val startDate: Long,
    val dueDate: Long,
    val expectedAmount: Double
)

enum class CycleStatus {
    FULLY_PAID,
    PARTIALLY_PAID,
    DUE,
    OVERDUE
}

data class CycleWithStatus(
    val cycle: BillingCycle,
    val status: CycleStatus,
    val paidAmount: Double,
    val payments: List<PaymentRecord>
)

object CycleEngine {

    fun getCycleStartAndDue(startDate: Long, index: Int, frequency: String, intervalDays: Int?): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.timeInMillis = startDate

        when (frequency.lowercase()) {
            "weekly" -> {
                val start = startDate + index * 7L * 24 * 60 * 60 * 1000
                val due = startDate + (index + 1) * 7L * 24 * 60 * 60 * 1000
                return Pair(start, due)
            }
            "monthly" -> {
                cal.add(Calendar.MONTH, index)
                val start = cal.timeInMillis
                
                val calNext = Calendar.getInstance()
                calNext.timeInMillis = startDate
                calNext.add(Calendar.MONTH, index + 1)
                val due = calNext.timeInMillis
                
                return Pair(start, due)
            }
            else -> { // custom
                val days = intervalDays ?: 1
                val start = startDate + index * days.toLong() * 24 * 60 * 60 * 1000
                val due = startDate + (index + 1) * days.toLong() * 24 * 60 * 60 * 1000
                return Pair(start, due)
            }
        }
    }

    fun generateCyclesForItem(item: CollectionItem, currentTime: Long): List<BillingCycle> {
        val cycles = mutableListOf<BillingCycle>()
        var index = 0
        while (true) {
            val (start, due) = getCycleStartAndDue(item.startDate, index, item.frequency, item.intervalDays)
            
            // Halting condition 1: start date exceeds endDate
            if (item.endDate != null && start > item.endDate) {
                break
            }
            
            // Halting condition 2: represents a future interval beyond the current date plus one billing cycle
            val cycleDuration = due - start
            val limit = currentTime + cycleDuration
            if (start > limit) {
                break
            }
            
            cycles.add(BillingCycle(index, start, due, item.amount))
            index++
            
            // Safety break to prevent infinite loops (e.g. if cycle duration is 0)
            if (cycleDuration <= 0) break
        }
        return cycles
    }

    fun determineCycleStatus(
        cycle: BillingCycle,
        payments: List<PaymentRecord>,
        currentTime: Long
    ): CycleStatus {
        val sumAmount = payments.filter { it.cycleIndex == cycle.index }.sumOf { it.amount }
        val expected = cycle.expectedAmount

        return when {
            sumAmount >= expected -> CycleStatus.FULLY_PAID
            sumAmount > 0.0 && sumAmount < expected -> {
                if (currentTime < cycle.dueDate) {
                    CycleStatus.PARTIALLY_PAID
                } else {
                    CycleStatus.OVERDUE
                }
            }
            else -> { // sumAmount == 0
                if (currentTime < cycle.dueDate) {
                    CycleStatus.DUE
                } else {
                    CycleStatus.OVERDUE
                }
            }
        }
    }

    fun getCyclesWithStatuses(
        item: CollectionItem,
        allPayments: List<PaymentRecord>,
        currentTime: Long
    ): List<CycleWithStatus> {
        val cycles = generateCyclesForItem(item, currentTime)
        val itemPayments = allPayments.filter { it.collectionItemId == item.id }
        
        return cycles.map { cycle ->
            val cyclePayments = itemPayments.filter { it.cycleIndex == cycle.index }
            val status = determineCycleStatus(cycle, itemPayments, currentTime)
            val paidAmount = cyclePayments.sumOf { it.amount }
            CycleWithStatus(cycle, status, paidAmount, cyclePayments)
        }
    }

    fun getCollectionItemStatus(
        item: CollectionItem,
        allPayments: List<PaymentRecord>,
        currentTime: Long
    ): CycleStatus {
        val cyclesWithStatuses = getCyclesWithStatuses(item, allPayments, currentTime)
        if (cyclesWithStatuses.isEmpty()) return CycleStatus.FULLY_PAID
        
        // Worst status priority: Overdue > Due > Partially Paid > Fully Paid
        return rollupStatuses(cyclesWithStatuses.map { it.status })
    }

    fun rollupStatuses(statuses: List<CycleStatus>): CycleStatus {
        if (statuses.isEmpty()) return CycleStatus.FULLY_PAID
        return when {
            statuses.contains(CycleStatus.OVERDUE) -> CycleStatus.OVERDUE
            statuses.contains(CycleStatus.DUE) -> CycleStatus.DUE
            statuses.contains(CycleStatus.PARTIALLY_PAID) -> CycleStatus.PARTIALLY_PAID
            else -> CycleStatus.FULLY_PAID
        }
    }

    // Roll up states for Payer
    fun getPayerStatusRollup(
        payerId: String,
        items: List<CollectionItem>,
        allPayments: List<PaymentRecord>,
        currentTime: Long
    ): CycleStatus {
        val payerItems = items.filter { it.payerId == payerId }
        if (payerItems.isEmpty()) return CycleStatus.FULLY_PAID
        
        val itemStatuses = payerItems.map { item ->
            getCollectionItemStatus(item, allPayments, currentTime)
        }
        return rollupStatuses(itemStatuses)
    }

    // Calculates the total due amount across active collections for a Payer
    fun getPayerDueBalance(
        payerId: String,
        items: List<CollectionItem>,
        allPayments: List<PaymentRecord>,
        currentTime: Long
    ): Double {
        val payerItems = items.filter { it.payerId == payerId }
        var netDue = 0.0
        for (item in payerItems) {
            val cycles = generateCyclesForItem(item, currentTime)
            val itemPayments = allPayments.filter { it.collectionItemId == item.id }
            for (cycle in cycles) {
                val paid = itemPayments.filter { it.cycleIndex == cycle.index }.sumOf { it.amount }
                // If it is overdue or due, we can calculate how much is still due.
                // Or simply: expected - paid (capped at 0)
                val unpaid = cycle.expectedAmount - paid
                if (unpaid > 0) {
                    netDue += unpaid
                }
            }
        }
        return netDue
    }
}
