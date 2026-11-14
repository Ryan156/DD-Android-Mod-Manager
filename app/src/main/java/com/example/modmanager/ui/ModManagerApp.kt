package com.darkestdungeon.modmanager.ui

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.darkestdungeon.modmanager.data.update.AppUpdate
import com.darkestdungeon.modmanager.data.update.AppUpdateChecker
import com.darkestdungeon.modmanager.R
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
    val isDebugBuild = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    val updateChecker = remember { AppUpdateChecker() }
    val updateScope = rememberCoroutineScope()
    var availableUpdate by remember { mutableStateOf<AppUpdate?>(null) }
    var updateDismissed by remember { mutableStateOf(false) }
    var isDownloadingUpdate by remember { mutableStateOf(false) }
    var updateDownloadFailed by remember { mutableStateOf(false) }

    LaunchedEffect(isDebugBuild) {
        if (!isDebugBuild) {
            val currentVersion = context.packageManager
                .getPackageInfo(context.packageName, 0)
                .versionName
                .orEmpty()
            availableUpdate = updateChecker.check(currentVersion)
        }
    }

    if (!isDebugBuild) {
        availableUpdate?.takeUnless { updateDismissed }?.let { update ->
        AlertDialog(
            onDismissRequest = {
                if (!isDownloadingUpdate) updateDismissed = true
            },
            title = { Text(stringResource(R.string.update_available_title)) },
            text = {
                Text(
                    text = when {
                        isDownloadingUpdate -> stringResource(R.string.downloading_update)
                        updateDownloadFailed -> stringResource(R.string.update_download_failed)
                        else -> stringResource(R.string.update_available_message, update.version)
                    }
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !isDownloadingUpdate,
                    onClick = {
                        if (update.apkUrl == null) {
                            updateDismissed = true
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, update.releaseUrl.toUri())
                                )
                            }
                        } else {
                            updateDownloadFailed = false
                            isDownloadingUpdate = true
                            updateScope.launch {
                                val apkUri = updateChecker.downloadApk(context, update)
                                isDownloadingUpdate = false
                                if (apkUri == null) {
                                    updateDownloadFailed = true
                                } else {
                                    val installIntent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(
                                            apkUri,
                                            "application/vnd.android.package-archive"
                                        )
                                        addFlags(
                                            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                                Intent.FLAG_ACTIVITY_NEW_TASK
                                        )
                                    }
                                    runCatching {
                                        context.startActivity(installIntent)
                                    }.onSuccess {
                                        updateDismissed = true
                                    }.onFailure {
                                        updateDownloadFailed = true
                                    }
                                }
                            }
                        }
                    }
                ) {
                    Text(
                        stringResource(
                            if (update.apkUrl == null) R.string.update_now
                            else R.string.download_update
                        )
                    )
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isDownloadingUpdate,
                    onClick = { updateDismissed = true }
                ) {
                    Text(stringResource(R.string.update_later))
                }
            }
        )
        }
    }

    // Navigation Stack
    val backStack = remember {
        mutableStateListOf<Screen>(
            if (AppLanguage.consumeReturnToSettings(context)) {
                Screen.Settings
            } else {
                Screen.ModList
            }
        )
    }
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
                    onAssignSelectedMods = { categoryId ->
                        viewModel.assignSelectedModsToCategory(categoryId)
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
                    onMoveSelectedFolderModsToPosition = { position ->
                        viewModel.moveSelectedFolderModsToPosition(position)
                    },
                    onSearchQueryChange = { query ->
                        viewModel.setSearchQuery(query)
                    },
                    onCategoryFilterChange = { categoryId ->
                        viewModel.setCategoryFilter(categoryId)
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
                    categoryAssignmentReport = uiState.categoryAssignmentReport,
                    onClearCategoryAssignmentReport = {
                        viewModel.clearCategoryAssignmentReport()
                    },
                    onAutoCategorize = {
                        viewModel.autoCategorizeMods()
                    },
                    onAutoSort = {
                        viewModel.autoSortMods()
                    },
                    currentLanguageTag = AppLanguage.selectedTag(context),
                    onLanguageSelected = { tag ->
                        AppLanguage.setLanguage(context, tag)
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
