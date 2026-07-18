package com.example.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.CollectionItem
import com.example.data.Payer
import com.example.data.PaymentRecord
import com.example.engine.CycleEngine
import com.example.engine.CycleStatus
import com.example.engine.CycleWithStatus
import com.example.sync.SyncManager
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun CollectionsUi(viewModel: CollectionsViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val lang by viewModel.currentLanguage.collectAsStateWithLifecycle()
    val layoutDirection = if (lang == "ar") androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr

    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalLayoutDirection provides layoutDirection
    ) {
        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = {
                fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
            },
            label = "screen_transition"
        ) { screen ->
            when (screen) {
                is Screen.Dashboard -> DashboardScreen(viewModel)
                is Screen.Payers -> PayersScreen(viewModel)
                is Screen.CashBox -> CashBoxScreen(viewModel)
                is Screen.Settings -> SettingsScreen(viewModel)
                is Screen.PayerDetail -> PayerDetailScreen(viewModel, screen.payerId)
                is Screen.AddCollectionItem -> AddCollectionItemScreen(viewModel, screen.payerId)
            }
        }
    }
}

// --- Bottom Navigation Bar ---
@Composable
fun CollectionsBottomBar(
    currentScreen: Screen,
    viewModel: CollectionsViewModel,
    lang: String
) {
    NavigationBar(
        modifier = Modifier.testTag("bottom_navigation_bar")
    ) {
        NavigationBarItem(
            icon = { Icon(Icons.Default.Home, contentDescription = "Dashboard") },
            label = { Text(Translations.getString("dashboard", lang)) },
            selected = currentScreen is Screen.Dashboard,
            onClick = { viewModel.navigateTo(Screen.Dashboard) },
            modifier = Modifier.testTag("nav_dashboard")
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Person, contentDescription = "Payers") },
            label = { Text(Translations.getString("payers", lang)) },
            selected = currentScreen is Screen.Payers,
            onClick = { viewModel.navigateTo(Screen.Payers) },
            modifier = Modifier.testTag("nav_payers")
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Payments, contentDescription = "Cash Box") },
            label = { Text(Translations.getString("cash_box", lang)) },
            selected = currentScreen is Screen.CashBox,
            onClick = { viewModel.navigateTo(Screen.CashBox) },
            modifier = Modifier.testTag("nav_cash_box")
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
            label = { Text(Translations.getString("settings", lang)) },
            selected = currentScreen is Screen.Settings,
            onClick = { viewModel.navigateTo(Screen.Settings) },
            modifier = Modifier.testTag("nav_settings")
        )
    }
}

// --- Dashboard Screen ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: CollectionsViewModel) {
    val payersList by viewModel.payers.collectAsStateWithLifecycle()
    val itemsList by viewModel.collectionItems.collectAsStateWithLifecycle()
    val paymentsList by viewModel.paymentRecords.collectAsStateWithLifecycle()
    val analyticsState by viewModel.analyticsState.collectAsStateWithLifecycle()
    val isComputingAnalytics by viewModel.isComputingAnalytics.collectAsStateWithLifecycle()
    val lang by viewModel.currentLanguage.collectAsStateWithLifecycle()

    val context = LocalContext.current

    // 1. Calculate Real-time Financial Metrics
    val currentTime = System.currentTimeMillis()
    var totalExpected = 0.0
    val totalCollected = paymentsList.sumOf { it.amount }

    for (item in itemsList) {
        val cycles = CycleEngine.generateCyclesForItem(item, currentTime)
        for (cycle in cycles) {
            totalExpected += cycle.expectedAmount
        }
    }

    val recoveryRate = if (totalExpected > 0.0) (totalCollected / totalExpected) * 100.0 else 100.0
    val projectedVolume = if (totalExpected > totalCollected) totalExpected - totalCollected else 0.0

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            Translations.getString("app_title", lang),
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            CollectionsBottomBar(
                currentScreen = Screen.Dashboard,
                viewModel = viewModel,
                lang = lang
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Key Metrics Panel
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            Translations.getString("financial_overview", lang),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            MetricItemCard(
                                title = Translations.getString("total_collected", lang),
                                value = String.format("$%,.2f", totalCollected),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            MetricItemCard(
                                title = Translations.getString("projected_volume", lang),
                                value = String.format("$%,.2f", projectedVolume),
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.weight(1f)
                            )
                            MetricItemCard(
                                title = Translations.getString("recovery_rate", lang),
                                value = String.format("%.1f%%", recoveryRate),
                                color = if (recoveryRate >= 80.0) Color(0xFF2B6B2B) else if (recoveryRate >= 50.0) Color(0xFF7A5A00) else Color(0xFFBA1A1A),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Intelligent Analytics Panel (Bento Card style)
            item {
                AnalyticsPanel(analyticsState, isComputingAnalytics, lang)
            }

            // Recent Activity Section
            item {
                Text(
                    text = if (lang == "ar") "المدفوعات الأخيرة" else "Recent Payments",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            val sortedPayments = paymentsList.sortedByDescending { it.date }.take(5)
            if (sortedPayments.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = "No payments",
                                modifier = Modifier.size(32.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Text(
                                text = if (lang == "ar") "لا توجد مدفوعات مسجلة بعد" else "No payments recorded yet.",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = { viewModel.navigateTo(Screen.Payers) },
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text(
                                    text = if (lang == "ar") "ابدأ بإضافة دافع" else "Manage Payers",
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            } else {
                items(sortedPayments, key = { it.id }) { payment ->
                    val relatedItem = itemsList.find { it.id == payment.collectionItemId }
                    val relatedPayer = payersList.find { it.id == relatedItem?.payerId }
                    val payerName = relatedPayer?.name ?: (if (lang == "ar") "دافع غير معروف" else "Unknown Payer")
                    
                    val itemTitle = if (relatedItem == null) {
                        ""
                    } else if (relatedItem.type.lowercase() == "custom") {
                        relatedItem.customTypeLabel ?: (if (lang == "ar") "تحصيل مخصص" else "Custom Billing")
                    } else {
                        Translations.getString(relatedItem.type.lowercase(), lang)
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = payerName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (itemTitle.isNotEmpty()) {
                                    Text(
                                        text = "$itemTitle • ${Translations.getString("cycle_num", lang, payment.cycleIndex + 1)}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = String.format("$%,.2f", payment.amount),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                                Text(
                                    text = dateFormat.format(Date(payment.date)),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

// --- Payers Screen ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayersScreen(viewModel: CollectionsViewModel) {
    val payersList by viewModel.payers.collectAsStateWithLifecycle()
    val itemsList by viewModel.collectionItems.collectAsStateWithLifecycle()
    val paymentsList by viewModel.paymentRecords.collectAsStateWithLifecycle()
    val lang by viewModel.currentLanguage.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var showAddPayerDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val currentTime = System.currentTimeMillis()

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            Translations.getString("payers", lang),
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddPayerDialog = true },
                icon = { Icon(Icons.Default.Add, "Add Payer Icon") },
                text = { Text(Translations.getString("add_payer", lang)) },
                modifier = Modifier.testTag("add_payer_button"),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        },
        bottomBar = {
            CollectionsBottomBar(
                currentScreen = Screen.Payers,
                viewModel = viewModel,
                lang = lang
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Search Bar
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_payer_input"),
                    placeholder = { Text(Translations.getString("search_placeholder", lang)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search Icon") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )
            }

            // Payers Title Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        Translations.getString("payers", lang),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        Translations.getString("total_payers", lang, payersList.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Payers List
            val filteredPayers = payersList.filter {
                it.name.contains(searchQuery, ignoreCase = true)
            }

            if (filteredPayers.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = "No Payers Icon",
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Text(
                                if (searchQuery.isEmpty()) Translations.getString("no_payers_added", lang) else Translations.getString("no_matching_payers", lang),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(filteredPayers, key = { it.id }) { payer ->
                    PayerCard(
                        payer = payer,
                        items = itemsList,
                        payments = paymentsList,
                        currentTime = currentTime,
                        onClick = { viewModel.navigateTo(Screen.PayerDetail(payer.id)) }
                    )
                }
            }

            // Buffer spacing
            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    // Add Payer Dialog
    if (showAddPayerDialog) {
        AddPayerDialog(
            onDismiss = { showAddPayerDialog = false },
            onConfirm = { name, phone, notes ->
                viewModel.addPayer(name, phone, notes)
                showAddPayerDialog = false
            }
        )
    }
}

// --- Settings Screen ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: CollectionsViewModel) {
    val lang by viewModel.currentLanguage.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        Translations.getString("settings", lang),
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp
                    )
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            CollectionsBottomBar(
                currentScreen = Screen.Settings,
                viewModel = viewModel,
                lang = lang
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Language Selection Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "Language Selector Icon",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                Translations.getString("language", lang),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // English Option Button
                            Button(
                                onClick = { viewModel.setLanguage("en") },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (lang == "en") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (lang == "en") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("English", fontWeight = FontWeight.Bold)
                            }

                            // Arabic Option Button
                            Button(
                                onClick = { viewModel.setLanguage("ar") },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (lang == "ar") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (lang == "ar") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("العربية", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // System Information / Credits Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = "System Information Icon",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                if (lang == "ar") "معلومات النظام" else "System Information",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // App name & version
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(if (lang == "ar") "اسم التطبيق:" else "App Name:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(Translations.getString("app_title", lang), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(if (lang == "ar") "الإصدار:" else "Version:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("1.0.0-Stable (Jetpack Compose)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(if (lang == "ar") "وقت النظام:" else "System Time:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'", Locale.getDefault())
                            sdf.timeZone = TimeZone.getTimeZone("UTC")
                            Text(sdf.format(Date()), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

// --- Cash Box Screen ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashBoxScreen(viewModel: CollectionsViewModel) {
    val lang by viewModel.currentLanguage.collectAsStateWithLifecycle()
    val transactions by viewModel.cashTransactions.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }

    // Calculate totals
    val totalIn = transactions.filter { it.type == "in" }.sumOf { it.amount }
    val totalOut = transactions.filter { it.type == "out" }.sumOf { it.amount }
    val balance = totalIn - totalOut

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        Translations.getString("cash_box", lang),
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp
                    )
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            CollectionsBottomBar(
                currentScreen = Screen.CashBox,
                viewModel = viewModel,
                lang = lang
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_cash_transaction_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = Translations.getString("add_cash_transaction", lang))
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Main Balance Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = Translations.getString("current_balance", lang),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = String.format("$ %,.2f", balance),
                            fontSize = 36.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // Small side-by-side cards for total in / total out
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Total Cash In Card
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = Translations.getString("total_cash_in", lang),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = String.format("$ %,.2f", totalIn),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2B6B2B) // Clean green
                            )
                        }
                    }

                    // Total Paid Out Card
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = Translations.getString("total_paid_out", lang),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = String.format("$ %,.2f", totalOut),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            // Ledger List Section Header
            item {
                Text(
                    text = Translations.getString("cash_box_ledger", lang),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            if (transactions.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = Translations.getString("cash_box_empty", lang),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                items(transactions, key = { it.id }) { tx ->
                    CashTransactionRow(
                        transaction = tx,
                        lang = lang,
                        onDeleteClick = { viewModel.deleteCashTransaction(tx.id) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(80.dp)) // Extra space for FAB
            }
        }
    }

    if (showAddDialog) {
        AddCashTransactionDialog(
            lang = lang,
            onDismiss = { showAddDialog = false },
            onSave = { type, amount, category, desc, date ->
                viewModel.addCashTransaction(type, amount, category, desc, date)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun CashTransactionRow(
    transaction: com.example.data.CashTransaction,
    lang: String,
    onDeleteClick: () -> Unit
) {
    val isInflow = transaction.type == "in"
    val formattedDate = remember(transaction.date) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        sdf.format(Date(transaction.date))
    }

    Card(
        modifier = Modifier.fillMaxWidth().testTag("cash_transaction_item_${transaction.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left icon showing direction
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (isInflow) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isInflow) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                    contentDescription = if (isInflow) "In" else "Out",
                    tint = if (isInflow) Color(0xFF2B6B2B) else Color(0xFFC62828),
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Middle info details
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = Translations.getString(transaction.category, lang),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (transaction.relatedPaymentId != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "AUTO",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
                if (!transaction.description.isNullOrEmpty()) {
                    Text(
                        text = transaction.description,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
                Text(
                    text = formattedDate,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Right amount & optional action
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = String.format(if (isInflow) "+$ %,.2f" else "-$ %,.2f", transaction.amount),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = if (isInflow) Color(0xFF2B6B2B) else Color(0xFFC62828)
                )
                
                if (transaction.relatedPaymentId == null) {
                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier.size(24.dp).testTag("delete_cash_transaction_${transaction.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AddCashTransactionDialog(
    lang: String,
    onDismiss: () -> Unit,
    onSave: (type: String, amount: Double, category: String, description: String?, date: Long) -> Unit
) {
    var type by remember { mutableStateOf("out") } // Default to Paid Out / Outflow as requested (track paid outs)
    var amountStr by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("payout") }
    var description by remember { mutableStateOf("") }

    val categories = remember(type) {
        if (type == "in") {
            listOf("deposit", "other_in")
        } else {
            listOf("payout", "expense", "wage", "refund")
        }
    }

    // Automatically synchronize selected category if type changes
    LaunchedEffect(type) {
        category = categories.first()
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = Translations.getString("add_transaction_title", lang),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Transaction Type Selector Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { type = "out" },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (type == "out") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (type == "out") MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text(Translations.getString("type_out", lang), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    Button(
                        onClick = { type = "in" },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (type == "in") Color(0xFF2B6B2B) else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (type == "in") Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text(Translations.getString("type_in", lang), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                // Amount Field
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text(Translations.getString("amount", lang)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("add_cash_amount_input"),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                // Category selection chips row
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = Translations.getString("category", lang),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        categories.forEach { cat ->
                            FilterChip(
                                selected = category == cat,
                                onClick = { category = cat },
                                label = { Text(Translations.getString(cat, lang)) },
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }
                }

                // Description Field
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(Translations.getString("description", lang)) },
                    modifier = Modifier.fillMaxWidth().testTag("add_cash_desc_input"),
                    shape = RoundedCornerShape(12.dp),
                    maxLines = 3
                )

                // Actions Button Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(Translations.getString("cancel", lang))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val parsedAmount = amountStr.toDoubleOrNull()
                            if (parsedAmount != null && parsedAmount > 0.0) {
                                onSave(type, parsedAmount, category, description, System.currentTimeMillis())
                            }
                        },
                        enabled = amountStr.toDoubleOrNull()?.let { it > 0.0 } == true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("save_cash_transaction_button")
                    ) {
                        Text(Translations.getString("save_transaction", lang), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}



@Composable
fun MetricItemCard(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                title,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                value,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
fun AnalyticsPanel(state: AnalyticsState, isComputing: Boolean, lang: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary) // Sage-brown natural background
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = "AI Sparkle Icon",
                    tint = MaterialTheme.colorScheme.primaryContainer // Warm sandy beige
                )
                Text(
                    Translations.getString("intelligent_insights", lang),
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )

                Spacer(modifier = Modifier.weight(1f))

                if (isComputing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    val fallback = when (state) {
                        is AnalyticsState.Success -> state.summary.isLocalFallback
                        else -> true
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (fallback) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.primaryContainer
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (fallback) Translations.getString("local_fallback", lang) else Translations.getString("cloud_ai", lang),
                            color = if (fallback) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            when (state) {
                is AnalyticsState.Loading -> {
                    Text(
                        Translations.getString("analyzing_db", lang),
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                        fontSize = 14.sp
                    )
                }
                is AnalyticsState.Error -> {
                    Text(
                        if (lang == "ar") "خطأ في حساب الرؤى: ${state.message}" else "Error calculating insights: ${state.message}",
                        color = Color(0xFFF87171),
                        fontSize = 14.sp
                    )
                }
                is AnalyticsState.Success -> {
                    val summary = state.summary
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            summary.riskSummary,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        Text(
                            Translations.getString("strategic_recommendations", lang),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        summary.recommendations.forEach { bullet ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(start = 4.dp)
                            ) {
                                Text("•", color = MaterialTheme.colorScheme.primaryContainer, fontWeight = FontWeight.Bold)
                                Text(
                                    bullet,
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PayerCard(
    payer: Payer,
    items: List<CollectionItem>,
    payments: List<PaymentRecord>,
    currentTime: Long,
    onClick: () -> Unit
) {
    val isRtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    val lang = if (isRtl) "ar" else "en"

    val rollupStatus = CycleEngine.getPayerStatusRollup(payer.id, items, payments, currentTime)
    val dueBalance = CycleEngine.getPayerDueBalance(payer.id, items, payments, currentTime)

    val payerItems = items.filter { it.payerId == payer.id }
    val detailsLabel = if (payerItems.isEmpty()) {
        Translations.getString("no_active_schedules", lang)
    } else {
        payerItems.joinToString(", ") { item ->
            val typeStr = item.type.lowercase()
            val labelKey = when (typeStr) {
                "rent" -> "rent"
                "loan" -> "loan"
                "membership" -> "membership"
                else -> "custom"
            }
            val label = if (typeStr == "custom") item.customTypeLabel ?: Translations.getString("custom", lang) else Translations.getString(labelKey, lang)
            val activeLabel = Translations.getString("active_items", lang, "$label ($${String.format("%.0f", item.amount)})")
            activeLabel
        }
    }

    val (badgeBg, badgeText, badgeLabel) = when (rollupStatus) {
        CycleStatus.OVERDUE -> Triple(Color(0xFFFDE8E8), Color(0xFFBA1A1A), Translations.getString("overdue", lang))
        CycleStatus.DUE -> Triple(Color(0xFFFFF8E1), Color(0xFF7A5A00), Translations.getString("due", lang))
        CycleStatus.PARTIALLY_PAID -> Triple(Color(0xFFFFF8E1), Color(0xFF7A5A00), Translations.getString("partially_paid", lang))
        CycleStatus.FULLY_PAID -> Triple(Color(0xFFE8F5E9), Color(0xFF2B6B2B), Translations.getString("all_clear", lang))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("payer_card_${payer.id}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    payer.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.weight(1f))

                // Rollup status badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(badgeBg)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(badgeText)
                        )
                        Text(
                            text = badgeLabel,
                            color = badgeText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Text(
                detailsLabel,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (payer.phone != null) {
                    Icon(
                        Icons.Default.Phone,
                        contentDescription = "Phone Icon",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        payer.phone,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = if (dueBalance > 0.0) {
                        Translations.getString("unpaid", lang, String.format("$%,.2f", dueBalance))
                    } else {
                        Translations.getString("all_clear", lang)
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (dueBalance > 0.0) Color(0xFFBA1A1A) else Color(0xFF2B6B2B)
                )
            }
        }
    }
}

// --- Add Payer Dialog ---
@Composable
fun AddPayerDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, phone: String?, notes: String?) -> Unit
) {
    val isRtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    val lang = if (isRtl) "ar" else "en"

    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    Translations.getString("add_new_payer", lang),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        isError = false
                    },
                    label = { Text(Translations.getString("name_required", lang)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("payer_name_input"),
                    singleLine = true,
                    isError = isError,
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text(Translations.getString("phone_optional", lang)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(Translations.getString("notes_optional", lang)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(Translations.getString("cancel", lang))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (name.trim().isEmpty()) {
                                isError = true
                            } else {
                                onConfirm(name, phone, notes)
                            }
                        },
                        modifier = Modifier.testTag("submit_payer_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(Translations.getString("add", lang))
                    }
                }
            }
        }
    }
}

// --- Payer Detail Screen ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayerDetailScreen(viewModel: CollectionsViewModel, payerId: String) {
    val payersList by viewModel.payers.collectAsStateWithLifecycle()
    val itemsList by viewModel.collectionItems.collectAsStateWithLifecycle()
    val paymentsList by viewModel.paymentRecords.collectAsStateWithLifecycle()

    val payer = payersList.find { it.id == payerId }
    val payerItems = itemsList.filter { it.payerId == payerId }

    val context = LocalContext.current
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    // State for Recording Payment Dialog
    var showRecordPaymentDialog by remember { mutableStateOf(false) }
    var selectedItemForPayment by remember { mutableStateOf<CollectionItem?>(null) }
    var selectedCycleIndexForPayment by remember { mutableStateOf<Int?>(null) }
    var prefilledPaymentAmount by remember { mutableStateOf(0.0) }

    val isRtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    val lang = if (isRtl) "ar" else "en"

    if (payer == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(Translations.getString("payer_not_found", lang))
        }
        return
    }

    val currentTime = System.currentTimeMillis()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(payer.name, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateTo(Screen.Dashboard) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showDeleteConfirmation = true },
                        modifier = Modifier.testTag("delete_payer_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Payer",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Profile Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Person, contentDescription = "Payer Profile")
                            Text(
                                Translations.getString("profile_summary", lang),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (payer.phone != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        try {
                                            val intent = Intent(Intent.ACTION_DIAL).apply {
                                                data = Uri.parse("tel:${payer.phone}")
                                            }
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, Translations.getString("cannot_place_call", lang), Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    .padding(vertical = 4.dp)
                            ) {
                                Icon(
                                    Icons.Default.Phone,
                                    contentDescription = "Call Payer",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    payer.phone,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                                Text(Translations.getString("tap_to_dial", lang), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        if (payer.notes != null) {
                            Text(
                                payer.notes,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
                        Text(
                            Translations.getString("registered_at", lang, dateFormat.format(Date(payer.createdAt))),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            // Collection Items Section Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        Translations.getString("active_schedules_title", lang),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Button(
                        onClick = { viewModel.navigateTo(Screen.AddCollectionItem(payerId)) },
                        modifier = Modifier.testTag("add_collection_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Schedule Icon")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(Translations.getString("add_schedule", lang))
                    }
                }
            }

            // Collection Items List
            if (payerItems.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.List,
                                contentDescription = "No Schedules",
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Text(
                                Translations.getString("no_active_schedules_desc", lang),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(payerItems, key = { it.id }) { item ->
                    CollectionItemCard(
                        item = item,
                        payments = paymentsList,
                        currentTime = currentTime,
                        onDeleteClick = { viewModel.deleteCollectionItem(item.id) },
                        onRecordPaymentClick = { cycleIndex, unpaidAmount ->
                            selectedItemForPayment = item
                            selectedCycleIndexForPayment = cycleIndex
                            prefilledPaymentAmount = unpaidAmount
                            showRecordPaymentDialog = true
                        },
                        onDeletePaymentClick = { paymentId ->
                            viewModel.deletePaymentRecord(paymentId)
                        }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }

    // Record Payment Dialog
    if (showRecordPaymentDialog && selectedItemForPayment != null && selectedCycleIndexForPayment != null) {
        RecordPaymentDialog(
            item = selectedItemForPayment!!,
            cycleIndex = selectedCycleIndexForPayment!!,
            prefilledAmount = prefilledPaymentAmount,
            onDismiss = {
                showRecordPaymentDialog = false
                selectedItemForPayment = null
                selectedCycleIndexForPayment = null
            },
            onConfirm = { amount, date, method, notes ->
                viewModel.recordPayment(
                    collectionItemId = selectedItemForPayment!!.id,
                    cycleIndex = selectedCycleIndexForPayment!!,
                    amount = amount,
                    date = date,
                    method = method,
                    notes = notes
                )
                showRecordPaymentDialog = false
                selectedItemForPayment = null
                selectedCycleIndexForPayment = null
            }
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text(Translations.getString("delete_payer_title", lang)) },
            text = { Text(Translations.getString("delete_payer_desc", lang, payer.name)) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deletePayer(payerId)
                        showDeleteConfirmation = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(Translations.getString("delete", lang))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text(Translations.getString("cancel", lang))
                }
            }
        )
    }
}

@Composable
fun CollectionItemCard(
    item: CollectionItem,
    payments: List<PaymentRecord>,
    currentTime: Long,
    onDeleteClick: () -> Unit,
    onRecordPaymentClick: (cycleIndex: Int, unpaidAmount: Double) -> Unit,
    onDeletePaymentClick: (paymentId: String) -> Unit
) {
    val isRtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    val lang = if (isRtl) "ar" else "en"

    var expanded by remember { mutableStateOf(false) }

    val typeStr = item.type.lowercase()
    val labelKey = when (typeStr) {
        "rent" -> "rent"
        "loan" -> "loan"
        "membership" -> "membership"
        else -> "custom"
    }
    val title = if (typeStr == "custom") item.customTypeLabel ?: Translations.getString("custom", lang) else Translations.getString(labelKey, lang)

    val cycles = CycleEngine.getCyclesWithStatuses(item, payments, currentTime)
    val overallStatus = CycleEngine.getCollectionItemStatus(item, payments, currentTime)

    val (badgeBg, badgeText, badgeLabel) = when (overallStatus) {
        CycleStatus.OVERDUE -> Triple(Color(0xFFFDE8E8), Color(0xFFBA1A1A), Translations.getString("overdue", lang))
        CycleStatus.DUE -> Triple(Color(0xFFFFF8E1), Color(0xFF7A5A00), Translations.getString("due", lang))
        CycleStatus.PARTIALLY_PAID -> Triple(Color(0xFFFFF8E1), Color(0xFF7A5A00), Translations.getString("partially_paid", lang))
        CycleStatus.FULLY_PAID -> Triple(Color(0xFFE8F5E9), Color(0xFF2B6B2B), Translations.getString("all_clear", lang))
    }

    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${Translations.getString(item.frequency.lowercase(), lang)} • ${String.format("$%,.2f", item.amount)}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Status Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(badgeBg)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        badgeLabel,
                        color = badgeText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(onClick = onDeleteClick) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete Collection Item",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Subtitle Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    Translations.getString("start_date", lang, dateFormat.format(Date(item.startDate))),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (item.endDate != null) {
                    Text(
                        Translations.getString("end_date", lang, dateFormat.format(Date(item.endDate))),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Expandable billing cycles toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { expanded = !expanded }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (expanded) Translations.getString("hide_cycles", lang) else Translations.getString("show_cycles", lang, cycles.size),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = "Toggle Cycles Arrow",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            // Expanded Billing Cycles
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        Translations.getString("billing_cycles_chronological", lang),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    cycles.forEach { cycleWithStatus ->
                        CycleRow(
                            cycleWithStatus = cycleWithStatus,
                            onRecordPaymentClick = {
                                val unpaid = cycleWithStatus.cycle.expectedAmount - cycleWithStatus.paidAmount
                                onRecordPaymentClick(cycleWithStatus.cycle.index, unpaid)
                            },
                            onDeletePaymentClick = onDeletePaymentClick
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CycleRow(
    cycleWithStatus: CycleWithStatus,
    onRecordPaymentClick: () -> Unit,
    onDeletePaymentClick: (paymentId: String) -> Unit
) {
    val isRtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    val lang = if (isRtl) "ar" else "en"

    val cycle = cycleWithStatus.cycle
    val status = cycleWithStatus.status
    val paid = cycleWithStatus.paidAmount

    val dateFormat = SimpleDateFormat("MMM dd", Locale.getDefault())

    val (badgeBg, badgeText, badgeLabel) = when (status) {
        CycleStatus.OVERDUE -> Triple(Color(0xFFFDE8E8), Color(0xFFBA1A1A), Translations.getString("overdue", lang))
        CycleStatus.DUE -> Triple(Color(0xFFFFF8E1), Color(0xFF7A5A00), Translations.getString("due", lang))
        CycleStatus.PARTIALLY_PAID -> Triple(Color(0xFFFFF8E1), Color(0xFF7A5A00), Translations.getString("partially_paid_short", lang))
        CycleStatus.FULLY_PAID -> Triple(Color(0xFFE8F5E9), Color(0xFF2B6B2B), Translations.getString("paid", lang))
        else -> Triple(Color(0xFFE8F5E9), Color(0xFF2B6B2B), Translations.getString("paid", lang))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        Translations.getString("cycle_num", lang, cycle.index + 1),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${dateFormat.format(Date(cycle.startDate))} - ${Translations.getString("due_on", lang)} ${dateFormat.format(Date(cycle.dueDate))}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Status Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(badgeBg)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        badgeLabel,
                        color = badgeText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (status != CycleStatus.FULLY_PAID) {
                    IconButton(
                        onClick = onRecordPaymentClick,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("record_payment_button")
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Record Payment Icon",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Collected amount progress
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                Translations.getString("collected_of", lang, String.format("$%,.2f", paid), String.format("$%,.2f", cycle.expectedAmount)),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Payments registered for this cycle
            if (cycleWithStatus.payments.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(Translations.getString("payments_history", lang), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    cycleWithStatus.payments.forEach { payRec ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Payment, contentDescription = "Payment record Icon", modifier = Modifier.size(10.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                val methodKey = payRec.method.lowercase().replace(" ", "_")
                                val translatedMethod = try {
                                    Translations.getString(methodKey, lang)
                                } catch (e: Exception) {
                                    payRec.method
                                }
                                Text(
                                    Translations.getString("payment_via", lang, String.format("$%,.2f", payRec.amount), translatedMethod),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (payRec.notes != null) {
                                    Text(payRec.notes, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            IconButton(
                                onClick = { onDeletePaymentClick(payRec.id) },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove Payment",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- Record Payment Dialog ---
@Composable
fun RecordPaymentDialog(
    item: CollectionItem,
    cycleIndex: Int,
    prefilledAmount: Double,
    onDismiss: () -> Unit,
    onConfirm: (amount: Double, date: Long, method: String, notes: String?) -> Unit
) {
    val isRtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    val lang = if (isRtl) "ar" else "en"

    var amountStr by remember { mutableStateOf(String.format(Locale.US, "%.2f", prefilledAmount)) }
    var method by remember { mutableStateOf("cash") } // default
    var notes by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    val options = listOf("cash", "transfer", "other")

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    Translations.getString("record_payment_title", lang, cycleIndex + 1),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = amountStr,
                    onValueChange = {
                        amountStr = it
                        isError = false
                    },
                    label = { Text(Translations.getString("payment_amount_label", lang)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("payment_amount_input"),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = isError,
                    shape = RoundedCornerShape(12.dp)
                )

                // Method Selector
                Text(Translations.getString("payment_method_label", lang), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    options.forEach { opt ->
                        val selected = method == opt
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .clickable { method = opt }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = Translations.getString(opt, lang),
                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(Translations.getString("notes_optional", lang)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(Translations.getString("cancel", lang))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val amt = amountStr.toDoubleOrNull()
                            if (amt == null || amt <= 0) {
                                isError = true
                            } else {
                                onConfirm(amt, System.currentTimeMillis(), method, notes)
                            }
                        },
                        modifier = Modifier.testTag("submit_payment_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(Translations.getString("record", lang))
                    }
                }
            }
        }
    }
}

// --- Add Collection Item Screen ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCollectionItemScreen(viewModel: CollectionsViewModel, payerId: String) {
    val payersList by viewModel.payers.collectAsStateWithLifecycle()
    val payer = payersList.find { it.id == payerId }

    var type by remember { mutableStateOf("rent") }
    var customTypeLabel by remember { mutableStateOf("") }
    var amountStr by remember { mutableStateOf("") }
    var frequency by remember { mutableStateOf("monthly") }
    var intervalDaysStr by remember { mutableStateOf("") }
    var startDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var enableEndDate by remember { mutableStateOf(false) }
    var endDateMillis by remember { mutableStateOf(System.currentTimeMillis() + 30 * 24 * 60 * 60 * 1000L) }

    var isAmountError by remember { mutableStateOf(false) }
    var isCustomTypeError by remember { mutableStateOf(false) }

    val context = LocalContext.current

    val isRtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    val lang = if (isRtl) "ar" else "en"

    if (payer == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(Translations.getString("payer_not_found", lang))
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Translations.getString("configure_schedule", lang), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateTo(Screen.PayerDetail(payerId)) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Intro Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            Translations.getString("new_schedule_for", lang, payer.name),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            Translations.getString("new_schedule_desc", lang),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            // Collection Type Section
            item {
                Text(Translations.getString("collection_type_label", lang), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("rent", "loan", "membership", "custom").forEach { t ->
                        val selected = type == t
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .clickable {
                                    type = t
                                    isCustomTypeError = false
                                }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                Translations.getString(t, lang),
                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            if (type == "custom") {
                item {
                    OutlinedTextField(
                        value = customTypeLabel,
                        onValueChange = {
                            customTypeLabel = it
                            isCustomTypeError = false
                        },
                        label = { Text(Translations.getString("custom_type_label_hint", lang)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_type_label_input"),
                        singleLine = true,
                        isError = isCustomTypeError,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            // Amount Section
            item {
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = {
                        amountStr = it
                        isAmountError = false
                    },
                    label = { Text(Translations.getString("billing_cycle_amount", lang)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("collection_amount_input"),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = isAmountError,
                    shape = RoundedCornerShape(12.dp)
                )
            }

            // Billing Frequency Section
            item {
                Text(Translations.getString("billing_frequency_label", lang), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("weekly", "monthly", "custom").forEach { f ->
                        val selected = frequency == f
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .clickable { frequency = f }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                Translations.getString(f, lang),
                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            if (frequency == "custom") {
                item {
                    OutlinedTextField(
                        value = intervalDaysStr,
                        onValueChange = { intervalDaysStr = it },
                        label = { Text(Translations.getString("interval_days_label", lang)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            // Dates Section
            item {
                Text(Translations.getString("timeline_config_label", lang), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))

                // Start Date selection
                val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                Button(
                    onClick = {
                        // For a simple fully native prototype form, a dialog or just a simple mock date advancement
                        // can be used, or a DatePicker. Let's make a simple slider or toggle or prompt, 
                        // or just keep start date set to today but let them set it. Let's provide standard date offset buttons 
                        // to make it highly interactive and easy to test overdue states! 
                        // Tapping offsets allows setting start dates in the past (e.g. 1 month ago) easily, 
                        // which instantly demonstrates OVERDUE cycle status in real-time! This is extremely smart!
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Info, contentDescription = "Calendar Icon")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(Translations.getString("start_date", lang, dateFormat.format(Date(startDateMillis))))
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                Text(Translations.getString("test_offsets_label", lang), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { startDateMillis = System.currentTimeMillis() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.outlineVariant, contentColor = MaterialTheme.colorScheme.onSurface)
                    ) {
                        Text(Translations.getString("today_btn", lang), fontSize = 10.sp)
                    }
                    Button(
                        onClick = { startDateMillis = System.currentTimeMillis() - 10 * 24 * 60 * 60 * 1000L },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.outlineVariant, contentColor = MaterialTheme.colorScheme.onSurface)
                    ) {
                        Text(Translations.getString("offset_10_days", lang), fontSize = 10.sp)
                    }
                    Button(
                        onClick = { startDateMillis = System.currentTimeMillis() - 30 * 24 * 60 * 60 * 1000L },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.outlineVariant, contentColor = MaterialTheme.colorScheme.onSurface)
                    ) {
                        Text(Translations.getString("offset_30_days", lang), fontSize = 10.sp)
                    }
                }
            }

            // Optional End Date
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(Translations.getString("set_end_date_optional", lang), fontSize = 13.sp)
                    Spacer(modifier = Modifier.weight(1f))
                    Switch(checked = enableEndDate, onCheckedChange = { enableEndDate = it })
                }
            }

            if (enableEndDate) {
                item {
                    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                    Button(
                        onClick = {
                            // Advancing end date
                            endDateMillis += 30 * 24 * 60 * 60 * 1000L
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = "Calendar Icon")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(Translations.getString("end_date", lang, dateFormat.format(Date(endDateMillis))))
                    }
                }
            }

            // Submit Button
            item {
                Button(
                    onClick = {
                        val amount = amountStr.toDoubleOrNull()
                        val customTypeValid = type != "custom" || customTypeLabel.trim().isNotEmpty()

                        if (amount == null || amount <= 0) {
                            isAmountError = true
                        } else if (!customTypeValid) {
                            isCustomTypeError = true
                        } else {
                            val interval = if (frequency == "custom") intervalDaysStr.toIntOrNull() ?: 1 else null
                            viewModel.addCollectionItem(
                                payerId = payerId,
                                type = type,
                                customTypeLabel = customTypeLabel,
                                amount = amount,
                                frequency = frequency,
                                intervalDays = interval,
                                startDate = startDateMillis,
                                endDate = if (enableEndDate) endDateMillis else null
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("submit_collection_button")
                        .padding(vertical = 12.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(Translations.getString("save_schedule_btn", lang), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
