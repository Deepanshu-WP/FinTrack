package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.dialogs.AddTransactionDialog
import com.example.ui.screens.AnalyticsScreen
import com.example.ui.screens.BudgetsScreen
import com.example.ui.screens.GoalsScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.JsonStorageScreen
import com.example.ui.theme.FinTrackTheme
import com.example.viewmodel.MainViewModel

enum class NavigationTab(val title: String, val icon: ImageVector, val testTag: String) {
    HOME("Home", Icons.Default.Home, "nav_tab_home"),
    ANALYTICS("Analytics", Icons.Default.Analytics, "nav_tab_analytics"),
    BUDGETS("Budgets", Icons.Default.NotificationsActive, "nav_tab_budgets"),
    GOALS("Goals", Icons.Default.TrackChanges, "nav_tab_goals"),
    STORAGE("JSON File", Icons.Default.Description, "nav_tab_storage")
}

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FinTrackTheme {
                MainAppScreen(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun MainAppScreen(viewModel: MainViewModel) {
    var currentTab by remember { mutableStateOf(NavigationTab.HOME) }
    var showAddTxDialog by remember { mutableStateOf(false) }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Handle back button: if not on Home, navigate back to Home first
    BackHandler(enabled = currentTab != NavigationTab.HOME) {
        currentTab = NavigationTab.HOME
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                modifier = Modifier.testTag("main_bottom_nav_bar")
            ) {
                NavigationTab.entries.forEach { tab ->
                    val isSelected = currentTab == tab
                    val hasAlert = tab == NavigationTab.BUDGETS && uiState.activeAlerts.isNotEmpty()

                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { currentTab = tab },
                        icon = {
                            if (hasAlert) {
                                BadgedBox(badge = { Badge { Text("${uiState.activeAlerts.size}") } }) {
                                    Icon(imageVector = tab.icon, contentDescription = tab.title)
                                }
                            } else {
                                Icon(imageVector = tab.icon, contentDescription = tab.title)
                            }
                        },
                        label = { Text(tab.title, fontSize = 11.sp) },
                        modifier = Modifier.testTag(tab.testTag)
                    )
                }
            }
        }
    ) { innerPadding ->
        val contentModifier = Modifier.padding(innerPadding)

        when (currentTab) {
            NavigationTab.HOME -> {
                HomeScreen(
                    uiState = uiState,
                    onSyncSmsClick = { fullScan -> viewModel.syncInboxMessages(force = true, fullScan = fullScan) },
                    onAddTransactionClick = { showAddTxDialog = true },
                    onSetInitialBalance = { viewModel.setInitialAccountBalance(it) },
                    onViewBudgetsClick = { currentTab = NavigationTab.BUDGETS },
                    onInspectJsonClick = { currentTab = NavigationTab.STORAGE },
                    onDismissSyncAlert = { viewModel.dismissSyncAlert() },
                    modifier = contentModifier
                )
            }

            NavigationTab.ANALYTICS -> {
                AnalyticsScreen(
                    uiState = uiState,
                    modifier = contentModifier
                )
            }

            NavigationTab.BUDGETS -> {
                BudgetsScreen(
                    uiState = uiState,
                    onSaveBudget = { viewModel.saveCategoryBudget(it) },
                    modifier = contentModifier
                )
            }

            NavigationTab.GOALS -> {
                GoalsScreen(
                    uiState = uiState,
                    onAddGoal = { viewModel.addFinancialGoal(it) },
                    onContributeGoal = { id, amount -> viewModel.contributeToGoal(id, amount) },
                    modifier = contentModifier
                )
            }

            NavigationTab.STORAGE -> {
                JsonStorageScreen(
                    uiState = uiState,
                    onLoadJsonText = { viewModel.getRawJsonContent() },
                    onReloadData = { viewModel.loadData() },
                    onExportBackup = { uri -> viewModel.exportJsonBackup(uri) },
                    onClearAllData = { viewModel.clearAllTransactions() },
                    onSetInitialBalance = { viewModel.setInitialAccountBalance(it) },
                    modifier = contentModifier
                )
            }
        }
    }

    if (showAddTxDialog) {
        AddTransactionDialog(
            onDismiss = { showAddTxDialog = false },
            onSave = { tx ->
                viewModel.addManualTransaction(tx)
                showAddTxDialog = false
            },
            currencySymbol = uiState.currencySymbol
        )
    }
}
