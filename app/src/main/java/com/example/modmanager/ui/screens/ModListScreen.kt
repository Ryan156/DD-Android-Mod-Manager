package com.darkestdungeon.modmanager.ui.screens

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.material3.SnackbarDuration
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import com.darkestdungeon.modmanager.R
import com.darkestdungeon.modmanager.data.model.Mod
import com.darkestdungeon.modmanager.data.model.ModCategory
import com.darkestdungeon.modmanager.ui.localizedCategoryName
import com.darkestdungeon.modmanager.ui.components.FolderModCard
import com.darkestdungeon.modmanager.ui.components.ModCard
import com.darkestdungeon.modmanager.ui.components.SearchBarAndFilter
import com.darkestdungeon.modmanager.ui.theme.DDAndroidModManagerTheme
import com.darkestdungeon.modmanager.ui.viewmodel.ModListUiState
import com.darkestdungeon.modmanager.ui.viewmodel.UNASSIGNED_CATEGORY_FILTER_ID

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
    onAssignSelectedMods: (String?) -> Unit,
    onClearFolderSelection: () -> Unit,
    onActiveModSelectionChanged: (String, Boolean) -> Unit,
    onMoveSelectedActiveModsToPosition: (Int) -> Unit,
    onClearActiveSelection: () -> Unit,
    onDismissTransferReport: () -> Unit,
    onMoveModUp: (String) -> Unit,
    onMoveModDown: (String) -> Unit,
    onMoveSelectedFolderModsToPosition: (Int) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onCategoryFilterChange: (String?) -> Unit,
    onRescanMods: () -> Unit,
    onClearError: () -> Unit,
    onClearStatus: () -> Unit,
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var folderDestinationInput by rememberSaveable { mutableStateOf("") }
    var activeDestinationInput by rememberSaveable { mutableStateOf("") }
    var showCategoryPicker by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(error, duration = SnackbarDuration.Long)
            onClearError()
        }
    }

    LaunchedEffect(uiState.statusMessage) {
        uiState.statusMessage?.let { status ->
            snackbarHostState.showSnackbar(status, duration = SnackbarDuration.Long)
            onClearStatus()
        }
    }

    uiState.transferReport?.let { report ->
        AlertDialog(
            onDismissRequest = onDismissTransferReport,
            confirmButton = {
                TextButton(onClick = onDismissTransferReport) {
                    Text(stringResource(R.string.done))
                }
            },
            title = { Text(stringResource(R.string.load_order_copied)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.matched_source_order, report.matched.size))
                    Text(
                        stringResource(R.string.target_save_written_backup),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
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

    if (showCategoryPicker) {
        AlertDialog(
            onDismissRequest = { showCategoryPicker = false },
            confirmButton = {
                TextButton(onClick = { showCategoryPicker = false }) { Text(stringResource(R.string.cancel)) }
            },
            title = { Text(stringResource(R.string.assign_category)) },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        stringResource(R.string.choose_category_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    uiState.categories.forEach { category ->
                        TextButton(
                            onClick = {
                                onAssignSelectedMods(category.id)
                                showCategoryPicker = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CategoryOptionLabel(
                                name = localizedCategoryName(category),
                                category = category
                            )
                        }
                    }
                    TextButton(
                        onClick = {
                            onAssignSelectedMods(null)
                            showCategoryPicker = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CategoryOptionLabel(
                            name = stringResource(R.string.unassigned),
                            category = null
                        )
                    }
                }
            }
        )
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
                            contentDescription = stringResource(R.string.app_name),
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = stringResource(R.string.app_name),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onRescanMods) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.rescan_mods),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = stringResource(R.string.settings)
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
                        "${stringResource(R.string.mods_folder)} (${uiState.folderMods.size})",
                        "${stringResource(R.string.active)} (${uiState.activeMods.size})",
                        "${stringResource(R.string.inactive)} (${uiState.inactiveMods.size})",
                        stringResource(R.string.save_orders)
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
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (selectedTab < 3) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SearchBarAndFilter(
                                searchQuery = uiState.searchQuery,
                                onSearchQueryChange = onSearchQueryChange,
                                modifier = Modifier.weight(1f)
                            )
                            CategoryFilterSelector(
                                categories = uiState.categories,
                                selectedCategoryId = uiState.selectedCategoryFilterId,
                                onCategorySelected = onCategoryFilterChange,
                                modifier = Modifier.width(132.dp)
                            )
                        }
                    }
                    if (selectedTab == 0) {
                        Text(
                            text = stringResource(R.string.folder_order_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                                        Text(stringResource(R.string.clear_filters))
                                    }
                                }
                            }
                            if (uiState.selectedFolderModIds.isNotEmpty()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = folderDestinationInput,
                                        onValueChange = { value ->
                                            folderDestinationInput = value.filter(Char::isDigit)
                                        },
                                        label = { Text(stringResource(R.string.start_order)) },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f)
                                    )
                                    Button(
                                        onClick = {
                                            folderDestinationInput.toIntOrNull()?.let { position ->
                                                onMoveSelectedFolderModsToPosition(position)
                                                folderDestinationInput = ""
                                            }
                                        },
                                        enabled = folderDestinationInput.toIntOrNull()?.let { it > 0 } == true
                                    ) {
                                        Text(stringResource(R.string.move))
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = onDisableSelectedMods,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(stringResource(R.string.disable))
                                    }
                                    OutlinedButton(
                                        onClick = { showCategoryPicker = true },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(stringResource(R.string.assign))
                                    }
                                    Button(
                                        onClick = onEnableSelectedMods,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(stringResource(R.string.enable))
                                    }
                                }
                            }
                        }
                    }
                    if (selectedTab == 1) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                                        Text(stringResource(R.string.clear_filters))
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
                                        label = { Text(stringResource(R.string.start_order)) },
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
                                        Text(stringResource(R.string.move))
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
                                    text = if (uiState.mods.isEmpty()) stringResource(R.string.no_mods_found) else stringResource(R.string.no_matching_mods),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )

                                Text(
                                    text = if (uiState.mods.isEmpty()) {
                                        stringResource(R.string.no_mods_in_directory, uiState.activeFolderPath)
                                    } else {
                                        stringResource(R.string.no_mods_match_filters)
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
                                        Text(stringResource(R.string.configure_storage))
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = {
                                            onSearchQueryChange("")
                                        },
                                        shape = MaterialTheme.shapes.medium,
                                        modifier = Modifier.padding(top = 8.dp)
                                    ) {
                                        Text(stringResource(R.string.clear_filters))
                                    }
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp, top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(
                            items = filteredList,
                            key = { _, item -> item.id }
                        ) { index, mod ->
                            // Active = present in the current load order (the
                            // source of truth used by the Active/Inactive tabs),
                            // NOT merely the folder's on-disk location.
                            val appOrderForToggle = uiState.appLoadOrder
                                .ifEmpty { uiState.displayedGameLoadOrder }
                            val toggleCandidates = listOf(mod.title, mod.folderName, mod.id)
                                .map { it.trim().lowercase() }
                            val modIsActiveInOrder = appOrderForToggle.indexOfFirst { saved ->
                                val normalized = saved.trim().lowercase().filter { it.isLetterOrDigit() }
                                toggleCandidates.any {
                                    it.replace(" ", "") == normalized
                                }
                            } >= 0

                            if (selectedTab == 0) {
                                val previous = filteredList.getOrNull(index - 1)
                                val category = uiState.categories
                                    .firstOrNull { it.id == mod.categoryId }
                                val categoryName = category
                                    ?.let { localizedCategoryName(it) }
                                    ?: stringResource(R.string.unassigned)
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    if (index == 0 || previous?.categoryId != mod.categoryId) {
                                        CategoryHeader(
                                            name = categoryName,
                                            category = category,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                    val folderIndex = uiState.folderOrder.indexOf(mod.id)
                                    FolderModCard(
                                        mod = mod,
                                        isActive = modIsActiveInOrder,
                                        isSelected = mod.id in uiState.selectedFolderModIds,
                                        loadOrder = folderIndex,
                                        onClick = { onNavigateToModDetail(mod) },
                                        onSelectionChanged = { selected ->
                                            onFolderModSelectionChanged(mod.id, selected)
                                        }
                                    )
                                }
                            } else {
                                val previous = filteredList.getOrNull(index - 1)
                                val category = uiState.categories
                                    .firstOrNull { it.id == mod.categoryId }
                                val categoryName = category
                                    ?.let { localizedCategoryName(it) }
                                    ?: stringResource(R.string.unassigned)
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    if (index == 0 || previous?.categoryId != mod.categoryId) {
                                        CategoryHeader(
                                            name = categoryName,
                                            category = category,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
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
                                text = stringResource(R.string.scanning_mod_directory),
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
private fun CategoryColorDot(
    category: ModCategory?,
    modifier: Modifier = Modifier
) {
    val fallback = MaterialTheme.colorScheme.primary
    val color = category?.let { parseCategoryColor(it.colorHex, fallback) } ?: fallback
    Box(
        modifier = modifier
            .size(10.dp)
            .background(color, CircleShape)
    )
}

@Composable
private fun CategoryOptionLabel(
    name: String,
    category: ModCategory?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        CategoryColorDot(category = category)
        Text(
            text = name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CategoryHeader(
    name: String,
    category: ModCategory?,
    modifier: Modifier = Modifier
) {
    CategoryOptionLabel(
        name = name,
        category = category,
        modifier = modifier
    )
}

private fun parseCategoryColor(hex: String, fallback: Color): Color =
    runCatching { Color(AndroidColor.parseColor(hex.trim())) }.getOrDefault(fallback)

@Composable
private fun CategoryFilterSelector(
    categories: List<ModCategory>,
    selectedCategoryId: String?,
    onCategorySelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedCategory = categories.firstOrNull { it.id == selectedCategoryId }
    val selectedName = selectedCategory
        ?.let { localizedCategoryName(it) }
        ?: if (selectedCategoryId == null) stringResource(R.string.all) else stringResource(R.string.unassigned)

    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            CategoryOptionLabel(
                name = selectedName,
                category = selectedCategory,
                modifier = Modifier.weight(1f)
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(132.dp)
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                DropdownMenuItem(
                    text = {
                        CategoryOptionLabel(
                            name = stringResource(R.string.all),
                            category = null
                        )
                    },
                    onClick = {
                        onCategorySelected(null)
                        expanded = false
                    }
                )
                categories.forEach { category ->
                    DropdownMenuItem(
                        text = {
                            CategoryOptionLabel(
                                name = localizedCategoryName(category),
                                category = category
                            )
                        },
                        onClick = {
                            onCategorySelected(category.id)
                            expanded = false
                        }
                    )
                }
                DropdownMenuItem(
                    text = {
                        CategoryOptionLabel(
                            name = stringResource(R.string.unassigned),
                            category = null
                        )
                    },
                    onClick = {
                        onCategorySelected(UNASSIGNED_CATEGORY_FILTER_ID)
                        expanded = false
                    }
                )
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
            text = stringResource(R.string.save_load_orders),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = stringResource(R.string.save_orders_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(
            onClick = onCopySourceToTarget,
            enabled = sourceSelected && targetSelected && sourceOrder.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.copy_source_target))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SaveOrderColumn(
                title = "${stringResource(R.string.source_save)} (${sourceOrder.size})",
                order = sourceOrder,
                modifier = Modifier.weight(1f)
            )
            SaveOrderColumn(
                title = "${stringResource(R.string.target_save)} (${targetOrder.size})",
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
                        stringResource(R.string.no_order_loaded),
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
            onAssignSelectedMods = {},
            onClearFolderSelection = {},
            onActiveModSelectionChanged = { _, _ -> },
            onMoveSelectedActiveModsToPosition = {},
            onClearActiveSelection = {},
            onDismissTransferReport = {},
            onMoveModUp = {},
            onMoveModDown = {},
            onMoveSelectedFolderModsToPosition = {},
            onSearchQueryChange = {},
            onCategoryFilterChange = {},
            onRescanMods = {},
            onClearError = {},
            onClearStatus = {}
        )
    }
}
