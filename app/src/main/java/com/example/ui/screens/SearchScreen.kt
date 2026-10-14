package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.IndexStats
import com.example.core.model.ScreenshotItem
import com.example.core.model.SearchResult
import com.example.core.qa.model.AskRecallUiState
import com.example.core.search.query.ParsedQuery
import com.example.core.search.query.SearchMode
import com.example.core.search.suggestion.ProactiveSearchSuggestions
import com.example.ui.components.AppleCapsuleChip
import com.example.ui.components.AppleSegmentedControl
import com.example.ui.components.AskRecallSection
import com.example.ui.components.IndexingProgressBanner
import com.example.ui.components.ProactiveSearchSection
import com.example.ui.components.SearchResultCard
import com.example.ui.components.applePressBounce
import com.example.ui.theme.AppleOrange

@Composable
fun SearchScreen(
    query: String,
    isSearching: Boolean,
    results: List<SearchResult>,
    stats: IndexStats,
    parsedQuery: ParsedQuery?,
    searchMode: SearchMode,
    onQueryChange: (String) -> Unit,
    onSearchModeChange: (SearchMode) -> Unit,
    onRemoveInferredFilter: (String) -> Unit,
    onToggleIndexing: () -> Unit,
    onSelectScreenshot: (ScreenshotItem) -> Unit,
    isAskMode: Boolean = false,
    askQuery: String = "",
    askUiState: AskRecallUiState = AskRecallUiState.Idle,
    allScreenshots: List<ScreenshotItem> = emptyList(),
    proactiveSuggestions: ProactiveSearchSuggestions = ProactiveSearchSuggestions(),
    onRemoveRecentQuery: (String) -> Unit = {},
    onClearRecentQueries: () -> Unit = {},
    onToggleAskMode: (Boolean) -> Unit = {},
    onAskQueryChange: (String) -> Unit = {},
    onSubmitAskQuestion: (String) -> Unit = {},
    onClearAskState: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val sampleQueries = listOf(
        "A red Ferrari at night",
        "A golden sunset over the ocean",
        "A cat sleeping on a sofa",
        "Recipe with chickpeas and coconut milk",
        "Wi-Fi password from school",
        "192.168.1.1 router gateway",
        "A product box with a green label",
        "The Python error I saw yesterday"
    )

    var isSearchFocused by remember { mutableStateOf(false) }

    val searchBorderColor by animateColorAsState(
        targetValue = if (isSearchFocused || query.isNotEmpty()) AppleOrange else MaterialTheme.colorScheme.outlineVariant,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "searchBorderColor"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Apple Large Title Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Recall",
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "On-Device Visual & Text Search",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            // Apple Minimalist Privacy Capsule
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .border(
                        width = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(14.dp)
                    )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(AppleOrange)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "100% Offline",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Apple Segmented Control: Search Matches vs Ask Recall (AI)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            AppleSegmentedControl(
                items = listOf(false, true),
                selectedItem = isAskMode,
                onItemSelected = { onToggleAskMode(it) },
                labelProvider = { if (!it) "Search Matches" else "Ask Recall (AI)" },
                iconProvider = { if (!it) Icons.Rounded.Search else Icons.Rounded.AutoAwesome },
                modifier = Modifier.testTag(if (isAskMode) "mode_chip_ask" else "mode_chip_search")
            )
        }

        if (isAskMode) {
            IndexingProgressBanner(
                stats = stats,
                onToggleIndexing = onToggleIndexing
            )

            AskRecallSection(
                query = askQuery,
                uiState = askUiState,
                allScreenshots = allScreenshots,
                onQueryChange = onAskQueryChange,
                onSubmitQuestion = onSubmitAskQuestion,
                onClear = onClearAskState,
                onSelectScreenshot = onSelectScreenshot
            )
        } else {
            // Apple Spotlight Search Bar with Animated Focus Glow
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .height(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(
                        width = if (isSearchFocused || query.isNotEmpty()) 1.dp else 0.5.dp,
                        color = searchBorderColor,
                        shape = RoundedCornerShape(16.dp)
                    ),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Search",
                        tint = if (query.isNotEmpty() || isSearchFocused) AppleOrange else MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(19.dp)
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .testTag("search_input_field"),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (query.isEmpty()) {
                            Text(
                                text = "Search photos, concepts, recipes, text…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }

                        BasicTextField(
                            value = query,
                            onValueChange = onQueryChange,
                            singleLine = true,
                            textStyle = TextStyle(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Normal
                            ),
                            cursorBrush = SolidColor(AppleOrange),
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { isSearchFocused = it.isFocused }
                        )
                    }

                    if (isSearching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = AppleOrange
                        )
                    } else if (query.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .applePressBounce(targetScale = 0.9f) {
                                    onQueryChange("")
                                }
                                .testTag("clear_search_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Clear search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            // Inferred Constraints Bar (Explain & Remove Inferred Filters)
            AnimatedVisibility(
                visible = parsedQuery != null && parsedQuery.inferredFilters.isNotEmpty()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (filter in parsedQuery?.inferredFilters ?: emptyList()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = AppleOrange.copy(alpha = 0.15f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .border(0.5.dp, AppleOrange.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                .applePressBounce(onClick = { onRemoveInferredFilter(filter.id) })
                                .testTag("inferred_filter_${filter.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = filter.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Remove filter",
                                    tint = AppleOrange,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Search Mode Selector Row (Hybrid, Semantic, Exact)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(15.dp)
                )

                for (mode in listOf(SearchMode.HYBRID, SearchMode.SEMANTIC_ONLY, SearchMode.EXACT_ONLY)) {
                    AppleCapsuleChip(
                        selected = searchMode == mode,
                        onClick = { onSearchModeChange(mode) },
                        label = mode.displayName,
                        modifier = Modifier.testTag("search_mode_${mode.name}")
                    )
                }
            }

            // Example Suggestions Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (suggestion in sampleQueries) {
                    AppleCapsuleChip(
                        selected = query == suggestion,
                        onClick = { onQueryChange(suggestion) },
                        label = suggestion,
                        modifier = Modifier.testTag("suggestion_chip_$suggestion")
                    )
                }
            }

            // Indexing Banner
            IndexingProgressBanner(
                stats = stats,
                onToggleIndexing = onToggleIndexing
            )

            // Content Area: Results or Proactive Spotlight Suggestions
            if (query.isBlank()) {
                ProactiveSearchSection(
                    suggestions = proactiveSuggestions,
                    onSelectQuery = { onQueryChange(it) },
                    onSelectScreenshot = onSelectScreenshot,
                    onRemoveRecentQuery = onRemoveRecentQuery,
                    onClearRecentQueries = onClearRecentQueries
                )
            } else if (results.isEmpty() && !isSearching) {
                NoResultsFound(query = query)
            } else {
                // Results list header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${results.size} MATCH${if (results.size == 1) "" else "ES"}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary,
                        letterSpacing = 0.8.sp
                    )

                    if (stats.lastSearchLatencyMs > 0) {
                        Text(
                            text = "${stats.lastSearchLatencyMs}ms • RRF Rank",
                            style = MaterialTheme.typography.labelSmall,
                            color = AppleOrange
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("search_results_list"),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(results, key = { it.screenshot.id }) { result ->
                        SearchResultCard(
                            result = result,
                            onClick = { onSelectScreenshot(result.screenshot) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NoResultsFound(query: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "No Matches Found",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "We couldn't find any screenshots related to \"$query\". Try switching search modes or verify image indexing in the Library tab.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )
    }
}
