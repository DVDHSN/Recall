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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.example.core.model.DuplicateGroup
import com.example.core.model.ImageSource
import com.example.core.model.IndexingStatus
import com.example.core.model.MediaPermissionState
import com.example.core.model.ScreenshotCategory
import com.example.core.model.ScreenshotItem
import com.example.ui.components.ScreenshotGridItem
import com.example.ui.components.ScreenshotThumbnail

enum class ImageSourceFilter(val displayName: String, val source: ImageSource?) {
    ALL("All", null),
    PHOTOS("Camera Photos", ImageSource.CAMERA),
    SCREENSHOTS("Screenshots", ImageSource.SCREENSHOT),
    DOWNLOADS("Downloads", ImageSource.DOWNLOAD),
    SOCIAL("Social & Chats", ImageSource.SOCIAL_MESSAGING),
    NOTES("Notes & Docs", ImageSource.DOCUMENTS_NOTES),
    IMPORTED("Imported", ImageSource.IMPORTED)
}

@Composable
fun LibraryScreen(
    screenshots: List<ScreenshotItem>,
    duplicateGroups: List<DuplicateGroup>,
    onSelectScreenshot: (ScreenshotItem) -> Unit,
    onRescanMediaStore: () -> Unit,
    onSeedSamples: () -> Unit,
    onImportImages: () -> Unit = {},
    mediaPermissionState: MediaPermissionState = MediaPermissionState.FULL_ACCESS,
    onRequestPermissions: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedSource by remember { mutableStateOf(ImageSourceFilter.ALL) }
    var selectedCategory by remember { mutableStateOf<ScreenshotCategory?>(null) }
    var selectedStatus by remember { mutableStateOf<IndexingStatus?>(null) }
    var showDuplicatesOnly by remember { mutableStateOf(false) }

    val filteredList = remember(screenshots, selectedSource, selectedCategory, selectedStatus) {
        screenshots.filter { item ->
            val matchSource = when (selectedSource.source) {
                null -> true
                else -> item.getImageSourceEnum() == selectedSource.source
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
                    text = "Universal Library",
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

        // Permission Notice Banners
        if (mediaPermissionState == MediaPermissionState.DENIED) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("permission_denied_card"),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Media Access Denied",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Text(
                        text = "Recall requires permission to discover and index photos, screenshots, and downloads locally. You can also import specific photos without permissions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onRequestPermissions,
                            modifier = Modifier.testTag("grant_permission_button")
                        ) {
                            Text("Grant Access")
                        }
                        OutlinedButton(
                            onClick = onImportImages,
                            modifier = Modifier.testTag("import_picker_button")
                        ) {
                            Text("Select Photos")
                        }
                    }
                }
            }
        } else if (mediaPermissionState == MediaPermissionState.PARTIAL_ACCESS) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .testTag("partial_access_card"),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Partial Photo Access Active",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                text = "Only selected photos are accessible to Recall.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                    Button(
                        onClick = onRequestPermissions,
                        modifier = Modifier.testTag("manage_selection_button")
                    ) {
                        Text("Select More")
                    }
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
            // Source Filter Bar (All Images, Camera Photos, Screenshots, Downloads, Social & Chats, Notes & Docs, Imported)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (source in ImageSourceFilter.entries) {
                    val count = when (source.source) {
                        null -> screenshots.size
                        else -> screenshots.count { it.getImageSourceEnum() == source.source }
                    }
                    if (count > 0 || source == ImageSourceFilter.ALL || source == ImageSourceFilter.PHOTOS || source == ImageSourceFilter.SCREENSHOTS) {
                        FilterChip(
                            selected = selectedSource == source,
                            onClick = { selectedSource = source },
                            label = { Text("${source.displayName} ($count)") },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.testTag("filter_chip_source_${source.name.lowercase()}")
                        )
                    }
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
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = if (screenshots.isEmpty()) "No images in library yet" else "No images match the selected filter.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                        if (screenshots.isEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(onClick = onImportImages) {
                                    Text("Select Photos")
                                }
                                OutlinedButton(onClick = onSeedSamples) {
                                    Text("Seed Sample Images")
                                }
                            }
                        }
                    }
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
