package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MultilineChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.ScooterViewModel
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.ScannerScreen
import com.example.ui.screens.ChartScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.KirinTheme

enum class AppTab {
    DASHBOARD,
    SCANNER,
    CHART,
    HISTORY,
    SETTINGS
}

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            Toast.makeText(this, "Bluetooth-Berechtigungen erteilt!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Bluetooth eingeschränkt. Bitte in den Einstellungen erteilen.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Check BLE and location permissions on launch
        checkAndRequestPermissions()

        setContent {
            var isDarkTheme by remember { mutableStateOf(true) } // Start with sporty dark theme
            var currentTab by remember { mutableStateOf(AppTab.DASHBOARD) }

            KirinTheme(darkTheme = isDarkTheme) {
                val viewModel: ScooterViewModel = viewModel(
                    factory = ScooterViewModel.Factory(application)
                )

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        KirinTopAppBar()
                    },
                    bottomBar = {
                        KirinBottomNavigation(
                            currentTab = currentTab,
                            onTabSelected = { currentTab = it }
                        )
                    }
                ) { innerPadding ->
                    val screenModifier = Modifier.padding(innerPadding)

                    when (currentTab) {
                        AppTab.DASHBOARD -> DashboardScreen(
                            viewModel = viewModel,
                            modifier = screenModifier
                        )
                        AppTab.SCANNER -> ScannerScreen(
                            viewModel = viewModel,
                            modifier = screenModifier
                        )
                        AppTab.CHART -> ChartScreen(
                            viewModel = viewModel,
                            modifier = screenModifier
                        )
                        AppTab.HISTORY -> HistoryScreen(
                            viewModel = viewModel,
                            modifier = screenModifier
                        )
                        AppTab.SETTINGS -> SettingsScreen(
                            viewModel = viewModel,
                            isDarkTheme = isDarkTheme,
                            onToggleTheme = { isDarkTheme = it },
                            modifier = screenModifier
                        )
                    }
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }

        val neededPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (neededPermissions.isNotEmpty()) {
            requestPermissionLauncher.launch(neededPermissions)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KirinTopAppBar() {
    TopAppBar(
        title = {
            Text(
                text = "KirinControl",
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.titleLarge
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground
        ),
        modifier = Modifier
            .statusBarsPadding()
            .testTag("app_top_bar")
    )
}

@Composable
fun KirinBottomNavigation(
    currentTab: AppTab,
    onTabSelected: (AppTab) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        modifier = Modifier.testTag("app_bottom_nav")
    ) {
        NavigationBarItem(
            selected = currentTab == AppTab.DASHBOARD,
            onClick = { onTabSelected(AppTab.DASHBOARD) },
            icon = { Icon(imageVector = Icons.Default.Home, contentDescription = "Cockpit") },
            label = { Text("Cockpit") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            ),
            modifier = Modifier.testTag("nav_dashboard")
        )

        NavigationBarItem(
            selected = currentTab == AppTab.SCANNER,
            onClick = { onTabSelected(AppTab.SCANNER) },
            icon = { Icon(imageVector = Icons.Default.Bluetooth, contentDescription = "Bluetooth") },
            label = { Text("Verbindung") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            ),
            modifier = Modifier.testTag("nav_scanner")
        )

        NavigationBarItem(
            selected = currentTab == AppTab.CHART,
            onClick = { onTabSelected(AppTab.CHART) },
            icon = { Icon(imageVector = Icons.Default.MultilineChart, contentDescription = "Diagramm") },
            label = { Text("Diagramm") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            ),
            modifier = Modifier.testTag("nav_chart")
        )

        NavigationBarItem(
            selected = currentTab == AppTab.HISTORY,
            onClick = { onTabSelected(AppTab.HISTORY) },
            icon = { Icon(imageVector = Icons.Default.History, contentDescription = "Logbuch") },
            label = { Text("Logbuch") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            ),
            modifier = Modifier.testTag("nav_history")
        )

        NavigationBarItem(
            selected = currentTab == AppTab.SETTINGS,
            onClick = { onTabSelected(AppTab.SETTINGS) },
            icon = { Icon(imageVector = Icons.Default.Settings, contentDescription = "Info") },
            label = { Text("Info") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            ),
            modifier = Modifier.testTag("nav_settings")
        )
    }
}
