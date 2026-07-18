package com.example.analytics

import android.util.Log
import com.example.data.Payer
import com.example.data.CollectionItem
import com.example.data.PaymentRecord
import com.example.engine.CycleEngine
import com.example.engine.CycleStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class AnalyticsSummary(
    val collectionRate: Double,
    val projectedVolume: Double,
    val riskSummary: String,
    val recommendations: List<String>,
    val isLocalFallback: Boolean
)

object AnalyticsEngine {
    private const val TAG = "AnalyticsEngine"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun runAnalytics(
        payers: List<Payer>,
        items: List<CollectionItem>,
        payments: List<PaymentRecord>,
        isOnline: Boolean,
        apiKey: String
    ): AnalyticsSummary = withContext(Dispatchers.IO) {
        val currentTime = System.currentTimeMillis()

        // 1. Compute Base Core Metrics
        var totalExpected = 0.0
        var totalCollected = payments.sumOf { it.amount }
        var totalOverdue = 0.0
        val overduePayers = mutableSetOf<String>()
        val categoryCount = mutableMapOf<String, Int>()

        for (item in items) {
            categoryCount[item.type] = (categoryCount[item.type] ?: 0) + 1
            val cycles = CycleEngine.generateCyclesForItem(item, currentTime)
            val itemPayments = payments.filter { it.collectionItemId == item.id }

            for (cycle in cycles) {
                totalExpected += cycle.expectedAmount
                val paid = itemPayments.filter { it.cycleIndex == cycle.index }.sumOf { it.amount }
                val unpaid = cycle.expectedAmount - paid

                if (unpaid > 0.0) {
                    val status = CycleEngine.determineCycleStatus(cycle, itemPayments, currentTime)
                    if (status == CycleStatus.OVERDUE) {
                        totalOverdue += unpaid
                        val payer = payers.find { it.id == item.payerId }
                        if (payer != null) {
                            overduePayers.add(payer.name)
                        }
                    }
                }
            }
        }

        val rate = if (totalExpected > 0.0) {
            (totalCollected / totalExpected) * 100.0
        } else {
            100.0
        }

        val projected = if (totalExpected > totalCollected) {
            totalExpected - totalCollected
        } else {
            0.0
        }

        // 2. If Offline or API key is blank, run Heuristics fallback immediately
        if (!isOnline || apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext computeHeuristicsSummary(rate, projected, totalOverdue, overduePayers)
        }

        // 3. Cloud AI Pipeline with Gemini
        try {
            val categoryBreakdown = categoryCount.entries.joinToString(", ") { "${it.key}: ${it.value}" }
            val prompt = """
                You are a professional financial logical analyzer. Analyze the following Collections Tracker database metrics and draft a structured business report.
                
                METRICS:
                - Active Payers: ${payers.size}
                - Total Collections Configured: ${items.size}
                - Total Expected Amount across active cycles: $${String.format("%.2f", totalExpected)}
                - Total Collected Amount: $${String.format("%.2f", totalCollected)}
                - Outstanding Overdue Amount: $${String.format("%.2f", totalOverdue)}
                - List of Overdue Payers: ${overduePayers.joinToString(", ")}
                - Category breakdown: $categoryBreakdown
                
                Provide your analysis strictly in the following JSON format:
                {
                  "collectionRate": <number, overall collection rate percentage, e.g. 78.5>,
                  "projectedVolume": <number, forecast of expected collections, e.g. 3200.0>,
                  "riskSummary": "<string, critical alert regarding delinquent payers and financial health>",
                  "recommendations": [
                    "<string, action bullet 1>",
                    "<string, action bullet 2>"
                  ]
                }
                
                Do not return any conversational text, markdown wrapping (e.g. do not wrap in ```json), or backticks. Return ONLY raw valid JSON matching this schema.
            """.trimIndent()

            val requestBodyJson = JSONObject().apply {
                val contentsArray = JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", prompt)
                            })
                        })
                    })
                }
                put("contents", contentsArray)
            }

            val requestBody = requestBodyJson.toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())

            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "Gemini call failed with code: ${response.code}. Bypassing to local heuristics.")
                return@withContext computeHeuristicsSummary(rate, projected, totalOverdue, overduePayers)
            }

            val bodyString = response.body?.string() ?: ""
            val jsonResponse = JSONObject(bodyString)
            val candidates = jsonResponse.optJSONArray("candidates")
            val candidate = candidates?.optJSONObject(0)
            val content = candidate?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            var rawText = parts?.optJSONObject(0)?.optString("text")?.trim() ?: ""

            // Clean markdown blocks if present
            if (rawText.startsWith("```")) {
                val lines = rawText.lines()
                val cleanLines = lines.filter { !it.trim().startsWith("```") }
                rawText = cleanLines.joinToString("\n").trim()
            }

            val resultObj = JSONObject(rawText)
            val collectionRate = resultObj.optDouble("collectionRate", rate)
            val projectedVolume = resultObj.optDouble("projectedVolume", projected)
            val riskSummary = resultObj.optString("riskSummary", "Cloud AI analysis complete.")
            val recsArray = resultObj.optJSONArray("recommendations")
            val recs = mutableListOf<String>()
            if (recsArray != null) {
                for (i in 0 until recsArray.length()) {
                    recs.add(recsArray.getString(i))
                }
            } else {
                recs.add("No recommendations from AI.")
            }

            return@withContext AnalyticsSummary(
                collectionRate = collectionRate,
                projectedVolume = projectedVolume,
                riskSummary = riskSummary,
                recommendations = recs,
                isLocalFallback = false
            )

        } catch (e: Exception) {
            Log.e(TAG, "Gemini API exception: ${e.message}. Silently falling back to heuristics.", e)
            return@withContext computeHeuristicsSummary(rate, projected, totalOverdue, overduePayers)
        }
    }

    private fun computeHeuristicsSummary(
        rate: Double,
        projected: Double,
        totalOverdue: Double,
        overduePayers: Set<String>
    ): AnalyticsSummary {
        val riskSummary: String
        val recommendations = mutableListOf<String>()

        if (totalOverdue > 0.0) {
            riskSummary = "Alert: ${overduePayers.size} payers are currently past-due, totaling $${String.format("%.2f", totalOverdue)} in overdue cash flow."
            recommendations.add("Urgent: Contact overdue accounts (${overduePayers.joinToString(", ")}) to recover delinquent cycles.")
            recommendations.add("Consider setting up automatic transfer reminders for loan installments to lower payment delays.")
            recommendations.add("Apply late collection warnings or penalties to custom billing structures to encourage timing compliance.")
        } else {
            riskSummary = "Repayments Healthy: All configured billing and collection cycles are fully paid or on schedule."
            recommendations.add("Great performance! Continue maintaining close accounts and verify phone numbers for automated reminders.")
            recommendations.add("Review collection schedules to see if high-rate payers can be rolled into monthly billing.")
        }

        return AnalyticsSummary(
            collectionRate = rate,
            projectedVolume = projected,
            riskSummary = riskSummary,
            recommendations = recommendations,
            isLocalFallback = true
        )
    }
}
