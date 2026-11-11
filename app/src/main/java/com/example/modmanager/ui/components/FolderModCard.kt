package com.darkestdungeon.modmanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darkestdungeon.modmanager.data.model.Mod
import com.darkestdungeon.modmanager.ui.theme.StatusEnabledGreen

@Composable
fun FolderModCard(
    mod: Mod,
    /** Active = present in the current load order (source of truth), NOT merely the folder's location. */
    isActive: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onSelectionChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ModPreviewImage(
                mod = mod,
                size = 68.dp,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )

            Spacer(Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    mod.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp),
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Clip
                )
                if (mod.author.isKnownModder()) {
                    Text(
                        mod.author,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    mod.folderName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = if (isActive) "Active" else "Inactive",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isActive) StatusEnabledGreen else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onSelectionChanged(it) }
                )
            }
        }
    }
}

private fun String.isKnownModder(): Boolean =
    isNotBlank() && !contains("unknown", ignoreCase = true)
