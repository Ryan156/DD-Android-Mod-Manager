package com.darkestdungeon.modmanager.ui.screens

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.darkestdungeon.modmanager.R
import com.darkestdungeon.modmanager.data.model.ModCategory
import com.darkestdungeon.modmanager.ui.AppLanguage
import com.darkestdungeon.modmanager.ui.localizedCategoryName
import com.darkestdungeon.modmanager.ui.theme.DDAndroidModManagerTheme
import com.darkestdungeon.modmanager.ui.viewmodel.CategoryAssignmentReport

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    activeFolderPath: String,
    gameSaveUriStr: String?,
    gameLoadOrderCount: Int,
    sourceGameSaveUriStr: String?,
    sourceGameLoadOrderCount: Int,
    totalModsCount: Int,
    enabledModsCount: Int,
    disabledModsCount: Int,
    onBack: () -> Unit,
    onSavePath: (String) -> Unit,
    onLaunchFolderPicker: () -> Unit,
    onLaunchSaveFilePicker: () -> Unit,
    onLaunchSourceSaveFilePicker: () -> Unit,
    onEnableAll: () -> Unit,
    onDisableAll: () -> Unit,
    onRescanMods: () -> Unit,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    statusMessage: String? = null,
    onClearError: () -> Unit = {},
    onClearStatus: () -> Unit = {},
    categories: List<ModCategory> = ModCategory.defaults(),
    selectedCategoryId: String = ModCategory.BUILT_IN.first().id,
    onSelectCategory: (String) -> Unit = {},
    onAddCategory: (String) -> Unit = {},
    onRenameCategory: (String, String) -> Unit = { _, _ -> },
    onMoveCategoryUp: (String) -> Unit = {},
    onMoveCategoryDown: (String) -> Unit = {},
    onResetBuiltInCategoryOrder: () -> Unit = {},
    onAutoCategorize: () -> Unit = {},
    onAutoSort: () -> Unit = {},
    categoryAssignmentReport: CategoryAssignmentReport? = null,
    onClearCategoryAssignmentReport: () -> Unit = {},
    onSetCategoryColor: (String, String) -> Unit = { _, _ -> },
    onResetCategoryColor: (String) -> Unit = {},
    onRemoveCategory: (String) -> Unit = {},
    currentLanguageTag: String = "",
    onLanguageSelected: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var pathInput by remember(activeFolderPath) { mutableStateOf(activeFolderPath) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var renameCategory by remember { mutableStateOf<ModCategory?>(null) }
    var colorCategory by remember { mutableStateOf<ModCategory?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    categoryAssignmentReport?.let { report ->
        AlertDialog(
            onDismissRequest = onClearCategoryAssignmentReport,
            confirmButton = {
                TextButton(onClick = onClearCategoryAssignmentReport) { Text(stringResource(R.string.done)) }
            },
            title = { Text(stringResource(R.string.auto_categorize)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (report.assignedCount == 0) {
                            "No confident matches"
                        } else {
                            "Assigned ${report.assignedCount} mods"
                        },
                        fontWeight = FontWeight.Bold
                    )
                    if (report.unassignedModNames.isNotEmpty()) {
                        Text(
                            "Still [Unassigned] (${report.unassignedModNames.size})",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 320.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            report.unassignedModNames.forEach { name ->
                                Text("• $name", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        )
    }



    LaunchedEffect(errorMessage) {
        errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Long)
            onClearError()
        }
    }

    LaunchedEffect(statusMessage) {
        statusMessage?.let { message ->
            snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Long)
            onClearStatus()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_storage)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            // Storage Path Configuration Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = stringResource(R.string.configure_storage),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = stringResource(R.string.mod_directory_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = pathInput,
                        onValueChange = { pathInput = it },
                        label = { Text(stringResource(R.string.mod_path)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onLaunchFolderPicker,
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.FolderOpen,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.browse_saf))
                        }

                        Button(
                            onClick = { onSavePath(pathInput) },
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text(stringResource(R.string.save_path))
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    Text(
                        text = stringResource(R.string.preset_directory_suggestions),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    val presetPaths = listOf(
                        "/storage/emulated/0/Android/Media/com.darkestdungeon/mods"
                    )

                    presetPaths.forEach { preset ->
                        OutlinedButton(
                            onClick = {
                                pathInput = preset
                                onSavePath(preset)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = preset,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            LanguageCard(
                currentLanguageTag = currentLanguageTag,
                onLanguageSelected = onLanguageSelected
            )

            CategoryEditorCard(
                categories = categories,
                selectedCategoryId = selectedCategoryId,
                onSelectCategory = onSelectCategory,
                onAddCategory = { showAddCategoryDialog = true },
                onRenameCategory = { renameCategory = it },
                onMoveCategoryUp = onMoveCategoryUp,
                onMoveCategoryDown = onMoveCategoryDown,
                onResetBuiltInCategoryOrder = onResetBuiltInCategoryOrder,
                onAutoCategorize = onAutoCategorize,
                onAutoSort = onAutoSort,
                onSetCategoryColor = { colorCategory = it },
                onResetCategoryColor = onResetCategoryColor,
                onRemoveCategory = onRemoveCategory
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        stringResource(R.string.game_save_load_order),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (gameSaveUriStr == null) {
                            stringResource(R.string.no_target_save_selected)
                        } else {
                            stringResource(R.string.target_campaign_entries, gameLoadOrderCount)
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        if (sourceGameSaveUriStr == null) {
                            stringResource(R.string.source_campaign_none)
                        } else {
                            stringResource(R.string.source_campaign_entries, sourceGameLoadOrderCount)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onLaunchSourceSaveFilePicker,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Icon(Icons.Rounded.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.import_source_save), maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = onLaunchSaveFilePicker,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Icon(Icons.Rounded.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.import_target_save), maxLines = 1)
                        }
                    }
                }
            }

            // Quick Batch Operations Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = stringResource(R.string.batch_actions),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onEnableAll,
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.enable_all))
                        }

                        OutlinedButton(
                            onClick = onDisableAll,
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.medium,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ClearAll,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.disable_all))
                        }
                    }

                    OutlinedButton(
                        onClick = onRescanMods,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.rescan_mods))
                    }
                }
            }

            // Mod Summary Statistics Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = stringResource(R.string.mod_summary_statistics),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = stringResource(R.string.total_mods_detected, totalModsCount),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = stringResource(R.string.active_enabled_mods, enabledModsCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.disabled_mods, disabledModsCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showAddCategoryDialog) {
        CategoryNameDialog(
            title = stringResource(R.string.add_category_title),
            confirmLabel = stringResource(R.string.add),
            onDismiss = { showAddCategoryDialog = false },
            onConfirm = { name ->
                onAddCategory(name)
                showAddCategoryDialog = false
            }
        )
    }

    renameCategory?.let { category ->
        CategoryNameDialog(
            title = stringResource(R.string.rename_category_title),
            initialName = category.name,
            confirmLabel = stringResource(R.string.rename),
            onDismiss = { renameCategory = null },
            onConfirm = { name ->
                onRenameCategory(category.id, name)
                renameCategory = null
            }
        )
    }

    colorCategory?.let { category ->
        CategoryColorDialog(
            category = category,
            onDismiss = { colorCategory = null },
            onApply = { color ->
                onSetCategoryColor(category.id, color)
                colorCategory = null
            }
        )
    }
}


@Composable
private fun LanguageCard(
    currentLanguageTag: String,
    onLanguageSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = AppLanguage.options.firstOrNull { it.tag == currentLanguageTag }
        ?: AppLanguage.options.first()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.language),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.language_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Box {
                OutlinedButton(
                    onClick = { expanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        text = selected.label,
                        modifier = Modifier.weight(1f),
                        maxLines = 1
                    )
                    Text("⌄")
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AppLanguage.options.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = {
                                expanded = false
                                onLanguageSelected(option.tag)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryEditorCard(
    categories: List<ModCategory>,
    selectedCategoryId: String,
    onSelectCategory: (String) -> Unit,
    onAddCategory: () -> Unit,
    onRenameCategory: (ModCategory) -> Unit,
    onMoveCategoryUp: (String) -> Unit,
    onMoveCategoryDown: (String) -> Unit,
    onResetBuiltInCategoryOrder: () -> Unit,
    onAutoCategorize: () -> Unit,
    onAutoSort: () -> Unit,
    onSetCategoryColor: (ModCategory) -> Unit,
    onResetCategoryColor: (String) -> Unit,
    onRemoveCategory: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(stringResource(R.string.edit_categories), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.category_editor_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onAddCategory,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(stringResource(R.string.add_category), maxLines = 2)
                }
                OutlinedButton(
                    onClick = onResetBuiltInCategoryOrder,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(stringResource(R.string.reset_builtin_order), maxLines = 2)
                }
                OutlinedButton(
                    onClick = onAutoCategorize,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(stringResource(R.string.auto_categorize), maxLines = 2)
                }
                OutlinedButton(
                    onClick = onAutoSort,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(stringResource(R.string.auto_sort), maxLines = 2)
                }
            }

            categories.forEachIndexed { index, category ->
                val categoryColor = parseCategoryColor(category.colorHex)
                val isSelected = category.id == selectedCategoryId
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectCategory(category.id) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    shape = MaterialTheme.shapes.small
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(categoryColor, RoundedCornerShape(6.dp))
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(localizedCategoryName(category), fontWeight = FontWeight.Bold)
                                Text(
                                    if (category.builtIn) stringResource(R.string.built_in) else stringResource(R.string.custom),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                category.colorHex,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                TextButton(
                                    onClick = { onMoveCategoryUp(category.id) },
                                    enabled = index > 0,
                                    modifier = Modifier.weight(1f)
                                ) { Text(stringResource(R.string.up), maxLines = 1) }
                                TextButton(
                                    onClick = { onMoveCategoryDown(category.id) },
                                    enabled = index < categories.lastIndex,
                                    modifier = Modifier.weight(1f)
                                ) { Text(stringResource(R.string.down), maxLines = 1) }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                TextButton(
                                    onClick = { onSetCategoryColor(category) },
                                    modifier = Modifier.weight(1f)
                                ) { Text(stringResource(R.string.set_color), maxLines = 1) }
                                TextButton(
                                    onClick = { onResetCategoryColor(category.id) },
                                    modifier = Modifier.weight(1f)
                                ) { Text(stringResource(R.string.reset), maxLines = 1) }
                            }
                        }

                        if (!category.builtIn) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { onRenameCategory(category) },
                                    modifier = Modifier.weight(1f)
                                ) { Text(stringResource(R.string.rename)) }
                                OutlinedButton(
                                    onClick = { onRemoveCategory(category.id) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    )
                                ) { Text(stringResource(R.string.remove)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryNameDialog(
    title: String,
    initialName: String = "",
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.category_name)) },
                singleLine = true
            )
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim()) },
                enabled = name.trim().isNotEmpty()
            ) { Text(confirmLabel) }
        }
    )
}

@Composable
private fun CategoryColorDialog(
    category: ModCategory,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit
) {
    val initial = remember(category.colorHex) { parseCategoryColor(category.colorHex) }
    val initialArgb = initial.toArgb()
    var hue by remember(category.colorHex) {
        mutableStateOf(FloatArray(3).also { AndroidColor.colorToHSV(initialArgb, it) }[0])
    }
    var saturation by remember(category.colorHex) {
        mutableStateOf(FloatArray(3).also { AndroidColor.colorToHSV(initialArgb, it) }[1])
    }
    var luminosity by remember(category.colorHex) {
        mutableStateOf(FloatArray(3).also { AndroidColor.colorToHSV(initialArgb, it) }[2])
    }
    var red by remember(category.colorHex) { mutableStateOf(AndroidColor.red(initialArgb).toFloat()) }
    var green by remember(category.colorHex) { mutableStateOf(AndroidColor.green(initialArgb).toFloat()) }
    var blue by remember(category.colorHex) { mutableStateOf(AndroidColor.blue(initialArgb).toFloat()) }
    var hexInput by remember(category.colorHex) { mutableStateOf(category.colorHex) }

    fun updateFromRgb(r: Float, g: Float, b: Float) {
        red = r
        green = g
        blue = b
        val hsv = FloatArray(3)
        AndroidColor.RGBToHSV(r.toInt(), g.toInt(), b.toInt(), hsv)
        hue = hsv[0]
        saturation = hsv[1]
        luminosity = hsv[2]
        hexInput = rgbToHex(r.toInt(), g.toInt(), b.toInt())
    }

    fun updateFromHsv(h: Float = hue, s: Float = saturation, v: Float = luminosity) {
        hue = h
        saturation = s
        luminosity = v
        val argb = AndroidColor.HSVToColor(floatArrayOf(hue, saturation, luminosity))
        red = AndroidColor.red(argb).toFloat()
        green = AndroidColor.green(argb).toFloat()
        blue = AndroidColor.blue(argb).toFloat()
        hexInput = rgbToHex(red.toInt(), green.toInt(), blue.toInt())
    }

    val previewColor = Color(AndroidColor.HSVToColor(floatArrayOf(hue, saturation, luminosity)))
    val validHex = parseCategoryColorOrNull(hexInput) != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.set_color) + ": " + localizedCategoryName(category)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .background(previewColor, RoundedCornerShape(8.dp))
                )
                Text(stringResource(R.string.hue), style = MaterialTheme.typography.labelMedium)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(22.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)
                            ),
                            RoundedCornerShape(8.dp)
                        )
                )
                Slider(value = hue, onValueChange = { updateFromHsv(h = it) }, valueRange = 0f..360f)
                Text(stringResource(R.string.saturation, (saturation * 100).toInt()))
                Slider(value = saturation, onValueChange = { updateFromHsv(s = it) }, valueRange = 0f..1f)
                Text(stringResource(R.string.luminosity, (luminosity * 100).toInt()))
                Slider(value = luminosity, onValueChange = { updateFromHsv(v = it) }, valueRange = 0f..1f)
                Text(stringResource(R.string.red, red.toInt()))
                Slider(value = red, onValueChange = { updateFromRgb(it, green, blue) }, valueRange = 0f..255f)
                Text(stringResource(R.string.green, green.toInt()))
                Slider(value = green, onValueChange = { updateFromRgb(red, it, blue) }, valueRange = 0f..255f)
                Text(stringResource(R.string.blue, blue.toInt()))
                Slider(value = blue, onValueChange = { updateFromRgb(red, green, it) }, valueRange = 0f..255f)
                OutlinedTextField(
                    value = hexInput,
                    onValueChange = { value ->
                        hexInput = value
                        parseCategoryColorOrNull(value)?.let { parsed ->
                            val argb = parsed.toArgb()
                            updateFromRgb(
                                AndroidColor.red(argb).toFloat(),
                                AndroidColor.green(argb).toFloat(),
                                AndroidColor.blue(argb).toFloat()
                            )
                        }
                    },
                    label = { Text(stringResource(R.string.hex_color)) },
                    singleLine = true,
                    supportingText = { Text(stringResource(R.string.hex_color_hint)) }
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        confirmButton = {
            TextButton(onClick = { onApply(hexInput.uppercase()) }, enabled = validHex) {
                Text(stringResource(R.string.apply))
            }
        }
    )
}

private fun parseCategoryColor(hex: String): Color =
    parseCategoryColorOrNull(hex) ?: Color.White

private fun parseCategoryColorOrNull(hex: String): Color? =
    runCatching { Color(AndroidColor.parseColor(hex.trim())) }.getOrNull()

private fun rgbToHex(red: Int, green: Int, blue: Int): String =
    "#%02X%02X%02X".format(red.coerceIn(0, 255), green.coerceIn(0, 255), blue.coerceIn(0, 255))

@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
    DDAndroidModManagerTheme {
        SettingsScreen(
            activeFolderPath = "/storage/emulated/0/Android/Media/com.darkestdungeon/mods",
            gameSaveUriStr = null,
            gameLoadOrderCount = 0,
            sourceGameSaveUriStr = null,
            sourceGameLoadOrderCount = 0,
            totalModsCount = 10,
            enabledModsCount = 7,
            disabledModsCount = 3,
            onBack = {},
            onSavePath = {},
            onLaunchFolderPicker = {},
            onLaunchSaveFilePicker = {},
            onLaunchSourceSaveFilePicker = {},
            onEnableAll = {},
            onDisableAll = {},
            onRescanMods = {},
            isLoading = false,
            errorMessage = null,
            statusMessage = null
        )
    }
}
