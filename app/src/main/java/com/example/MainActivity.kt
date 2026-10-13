package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.screens.DiagnosticsScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.ScreenshotDetailScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MainViewModel

enum class NavigationTab(val label: String) {
    SEARCH("Search"),
    LIBRARY("Library"),
    DIAGNOSTICS("Diagnostics"),
    SETTINGS("Settings")
}

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                MainAppContainer(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun MainAppContainer(viewModel: MainViewModel) {
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf(NavigationTab.SEARCH) }

    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchMode by viewModel.searchMode.collectAsStateWithLifecycle()
    val parsedQuery by viewModel.parsedQuery.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val allScreenshots by viewModel.allScreenshots.collectAsStateWithLifecycle()
    val duplicateGroups by viewModel.duplicateGroups.collectAsStateWithLifecycle()
    val indexStats by viewModel.indexStats.collectAsStateWithLifecycle()
    val selectedScreenshot by viewModel.selectedScreenshot.collectAsStateWithLifecycle()
    val visuallySimilarResults by viewModel.visuallySimilarResults.collectAsStateWithLifecycle()
    val benchmarkReport by viewModel.benchmarkReport.collectAsStateWithLifecycle()
    val isBenchmarking by viewModel.isBenchmarking.collectAsStateWithLifecycle()

    // Android 14/15 Permission Handling
    val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        )
    } else {
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.discoverScreenshots()
    }

    LaunchedEffect(Unit) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_MEDIA_IMAGES
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            permissionLauncher.launch(permissionsToRequest)
        } else {
            viewModel.discoverScreenshots()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (selectedScreenshot == null) {
                NavigationBar(
                    modifier = Modifier.testTag("bottom_navigation_bar")
                ) {
                    NavigationBarItem(
                        selected = currentTab == NavigationTab.SEARCH,
                        onClick = { currentTab = NavigationTab.SEARCH },
                        icon = { Icon(Icons.Default.Search, contentDescription = "Search tab") },
                        label = { Text(NavigationTab.SEARCH.label) },
                        modifier = Modifier.testTag("nav_item_search")
                    )

                    NavigationBarItem(
                        selected = currentTab == NavigationTab.LIBRARY,
                        onClick = { currentTab = NavigationTab.LIBRARY },
                        icon = { Icon(Icons.Default.PhotoLibrary, contentDescription = "Library tab") },
                        label = { Text(NavigationTab.LIBRARY.label) },
                        modifier = Modifier.testTag("nav_item_library")
                    )

                    NavigationBarItem(
                        selected = currentTab == NavigationTab.DIAGNOSTICS,
                        onClick = { currentTab = NavigationTab.DIAGNOSTICS },
                        icon = { Icon(Icons.Default.Assessment, contentDescription = "Diagnostics tab") },
                        label = { Text(NavigationTab.DIAGNOSTICS.label) },
                        modifier = Modifier.testTag("nav_item_diagnostics")
                    )

                    NavigationBarItem(
                        selected = currentTab == NavigationTab.SETTINGS,
                        onClick = { currentTab = NavigationTab.SETTINGS },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings tab") },
                        label = { Text(NavigationTab.SETTINGS.label) },
                        modifier = Modifier.testTag("nav_item_settings")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (selectedScreenshot != null) {
                ScreenshotDetailScreen(
                    item = selectedScreenshot!!,
                    similarScreenshots = visuallySimilarResults,
                    onBack = { viewModel.selectScreenshot(null) },
                    onReindex = { viewModel.reindexScreenshot(it) },
                    onSelectScreenshot = { viewModel.selectScreenshot(it) }
                )
            } else {
                when (currentTab) {
                    NavigationTab.SEARCH -> SearchScreen(
                        query = searchQuery,
                        isSearching = isSearching,
                        results = searchResults,
                        stats = indexStats,
                        parsedQuery = parsedQuery,
                        searchMode = searchMode,
                        onQueryChange = { viewModel.onQueryChange(it) },
                        onSearchModeChange = { viewModel.onSearchModeChange(it) },
                        onRemoveInferredFilter = { viewModel.removeInferredFilter(it) },
                        onToggleIndexing = { viewModel.toggleIndexing() },
                        onSelectScreenshot = { viewModel.selectScreenshot(it) }
                    )

                    NavigationTab.LIBRARY -> LibraryScreen(
                        screenshots = allScreenshots,
                        duplicateGroups = duplicateGroups,
                        onSelectScreenshot = { viewModel.selectScreenshot(it) },
                        onRescanMediaStore = { viewModel.discoverScreenshots() },
                        onSeedSamples = { viewModel.seedSampleScreenshots() }
                    )

                    NavigationTab.DIAGNOSTICS -> DiagnosticsScreen(
                        stats = indexStats,
                        benchmarkReport = benchmarkReport,
                        isBenchmarking = isBenchmarking,
                        onRunBenchmark = { viewModel.runDiagnosticsBenchmark() }
                    )

                    NavigationTab.SETTINGS -> SettingsScreen(
                        onRescanMediaStore = { viewModel.discoverScreenshots() },
                        onSeedSamples = { viewModel.seedSampleScreenshots() },
                        onRebuildIndex = { viewModel.resetAllForReindexing() },
                        onClearIndex = { viewModel.clearIndex() }
                    )
                }
            }
        }
    }
}
