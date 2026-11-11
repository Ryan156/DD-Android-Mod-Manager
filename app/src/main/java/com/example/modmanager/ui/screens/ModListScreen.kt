package com.darkestdungeon.modmanager.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import com.darkestdungeon.modmanager.R
import com.darkestdungeon.modmanager.data.model.Mod
import com.darkestdungeon.modmanager.ui.components.FolderModCard
import com.darkestdungeon.modmanager.ui.components.ModCard
import com.darkestdungeon.modmanager.ui.components.SearchBarAndFilter
import com.darkestdungeon.modmanager.ui.theme.DDAndroidModManagerTheme
import com.darkestdungeon.modmanager.ui.viewmodel.ModListUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModListScreen(
    uiState: ModListUiState,
    onNavigateToSettings: () -> Unit,
    onNavigateToModDetail: (Mod) -> Unit,
    onCopySourceToTarget: () -> Unit,
    onFolderModSelectionChanged: (String, Boolean) -> Unit,
    onEnableSelectedMods: () -> Unit,
    onDisableSelectedMods: () -> Unit,
    onClearFolderSelection: () -> Unit,
    onActiveModSelectionChanged: (String, Boolean) -> Unit,
    onMoveSelectedActiveModsToPosition: (Int) -> Unit,
    onClearActiveSelection: () -> Unit,
    onDismissTransferReport: () -> Unit,
    onMoveModUp: (String) -> Unit,
    onMoveModDown: (String) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onRescanMods: () -> Unit,
    onClearError: () -> Unit,
    onClearStatus: () -> Unit,
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var activeDestinationInput by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(error)
            onClearError()
        }
    }

    LaunchedEffect(uiState.statusMessage) {
        uiState.statusMessage?.let { status ->
            snackbarHostState.showSnackbar(status)
            onClearStatus()
        }
    }

    uiState.transferReport?.let { report ->
        AlertDialog(
            onDismissRequest = onDismissTransferReport,
            confirmButton = {
                TextButton(onClick = onDismissTransferReport) {
                    Text("Done")
                }
            },
            title = { Text("Load order copied") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Matched source order: ${report.matched.size} mod(s)")
                    if (report.appendedNotInSource.isNotEmpty()) {
                        Text(
                            "Appended at the end (not in source):\n" +
                                report.appendedNotInSource.joinToString("\n")
                        )
                    }
                    if (report.ignoredNotInstalled.isNotEmpty()) {
                        Text(
                            "Ignored source mods not installed on this device: " +
                                "${report.ignoredNotInstalled.size}"
                        )
                    }
                }
            }
        )
    }

    if (uiState.isTransferring) {
        CircularProgressIndicator()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Image(
                            painter = painterResource(R.mipmap.darkestdungeon_modmanager_foreground),
                            contentDescription = "DD Mod Manager",
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = "DD Mod Manager",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onRescanMods) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Rescan Mods",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "Settings"
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                PrimaryScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    edgePadding = 0.dp,
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    listOf(
                        "Folder (${uiState.folderMods.size})",
                        "Active (${uiState.activeMods.size})",
                        "Inactive (${uiState.inactiveMods.size})",
                        "Saves"
                    ).forEachIndexed { index, label ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Clip
                                )
                            }
                        )
                    }
                }

                // Search and status filters remain on the selected tab.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (selectedTab < 3) {
                        SearchBarAndFilter(
                            searchQuery = uiState.searchQuery,
                            onSearchQueryChange = onSearchQueryChange
                        )
                    }
                    if (selectedTab == 0) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "${uiState.selectedFolderModIds.size} selected",
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (uiState.selectedFolderModIds.isNotEmpty()) {
                                    TextButton(onClick = onClearFolderSelection) {
                                        Text("Clear")
                                    }
                                }
                            }
                            if (uiState.selectedFolderModIds.isNotEmpty()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = onDisableSelectedMods,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Disable selected")
                                    }
                                    Button(
                                        onClick = onEnableSelectedMods,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Enable selected")
                                    }
                                }
                            }
                        }
                    }
                    if (selectedTab == 1) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "${uiState.selectedActiveModIds.size} selected",
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (uiState.selectedActiveModIds.isNotEmpty()) {
                                    TextButton(onClick = onClearActiveSelection) {
                                        Text("Clear")
                                    }
                                }
                            }
                            if (uiState.selectedActiveModIds.isNotEmpty()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = activeDestinationInput,
                                        onValueChange = { value ->
                                            activeDestinationInput = value.filter(Char::isDigit)
                                        },
                                        label = { Text("Start at order #") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f)
                                    )
                                    Button(
                                        onClick = {
                                            activeDestinationInput.toIntOrNull()?.let { position ->
                                                onMoveSelectedActiveModsToPosition(position)
                                                activeDestinationInput = ""
                                            }
                                        },
                                        enabled = activeDestinationInput.toIntOrNull()?.let { it > 0 } == true
                                    ) {
                                        Text("Move")
                                    }
                                }
                            }
                        }
                    }
                }

                // Mod List Content
                if (selectedTab == 3) {
                    SaveOrdersTab(
                        sourceOrder = uiState.sourceGameLoadOrder,
                        targetOrder = uiState.gameLoadOrder,
                        sourceSelected = uiState.sourceGameSaveUriStr != null,
                        targetSelected = uiState.gameSaveUriStr != null,
                        onCopySourceToTarget = onCopySourceToTarget
                    )
                }

                val filteredList = when (selectedTab) {
                    0 -> uiState.folderMods
                    1 -> uiState.activeMods
                    2 -> uiState.inactiveMods
                    else -> emptyList()
                }

                if (filteredList.isEmpty() && !uiState.isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.FolderOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(56.dp)
                                )

                                Text(
                                    text = if (uiState.mods.isEmpty()) "No Mods Found" else "No Matching Mods",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )

                                Text(
                                    text = if (uiState.mods.isEmpty()) {
                                        "No Darkest Dungeon mod folders found in '${uiState.activeFolderPath}'. Make sure your mods are extracted into subfolders here."
                                    } else {
                                        "No mods match the current search query or status filter."
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )

                                if (uiState.mods.isEmpty()) {
                                    Button(
                                        onClick = onNavigateToSettings,
                                        shape = MaterialTheme.shapes.medium,
                                        modifier = Modifier.padding(top = 8.dp)
                                    ) {
                                        Text("Configure Storage Directory")
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = {
                                            onSearchQueryChange("")
                                        },
                                        shape = MaterialTheme.shapes.medium,
                                        modifier = Modifier.padding(top = 8.dp)
                                    ) {
                                        Text("Clear Filters")
                                    }
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp, top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(
                            items = filteredList,
                            key = { _, item -> item.id }
                        ) { _, mod ->
                            // Active = present in the current load order (the
                            // source of truth used by the Active/Inactive tabs),
                            // NOT merely the folder's on-disk location.
                            val appOrderForToggle = uiState.appLoadOrder
                                .ifEmpty { uiState.displayedGameLoadOrder }
                            val toggleCandidates = listOf(mod.title, mod.folderName, mod.id)
                                .map { it.trim().lowercase() }
                            val modIsActiveInOrder = appOrderForToggle.indexOfFirst { saved ->
                                val normalized = saved.trim().lowercase().replace(" ", "")
                                toggleCandidates.any {
                                    it.replace(" ", "") == normalized
                                }
                            } >= 0

                            if (selectedTab == 0) {
                                FolderModCard(
                                    mod = mod,
                                    isActive = modIsActiveInOrder,
                                    isSelected = mod.id in uiState.selectedFolderModIds,
                                    onClick = { onNavigateToModDetail(mod) },
                                    onSelectionChanged = { selected ->
                                        onFolderModSelectionChanged(mod.id, selected)
                                    }
                                )
                            } else {
                                // Button state follows the mod's position in
                                // the APP ORDER (the source of truth), not the
                                // filtered tab index — otherwise buttons gray
                                // inconsistently vs. what the move will do.
                                val appOrder = uiState.appLoadOrder
                                    .ifEmpty { uiState.displayedGameLoadOrder }
                                val candidates = listOf(mod.title, mod.folderName, mod.id)
                                    .map { it.trim().lowercase() }
                                val orderIndex = appOrder.indexOfFirst { saved ->
                                    val normalized = saved.trim().lowercase()
                                    candidates.any { it == normalized }
                                }
                                ModCard(
                                    mod = mod,
                                    isFirst = orderIndex <= 0,
                                    isLast = orderIndex < 0 || orderIndex >= appOrder.size - 1,
                                    onToggleEnabled = {},
                                    onMoveUp = { onMoveModUp(mod.id) },
                                    onMoveDown = { onMoveModDown(mod.id) },
                                    showToggle = false,
                                    showReorderControls = false,
                                    showSelectionCheckbox = selectedTab == 1,
                                    isSelected = mod.id in uiState.selectedActiveModIds,
                                    onSelectionChanged = { selected ->
                                        onActiveModSelectionChanged(mod.id, selected)
                                    },
                                    onClick = { onNavigateToModDetail(mod) }
                                )
                            }
                        }
                    }
                }
            }

            // Loading overlay
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Row(
                            modifier = Modifier.padding( horizontal = 24.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Scanning mod directory...",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SaveOrdersTab(
    sourceOrder: List<String>,
    targetOrder: List<String>,
    sourceSelected: Boolean,
    targetSelected: Boolean,
    onCopySourceToTarget: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Save Load Orders",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Source save is the campaign order you want to use. Target save is the campaign that will receive it. Import both in Settings, then copy the source order to the target.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(
            onClick = onCopySourceToTarget,
            enabled = sourceSelected && targetSelected && sourceOrder.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Copy source order to target")
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SaveOrderColumn(
                title = "Source save (${sourceOrder.size})",
                order = sourceOrder,
                modifier = Modifier.weight(1f)
            )
            SaveOrderColumn(
                title = "Target save (${targetOrder.size})",
                order = targetOrder,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SaveOrderColumn(
    title: String,
    order: List<String>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxHeight()
    ) {
        Column(
            modifier = Modifier
                .padding(10.dp)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(title, fontWeight = FontWeight.Bold)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (order.isEmpty()) {
                    Text(
                        "No order loaded",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    order.forEachIndexed { index, name ->
                        Text(
                            "${index + 1}. $name",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ModListScreenPreview() {
    DDAndroidModManagerTheme {
        ModListScreen(
            uiState = ModListUiState(
                activeFolderPath = "/storage/emulated/0/DarkestDungeon/mods",
                mods = listOf(
                    Mod(
                        id = "falconer",
                        title = "The Falconer Hero Class",
                        description = "Adds a brand new ranged marksman hero class with custom trinkets.",
                        author = "Marney",
                        version = "2.4",
                        folderName = "falconer",
                        folderPath = "",
                        isEnabled = true,
                        loadOrder = 0,
                        tags = listOf("Hero Class", "Trinkets")
                    ),
                    Mod(
                        id = "mariner",
                        title = "The Mariner Hero",
                        description = "Seafaring front-line warrior for dungeon raids.",
                        author = "ActionJack",
                        version = "1.8",
                        folderName = "mariner",
                        folderPath = "",
                        isEnabled = true,
                        loadOrder = 1,
                        tags = listOf("Hero Class")
                    ),
                    Mod(
                        id = "faster_walk",
                        title = "Faster Walking Speed",
                        description = "Increases walking speed in dungeons.",
                        author = "ModderX",
                        version = "3.0",
                        folderName = "qol_speed",
                        folderPath = "",
                        isEnabled = false,
                        loadOrder = 2,
                        tags = listOf("QoL")
                    )
                )
            ),
            onNavigateToSettings = {},
            onNavigateToModDetail = {},
            onCopySourceToTarget = {},
            onFolderModSelectionChanged = { _, _ -> },
            onEnableSelectedMods = {},
            onDisableSelectedMods = {},
            onClearFolderSelection = {},
            onActiveModSelectionChanged = { _, _ -> },
            onMoveSelectedActiveModsToPosition = {},
            onClearActiveSelection = {},
            onDismissTransferReport = {},
            onMoveModUp = {},
            onMoveModDown = {},
            onSearchQueryChange = {},
            onRescanMods = {},
            onClearError = {},
            onClearStatus = {}
        )
    }
}
