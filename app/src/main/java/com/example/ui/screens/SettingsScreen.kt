package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.AppleGroupedCard
import com.example.ui.components.AppleGroupedRow
import com.example.ui.components.AppleSectionHeader
import com.example.ui.components.applePressBounce
import com.example.ui.theme.AppleOrange
import com.example.ui.theme.StatusFailedColor

@Composable
fun SettingsScreen(
    onRescanMediaStore: () -> Unit,
    onSeedSamples: () -> Unit,
    onRebuildIndex: () -> Unit,
    onClearIndex: () -> Unit,
    onRetryFailed: () -> Unit = {},
    onImportImages: () -> Unit = {},
    scopeScreenshotsOnly: Boolean = false,
    onToggleScopeScreenshotsOnly: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showClearDialog by remember { mutableStateOf(false) }
    var showRebuildDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Apple Large Title
        Column(modifier = Modifier.padding(horizontal = 4.dp)) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Privacy, Image Sources, and Local Index Controls",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        // Privacy Section
        AppleSectionHeader(title = "Privacy & Security")
        AppleGroupedCard {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(AppleOrange.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Security,
                            contentDescription = null,
                            tint = AppleOrange,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "100% On-Device Air-Gapped",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    PrivacyBullet("Zero network calls or telemetry. No internet permission.")
                    PrivacyBullet("All OCR and 384D vectors reside in encrypted local app storage.")
                    PrivacyBullet("Works completely offline and in Airplane Mode.")
                }
            }
        }

        // Discovery Scope Section
        AppleSectionHeader(title = "Discovery Scope")
        AppleGroupedCard {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (scopeScreenshotsOnly) "Screenshots Only" else "All Device Photos",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (scopeScreenshotsOnly)
                                "Scans dedicated screenshot folders only"
                            else
                                "Scans camera photos, downloads, and screenshots",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    Switch(
                        checked = !scopeScreenshotsOnly,
                        onCheckedChange = { onToggleScopeScreenshotsOnly(!it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = AppleOrange,
                            uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.testTag("settings_all_images_switch")
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(start = 16.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                AppleGroupedRow(
                    title = "Select Photos from Gallery",
                    leadingIcon = Icons.Rounded.AddPhotoAlternate,
                    trailingIcon = Icons.Rounded.ChevronRight,
                    showDivider = false,
                    onClick = onImportImages
                )
            }
        }

        // Index Management Section
        AppleSectionHeader(title = "Index Controls")
        AppleGroupedCard {
            Column {
                AppleGroupedRow(
                    title = "Rescan Device Images",
                    leadingIcon = Icons.Rounded.Sync,
                    trailingIcon = Icons.Rounded.ChevronRight,
                    showDivider = true,
                    onClick = onRescanMediaStore
                )

                AppleGroupedRow(
                    title = "Seed Sample Images (Recipes, Notes)",
                    leadingIcon = Icons.Rounded.AddPhotoAlternate,
                    trailingIcon = Icons.Rounded.ChevronRight,
                    showDivider = true,
                    onClick = onSeedSamples
                )

                AppleGroupedRow(
                    title = "Rebuild Vector Index",
                    leadingIcon = Icons.Rounded.Refresh,
                    trailingIcon = Icons.Rounded.ChevronRight,
                    showDivider = true,
                    onClick = { showRebuildDialog = true }
                )

                AppleGroupedRow(
                    title = "Retry Quarantined Images",
                    leadingIcon = Icons.Rounded.Refresh,
                    trailingIcon = Icons.Rounded.ChevronRight,
                    showDivider = true,
                    onClick = onRetryFailed
                )

                AppleGroupedRow(
                    title = "Clear Local Index",
                    leadingIcon = Icons.Rounded.DeleteSweep,
                    accentColor = StatusFailedColor,
                    trailingIcon = Icons.Rounded.ChevronRight,
                    showDivider = false,
                    onClick = { showClearDialog = true }
                )
            }
        }

        // About Section
        AppleSectionHeader(title = "About Recall")
        AppleGroupedCard {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                SettingsAboutRow("Version", "1.0 (Build 2026.10)")
                SettingsAboutRow("Target Platform", "Android 15 (minSdk 35)")
                SettingsAboutRow("Model", "all-MiniLM-L6-v2 (Apache 2.0)")
                SettingsAboutRow("Design", "Monochrome Minimalist + Orange", isLast = true)
            }
        }
    }

    // Clear Index Confirmation Dialog
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = {
                Text(
                    text = "Clear Local Search Index?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "This clears the OCR text and vector embeddings from Recall's database. Your original photos and screenshots on your device will NOT be deleted.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onClearIndex()
                        showClearDialog = false
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = StatusFailedColor)
                ) {
                    Text("Clear Index", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurface)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(18.dp)
        )
    }

    // Rebuild Index Confirmation Dialog
    if (showRebuildDialog) {
        AlertDialog(
            onDismissRequest = { showRebuildDialog = false },
            title = {
                Text(
                    text = "Rebuild Vector Index?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "This resets all cached embeddings and re-runs on-device sentence transformer embeddings across all screenshots.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRebuildIndex()
                        showRebuildDialog = false
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppleOrange)
                ) {
                    Text("Rebuild", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRebuildDialog = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurface)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(18.dp)
        )
    }
}

@Composable
private fun PrivacyBullet(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            imageVector = Icons.Rounded.Check,
            contentDescription = null,
            tint = AppleOrange,
            modifier = Modifier
                .size(16.dp)
                .padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingsAboutRow(label: String, value: String, isLast: Boolean = false) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary
            )
        }
        if (!isLast) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 16.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }
    }
}
