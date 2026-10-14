package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.model.MediaPermissionState
import com.example.ui.screens.DiagnosticsScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.ScreenshotDetailScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.AppleOrange
import com.example.ui.theme.AppleOrangeDark
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MainViewModel

enum class NavigationTab(val label: String, val activeIcon: ImageVector, val inactiveIcon: ImageVector) {
    SEARCH("Search", Icons.Rounded.Search, Icons.Outlined.Search),
    LIBRARY("Library", Icons.Rounded.PhotoLibrary, Icons.Outlined.PhotoLibrary),
    DIAGNOSTICS("Diagnostics", Icons.Rounded.Assessment, Icons.Outlined.Assessment),
    SETTINGS("Settings", Icons.Rounded.Settings, Icons.Outlined.Settings)
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
    val scopeScreenshotsOnly by viewModel.scopeScreenshotsOnly.collectAsStateWithLifecycle()
    val mediaPermissionState by viewModel.mediaPermissionState.collectAsStateWithLifecycle()
    val isAskMode by viewModel.isAskMode.collectAsStateWithLifecycle()
    val askQuery by viewModel.askQuery.collectAsStateWithLifecycle()
    val askUiState by viewModel.askUiState.collectAsStateWithLifecycle()
    val proactiveSuggestions by viewModel.proactiveSuggestions.collectAsStateWithLifecycle()

    // Handle back button when inspecting a screenshot detail
    if (selectedScreenshot != null) {
        BackHandler {
            viewModel.selectScreenshot(null)
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importImages(uris)
        }
    }

    // Android 14/15 Permission Handling with Background Notification Support
    val permissionsToRequest = remember {
        buildList {
            add(Manifest.permission.READ_MEDIA_IMAGES)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.updatePermissionState()
        viewModel.discoverImages()
    }

    LaunchedEffect(Unit) {
        viewModel.updatePermissionState()
        if (viewModel.mediaPermissionState.value == MediaPermissionState.DENIED) {
            permissionLauncher.launch(permissionsToRequest)
        } else {
            viewModel.discoverImages()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (selectedScreenshot == null) {
                AppleMinimalistTabBar(
                    currentTab = currentTab,
                    onTabSelected = { currentTab = it }
                )
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
                AnimatedContent(
                    targetState = currentTab,
                    transitionSpec = {
                        fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMedium)) togetherWith
                                fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMedium))
                    },
                    label = "tabTransition"
                ) { targetTab ->
                    when (targetTab) {
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
                            onSelectScreenshot = { viewModel.selectScreenshot(it) },
                            isAskMode = isAskMode,
                            askQuery = askQuery,
                            askUiState = askUiState,
                            proactiveSuggestions = proactiveSuggestions,
                            onRemoveRecentQuery = { viewModel.removeRecentQuery(it) },
                            onClearRecentQueries = { viewModel.clearRecentQueries() },
                            allScreenshots = allScreenshots,
                            onToggleAskMode = { viewModel.setAskMode(it) },
                            onAskQueryChange = { viewModel.onAskQueryChange(it) },
                            onSubmitAskQuestion = { viewModel.submitAskQuestion(it) },
                            onClearAskState = { viewModel.clearAskState() }
                        )

                        NavigationTab.LIBRARY -> LibraryScreen(
                            screenshots = allScreenshots,
                            duplicateGroups = duplicateGroups,
                            onSelectScreenshot = { viewModel.selectScreenshot(it) },
                            onRescanMediaStore = { viewModel.discoverImages() },
                            onSeedSamples = { viewModel.seedSampleScreenshots() },
                            onImportImages = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            mediaPermissionState = mediaPermissionState,
                            onRequestPermissions = { permissionLauncher.launch(permissionsToRequest) }
                        )

                        NavigationTab.DIAGNOSTICS -> DiagnosticsScreen(
                            stats = indexStats,
                            benchmarkReport = benchmarkReport,
                            isBenchmarking = isBenchmarking,
                            onRunBenchmark = { viewModel.runDiagnosticsBenchmark() },
                            onRetryFailed = { viewModel.retryFailedImages() }
                        )

                        NavigationTab.SETTINGS -> SettingsScreen(
                            onRescanMediaStore = { viewModel.discoverImages() },
                            onSeedSamples = { viewModel.seedSampleScreenshots() },
                            onRebuildIndex = { viewModel.resetAllForReindexing() },
                            onClearIndex = { viewModel.clearIndex() },
                            onRetryFailed = { viewModel.retryFailedImages() },
                            onImportImages = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            scopeScreenshotsOnly = scopeScreenshotsOnly,
                            onToggleScopeScreenshotsOnly = { viewModel.setScopeScreenshotsOnly(it) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Apple-style minimalist tab bar with translucent surface, hairline divider, and smooth icon spring microinteractions.
 */
@Composable
private fun AppleMinimalistTabBar(
    currentTab: NavigationTab,
    onTabSelected: (NavigationTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("bottom_navigation_bar"),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            // Apple hairline top border
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NavigationTab.entries.forEach { tab ->
                    val isSelected = tab == currentTab
                    val scale by animateFloatAsState(
                        targetValue = if (isSelected) 1.05f else 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "tabIconScale"
                    )

                    val testTagKey = when (tab) {
                        NavigationTab.SEARCH -> "nav_item_search"
                        NavigationTab.LIBRARY -> "nav_item_library"
                        NavigationTab.DIAGNOSTICS -> "nav_item_diagnostics"
                        NavigationTab.SETTINGS -> "nav_item_settings"
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onTabSelected(tab) }
                            )
                            .testTag(testTagKey)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.scale(scale)
                        ) {
                            Icon(
                                imageVector = if (isSelected) tab.activeIcon else tab.inactiveIcon,
                                contentDescription = "${tab.label} tab",
                                tint = if (isSelected) AppleOrange else MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(23.dp)
                            )
                        }

                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) AppleOrange else MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
