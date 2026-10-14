package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.ScreenshotCategory
import com.example.core.model.ScreenshotItem
import com.example.core.search.suggestion.CategorySuggestion
import com.example.core.search.suggestion.ProactiveSearchSuggestions
import com.example.ui.theme.AppleOrange

/**
 * Apple Spotlight Proactive Search View.
 * Displays Recent Searches, Dynamically Inferred Semantic Categories,
 * Local OCR Extracted Keywords, and Recently Indexed Screenshots.
 */
@Composable
fun ProactiveSearchSection(
    suggestions: ProactiveSearchSuggestions,
    onSelectQuery: (String) -> Unit,
    onSelectScreenshot: (ScreenshotItem) -> Unit,
    onRemoveRecentQuery: (String) -> Unit,
    onClearRecentQueries: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. RECENT SEARCHES SECTION (if available)
        if (suggestions.recentQueries.isNotEmpty()) {
            Column {
                AppleSectionHeader(
                    title = "Recent Searches",
                    trailingText = "Clear All",
                    trailingAction = onClearRecentQueries
                )

                AppleGroupedCard {
                    Column {
                        suggestions.recentQueries.forEachIndexed { index, query ->
                            RecentQueryRow(
                                query = query,
                                onClick = { onSelectQuery(query) },
                                onRemove = { onRemoveRecentQuery(query) },
                                showDivider = index < suggestions.recentQueries.size - 1
                            )
                        }
                    }
                }
            }
        }

        // 2. SUGGESTED CATEGORIES (Derived from Local Semantic Tags)
        if (suggestions.suggestedCategories.isNotEmpty()) {
            Column {
                AppleSectionHeader(
                    title = "Suggested Categories",
                    trailingText = "${suggestions.suggestedCategories.sumOf { it.count }} images"
                )

                // 2-column grid of Cupertino category tiles
                val chunkedCategories = suggestions.suggestedCategories.take(6).chunked(2)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (row in chunkedCategories) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (catSuggestion in row) {
                                CategorySuggestionTile(
                                    suggestion = catSuggestion,
                                    onClick = { onSelectQuery(catSuggestion.label) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (row.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        // 3. EXTRACTED OCR CONCEPTS & KEYWORDS (Mined from on-device OCR)
        if (suggestions.topOcrKeywords.isNotEmpty()) {
            Column {
                AppleSectionHeader(
                    title = "Extracted OCR Keywords",
                    trailingText = "Local OCR"
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    suggestions.topOcrKeywords.forEach { ocrSuggestion ->
                        AppleCapsuleChip(
                            selected = false,
                            onClick = { onSelectQuery(ocrSuggestion.keyword) },
                            label = ocrSuggestion.keyword,
                            badgeCount = ocrSuggestion.count,
                            icon = Icons.Rounded.Tag,
                            modifier = Modifier.testTag("ocr_keyword_chip_${ocrSuggestion.keyword}")
                        )
                    }
                }
            }
        }

        // 4. RECENTLY INDEXED PHOTOS & CAPTURES
        if (suggestions.recentItems.isNotEmpty()) {
            Column {
                AppleSectionHeader(
                    title = "Recently Indexed",
                    trailingText = "Latest"
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    items(suggestions.recentItems, key = { it.id }) { item ->
                        RecentItemThumbnailCard(
                            item = item,
                            onClick = { onSelectScreenshot(item) }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun RecentQueryRow(
    query: String,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    showDivider: Boolean
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(AppleOrange.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.History,
                        contentDescription = null,
                        tint = AppleOrange,
                        modifier = Modifier.size(15.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = query,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .applePressBounce(targetScale = 0.85f, onClick = onRemove)
                    .testTag("remove_recent_query_$query"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Clear,
                    contentDescription = "Remove query",
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 54.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }
    }
}

@Composable
private fun CategorySuggestionTile(
    suggestion: CategorySuggestion,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val icon = getCategoryIcon(suggestion.category)

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .border(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(14.dp)
            )
            .applePressBounce(targetScale = 0.96f, onClick = onClick)
            .testTag("category_tile_${suggestion.category.name}"),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(AppleOrange.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = AppleOrange,
                        modifier = Modifier.size(17.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = suggestion.label,
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 12.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "${suggestion.count}",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    fontWeight = FontWeight.Bold,
                    color = AppleOrange
                )
            }
        }
    }
}

@Composable
private fun RecentItemThumbnailCard(
    item: ScreenshotItem,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(88.dp)
            .applePressBounce(targetScale = 0.95f, onClick = onClick)
            .testTag("recent_item_${item.id}")
    ) {
        ScreenshotThumbnail(
            uriString = item.contentUri,
            modifier = Modifier
                .size(width = 88.dp, height = 118.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(
                    width = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(12.dp)
                )
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = item.displayName,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}

private fun getCategoryIcon(cat: ScreenshotCategory): ImageVector {
    return when (cat) {
        ScreenshotCategory.FINANCE_RECEIPTS -> Icons.Rounded.Receipt
        ScreenshotCategory.CODE_ERROR -> Icons.Rounded.Code
        ScreenshotCategory.RECIPES -> Icons.Rounded.Restaurant
        ScreenshotCategory.NETWORK_TECH -> Icons.Rounded.Wifi
        ScreenshotCategory.STUDY_NOTES -> Icons.Rounded.MenuBook
        ScreenshotCategory.SHOPPING_PRICES -> Icons.Rounded.ShoppingCart
        ScreenshotCategory.COMMUNICATION -> Icons.Rounded.Chat
        ScreenshotCategory.GENERAL_REFERENCE -> Icons.Rounded.Description
    }
}
