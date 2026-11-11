package com.darkestdungeon.modmanager.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.darkestdungeon.modmanager.ui.theme.DDAndroidModManagerTheme

@Composable
fun PathPickerDialog(
    currentPath: String,
    onDismissRequest: () -> Unit,
    onConfirmPath: (String) -> Unit,
    onLaunchFolderPicker: () -> Unit
) {
    var pathInput by remember { mutableStateOf(currentPath) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = "Mod Storage Directory",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Specify the directory where Darkest Dungeon mods are extracted.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = pathInput,
                    onValueChange = { pathInput = it },
                    label = { Text("Directory Path") },
                    placeholder = { Text("/storage/emulated/0/DarkestDungeon/mods") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium
                )

                OutlinedButton(
                    onClick = onLaunchFolderPicker,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text("Browse System Directory")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmPath(pathInput) },
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel")
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun PathPickerDialogPreview() {
    DDAndroidModManagerTheme {
        PathPickerDialog(
            currentPath = "/storage/emulated/0/DarkestDungeon/mods",
            onDismissRequest = {},
            onConfirmPath = {},
            onLaunchFolderPicker = {}
        )
    }
}
