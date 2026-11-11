package com.darkestdungeon.modmanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darkestdungeon.modmanager.data.model.Mod
import com.darkestdungeon.modmanager.ui.theme.DDAndroidModManagerTheme
import com.darkestdungeon.modmanager.ui.theme.StatusEnabledGreen

@Composable
fun ModCard(
    mod: Mod,
    isFirst: Boolean,
    isLast: Boolean,
    onToggleEnabled: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onClick: () -> Unit,
    showToggle: Boolean = true,
    showReorderControls: Boolean = true,
    showSelectionCheckbox: Boolean = false,
    isSelected: Boolean = false,
    onSelectionChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (mod.isEnabled) {
                MaterialTheme.colorScheme.surfaceContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f)
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Load order reorder controls column
            if (showReorderControls) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(end = 8.dp)
            ) {
                IconButton(
                    onClick = onMoveUp,
                    enabled = !isFirst,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowUp,
                        contentDescription = "Move Up",
                        tint = if (!isFirst) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )
                }

                // Load Order Position Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (mod.isEnabled) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "#${mod.loadOrder + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (mod.isEnabled) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onMoveDown,
                    enabled = !isLast,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Move Down",
                        tint = if (!isLast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )
                }
                }
            }

            ModPreviewImage(
                mod = mod,
                size = 64.dp,
                modifier = Modifier.border(
                    width = 1.dp,
                    color = if (mod.isEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(8.dp)
                )
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Main mod details info column
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = mod.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp),
                        fontWeight = FontWeight.Bold,
                        color = if (mod.isEnabled) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 3,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.weight(1f)
                    )

                    // Version Tag
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "v${mod.version}",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Hide the metadata fallback and keep author presentation consistent.
                if (mod.author.isKnownModder()) {
                    Text(
                        text = mod.author,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Description summary
                if (mod.description.isNotBlank()) {
                    Text(
                        text = mod.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Keep tags compact so metadata stays on one predictable line.
                if (mod.tags.isNotEmpty()) {
                    Text(
                        text = mod.tags.joinToString(" • "),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(top = 2.dp)
                    )
                }
            }

            if (showSelectionCheckbox) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "#${mod.loadOrder + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = onSelectionChanged
                    )
                }
            }

            // Enable / Disable switch
            if (showToggle) Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Switch(
                    checked = mod.isEnabled,
                    onCheckedChange = { onToggleEnabled() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = StatusEnabledGreen,
                        uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                )

                Text(
                    text = if (mod.isEnabled) "ACTIVE" else "OFF",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    fontWeight = FontWeight.Bold,
                    color = if (mod.isEnabled) StatusEnabledGreen else MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

private fun String.isKnownModder(): Boolean =
    isNotBlank() && !contains("unknown", ignoreCase = true)

@Preview(showBackground = true)
@Composable
fun ModCardPreview() {
    DDAndroidModManagerTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ModCard(
                mod = Mod(
                    id = "falconer",
                    title = "The Falconer Hero Class",
                    description = "Adds a brand new ranged marksman hero class complete with custom trinkets, camping skills, and artwork.",
                    author = "Marney & Team",
                    version = "2.4",
                    folderName = "falconer_class_mod",
                    folderPath = "/storage/emulated/0/DarkestDungeon/mods/falconer",
                    isEnabled = true,
                    loadOrder = 0,
                    tags = listOf("Hero Class", "Trinkets")
                ),
                isFirst = true,
                isLast = false,
                onToggleEnabled = {},
                onMoveUp = {},
                onMoveDown = {},
                onClick = {}
            )

            ModCard(
                mod = Mod(
                    id = "faster_walk",
                    title = "Faster Dungeon Walking Speed",
                    description = "Increases party walking speed in dungeons for quicker runs.",
                    author = "ModderX",
                    version = "1.0",
                    folderName = "qol_walk_speed",
                    folderPath = "/storage/emulated/0/DarkestDungeon/mods/qol_walk_speed",
                    isEnabled = false,
                    loadOrder = 1,
                    tags = listOf("QoL", "Speed")
                ),
                isFirst = false,
                isLast = true,
                onToggleEnabled = {},
                onMoveUp = {},
                onMoveDown = {},
                onClick = {}
            )
        }
    }
}
