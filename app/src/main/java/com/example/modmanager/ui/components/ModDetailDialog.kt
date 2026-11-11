package com.darkestdungeon.modmanager.ui.components

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.darkestdungeon.modmanager.data.model.Mod
import com.darkestdungeon.modmanager.ui.screens.ModDetailScreen

@Composable
fun ModDetailDialog(
    mod: Mod?,
    allMods: List<Mod>,
    onDismissRequest: () -> Unit,
    onToggleEnabled: (String) -> Unit,
    onMoveUp: (String) -> Unit,
    onMoveDown: (String) -> Unit
) {
    if (mod == null) return

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f)
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        ) {
            ModDetailScreen(
                mod = mod,
                onBack = onDismissRequest,
                onToggleEnabled = onToggleEnabled,
                onMoveUp = onMoveUp,
                onMoveDown = onMoveDown,
                allMods = allMods
            )
        }
    }
}
