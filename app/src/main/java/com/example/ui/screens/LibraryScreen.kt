package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.AutoAwesome
import com.example.core.media.ScreenshotDiscovery
import com.example.core.model.DuplicateGroup
import com.example.core.model.IndexingStatus
import com.example.core.model.ScreenshotCategory
import com.example.core.model.ScreenshotItem
import com.example.ui.components.ScreenshotGridItem
import com.example.ui.components.ScreenshotThumbnail

enum class ImageSourceFilter(val displayName: String) {
    ALL("All"),
    SCREENSHOTS("Screenshots"),
    PHOTOS("Camera & Photos"),
    IMPORTED("Imported & Other")
}

@Composable
fun LibraryScreen(
    screenshots: List<ScreenshotItem>,
    duplicateGroups: List<DuplicateGroup>,
    onSelectScreenshot: (ScreenshotItem) -> Unit,
    onRescanMediaStore: () -> Unit,
    onSeedSamples: () -> Unit,
    onImportImages: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedSource by remember { mutableStateOf(ImageSourceFilter.ALL) }
    var selectedCategory by remember { mutableStateOf<ScreenshotCategory?>(null) }
    var selectedStatus by remember { mutableStateOf<IndexingStatus?>(null) }
    var showDuplicatesOnly by remember { mutableStateOf(false) }

    val filteredList = remember(screenshots, selectedSource, selectedCategory, selectedStatus) {
        screenshots.filter { item ->
            val matchSource = when (selectedSource) {
                ImageSourceFilter.ALL -> true
                ImageSourceFilter.SCREENSHOTS -> ScreenshotDiscovery.isScreenshot(item.displayName, item.relativePath)
                ImageSourceFilter.PHOTOS -> ScreenshotDiscovery.isCameraPhoto(item.displayName, item.relativePath)
                ImageSourceFilter.IMPORTED -> !ScreenshotDiscovery.isScreenshot(item.displayName, item.relativePath) &&
                        !ScreenshotDiscovery.isCameraPhoto(item.displayName, item.relativePath)
            }
            val matchCat = selectedCategory == null || item.categoryLabels.contains(selectedCategory)
            val matchStatus = selectedStatus == null || item.indexingStatus == selectedStatus
            matchSource && matchCat && matchStatus
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Library Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Image Library",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "${screenshots.size} images • ${duplicateGroups.size} duplicate clusters",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Row {
                IconButton(
                    onClick = onImportImages,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("import_images_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.AddPhotoAlternate,
                        contentDescription = "Import images from gallery",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(
                    onClick = onSeedSamples,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("seed_sample_screenshots_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Seed sample images",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(
                    onClick = onRescanMediaStore,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("rescan_mediastore_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Rescan MediaStore",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Duplicates Explorer Banner
        if (duplicateGroups.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clickable { showDuplicatesOnly = !showDuplicatesOnly }
                    .testTag("duplicates_toggle_card"),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (showDuplicatesOnly) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${duplicateGroups.size} duplicate image clusters detected",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Text(
                        text = if (showDuplicatesOnly) "View All" else "Explore",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        if (showDuplicatesOnly) {
            // Duplicate clusters list
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("duplicate_clusters_list"),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(duplicateGroups, key = { it.groupId }) { group ->
                    DuplicateGroupCard(
                        group = group,
                        onSelectScreenshot = onSelectScreenshot
                    )
                }
            }
        } else {
            // Source Filter Bar (All Images, Screenshots, Camera & Photos, Imported)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (source in ImageSourceFilter.entries) {
                    val count = when (source) {
                        ImageSourceFilter.ALL -> screenshots.size
                        ImageSourceFilter.SCREENSHOTS -> screenshots.count { ScreenshotDiscovery.isScreenshot(it.displayName, it.relativePath) }
                        ImageSourceFilter.PHOTOS -> screenshots.count { ScreenshotDiscovery.isCameraPhoto(it.displayName, it.relativePath) }
                        ImageSourceFilter.IMPORTED -> screenshots.count {
                            !ScreenshotDiscovery.isScreenshot(it.displayName, it.relativePath) &&
                            !ScreenshotDiscovery.isCameraPhoto(it.displayName, it.relativePath)
                        }
                    }
                    FilterChip(
                        selected = selectedSource == source,
                        onClick = { selectedSource = source },
                        label = { Text("${source.displayName} ($count)") },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.testTag("filter_chip_source_${source.name.lowercase()}")
                    )
                }
            }

            // Topic Categories Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { selectedCategory = null },
                    label = { Text("All Topics (${screenshots.size})") },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.testTag("filter_chip_all_topics")
                )

                for (cat in ScreenshotCategory.entries) {
                    val count = screenshots.count { it.categoryLabels.contains(cat) }
                    if (count > 0 || cat == ScreenshotCategory.CODE_ERROR || cat == ScreenshotCategory.RECIPES) {
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = if (selectedCategory == cat) null else cat },
                            label = { Text("${cat.displayName} ($count)") },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.testTag("filter_chip_cat_${cat.name}")
                        )
                    }
                }
            }

            // Grid Content
            if (filteredList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No images match the selected filter.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("screenshot_library_grid"),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredList, key = { it.id }) { item ->
                        ScreenshotGridItem(
                            item = item,
                            onClick = { onSelectScreenshot(item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DuplicateGroupCard(
    group: DuplicateGroup,
    onSelectScreenshot: (ScreenshotItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (group.isExactDuplicate) "Exact Binary Copies" else "Near-Duplicate Pair",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = group.similarityDescription,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            // Thumbnails of representative and duplicates
            val allInGroup = listOf(group.representative) + group.duplicates
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                for (item in allInGroup) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(90.dp)
                            .clickable { onSelectScreenshot(item) }
                    ) {
                        ScreenshotThumbnail(
                            uriString = item.contentUri,
                            modifier = Modifier
                                .size(width = 90.dp, height = 120.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = item.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
