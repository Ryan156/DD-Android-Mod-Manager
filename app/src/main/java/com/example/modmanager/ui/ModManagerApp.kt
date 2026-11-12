package com.darkestdungeon.modmanager.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.darkestdungeon.modmanager.ui.navigation.Screen
import com.darkestdungeon.modmanager.ui.screens.ModDetailScreen
import com.darkestdungeon.modmanager.ui.screens.ModListScreen
import com.darkestdungeon.modmanager.ui.screens.SettingsScreen
import com.darkestdungeon.modmanager.ui.theme.DDAndroidModManagerTheme
import com.darkestdungeon.modmanager.ui.viewmodel.ModManagerViewModel

@Composable
fun ModManagerApp(
    viewModel: ModManagerViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Navigation Stack
    val backStack = remember { mutableStateListOf<Screen>(Screen.ModList) }
    val currentScreen = backStack.lastOrNull() ?: Screen.ModList

    // SAF Directory Picker Launchers
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching {
                context.contentResolver.takePersistableUriPermission(it, flags)
            }
            val path = uri.path ?: uri.toString()
            viewModel.setTargetDirectory(path, uri.toString())
        }
    }

    val saveFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching {
                context.contentResolver.takePersistableUriPermission(it, flags)
            }
            viewModel.setGameSaveFile(it.toString())
        }
    }

    val sourceSaveFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching {
                context.contentResolver.takePersistableUriPermission(it, flags)
            }
            viewModel.setSourceGameSaveFile(it.toString())
        }
    }

    // Handle back button on child screens
    BackHandler(enabled = backStack.size > 1) {
        if (backStack.size > 1) {
            backStack.removeAt(backStack.size - 1)
        }
    }

    DDAndroidModManagerTheme {
        when (val screen = currentScreen) {
            is Screen.ModList -> {
                ModListScreen(
                    uiState = uiState,
                    onNavigateToSettings = {
                        backStack.add(Screen.Settings)
                    },
                    onNavigateToModDetail = { mod ->
                        viewModel.selectMod(mod)
                        backStack.add(Screen.ModDetail(mod.id))
                    },
                    onCopySourceToTarget = {
                        viewModel.copySourceOrderToTarget()
                    },
                    onFolderModSelectionChanged = { modId, selected ->
                        viewModel.setFolderModSelected(modId, selected)
                    },
                    onEnableSelectedMods = {
                        viewModel.enableSelectedMods()
                    },
                    onDisableSelectedMods = {
                        viewModel.disableSelectedMods()
                    },
                    onClearFolderSelection = {
                        viewModel.clearFolderModSelection()
                    },
                    onActiveModSelectionChanged = { modId, selected ->
                        viewModel.setActiveModSelected(modId, selected)
                    },
                    onMoveSelectedActiveModsToPosition = { position ->
                        viewModel.moveSelectedActiveModsToPosition(position)
                    },
                    onClearActiveSelection = {
                        viewModel.clearActiveModSelection()
                    },
                    onDismissTransferReport = {
                        viewModel.dismissTransferReport()
                    },
                    onMoveModUp = { modId ->
                        viewModel.moveModUp(modId)
                    },
                    onMoveModDown = { modId ->
                        viewModel.moveModDown(modId)
                    },
                    onSearchQueryChange = { query ->
                        viewModel.setSearchQuery(query)
                    },
                    onRescanMods = {
                        viewModel.scanMods()
                    },
                    onClearError = {
                        viewModel.clearError()
                    },
                    onClearStatus = {
                        viewModel.clearStatus()
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            is Screen.ModDetail -> {
                val selectedMod = uiState.mods.firstOrNull { it.id == screen.modId } ?: uiState.selectedMod

                ModDetailScreen(
                    mod = selectedMod,
                    allMods = uiState.mods,
                    isActive = selectedMod?.let { selected ->
                        uiState.activeMods.any { it.id == selected.id }
                    } == true,
                    onBack = {
                        if (backStack.size > 1) {
                            backStack.removeAt(backStack.size - 1)
                        }
                    },
                    onToggleEnabled = { modId ->
                        viewModel.toggleModEnabled(modId)
                    },
                    onMoveUp = { modId ->
                        viewModel.moveModUp(modId)
                    },
                    onMoveDown = { modId ->
                        viewModel.moveModDown(modId)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            is Screen.Settings -> {
                SettingsScreen(
                    activeFolderPath = uiState.activeFolderPath,
                    gameSaveUriStr = uiState.gameSaveUriStr,
                    gameLoadOrderCount = uiState.gameLoadOrder.size,
                    sourceGameSaveUriStr = uiState.sourceGameSaveUriStr,
                    sourceGameLoadOrderCount = uiState.sourceGameLoadOrder.size,
                    totalModsCount = uiState.mods.size,
                    enabledModsCount = uiState.enabledCount,
                    disabledModsCount = uiState.disabledCount,
                    onBack = {
                        if (backStack.size > 1) {
                            backStack.removeAt(backStack.size - 1)
                        }
                    },
                    onSavePath = { newPath ->
                        viewModel.setTargetDirectory(newPath, null)
                    },
                    onLaunchFolderPicker = {
                        folderPickerLauncher.launch(null)
                    },
                    onLaunchSaveFilePicker = {
                        saveFilePickerLauncher.launch(arrayOf("*/*"))
                    },
                    onLaunchSourceSaveFilePicker = {
                        sourceSaveFilePickerLauncher.launch(arrayOf("*/*"))
                    },
                    onEnableAll = {
                        viewModel.enableAllMods()
                    },
                    onDisableAll = {
                        viewModel.disableAllMods()
                    },
                    onRescanMods = {
                        viewModel.scanMods()
                    },
                    isLoading = uiState.isLoading,
                    errorMessage = uiState.errorMessage,
                    statusMessage = uiState.statusMessage,
                    onClearError = {
                        viewModel.clearError()
                    },
                    onClearStatus = {
                        viewModel.clearStatus()
                    },
                    categories = uiState.categories,
                    selectedCategoryId = uiState.selectedCategoryId,
                    onSelectCategory = { categoryId ->
                        viewModel.selectCategory(categoryId)
                    },
                    onAddCategory = { name ->
                        viewModel.addCategory(name)
                    },
                    onRenameCategory = { categoryId, name ->
                        viewModel.renameCustomCategory(categoryId, name)
                    },
                    onMoveCategoryUp = { categoryId ->
                        viewModel.moveCategoryUp(categoryId)
                    },
                    onMoveCategoryDown = { categoryId ->
                        viewModel.moveCategoryDown(categoryId)
                    },
                    onResetBuiltInCategoryOrder = {
                        viewModel.resetBuiltInCategoryOrder()
                    },
                    onSetCategoryColor = { categoryId, colorHex ->
                        viewModel.setCategoryColor(categoryId, colorHex)
                    },
                    onResetCategoryColor = { categoryId ->
                        viewModel.resetCategoryColor(categoryId)
                    },
                    onRemoveCategory = { categoryId ->
                        viewModel.removeCustomCategory(categoryId)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
