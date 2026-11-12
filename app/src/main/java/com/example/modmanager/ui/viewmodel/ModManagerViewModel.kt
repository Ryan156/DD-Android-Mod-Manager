package com.darkestdungeon.modmanager.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.darkestdungeon.modmanager.data.model.Mod
import com.darkestdungeon.modmanager.data.model.ModCategory
import com.darkestdungeon.modmanager.data.repository.ModRepository
import com.darkestdungeon.modmanager.data.repository.ModRepository.TransferOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ModManagerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ModRepository(application)
    // SAF providers on physical devices are not guaranteed to handle
    // overlapping read/modify/write operations safely.
    private val saveWriteMutex = Mutex()

    private val _uiState = MutableStateFlow(ModListUiState())
    val uiState: StateFlow<ModListUiState> = _uiState.asStateFlow()

    init {
        loadInitialState()
    }

    fun loadInitialState() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val path = repository.getActivePath()
                val uriStr = repository.getActiveUriStr()
                val saveUri = repository.getActiveSaveUri()
                val saveFileUri = repository.getActiveSaveFileUri()
                val sourceSaveFileUri = repository.getSourceSaveFileUri()
                // The APP's OWN load order is the source of truth (persisted);
                // it is only replaced by a new source-save import.
                val appOrder = repository.getAppLoadOrder()
                val categories = repository.getCategories()
                val selectedCategoryId = repository.getSelectedCategoryId()
                val automaticResult = if (saveUri == null && saveFileUri == null) {
                    repository.tryReadDefaultGameLoadOrder()
                } else {
                    null
                }
                val savedOrder = when {
                    saveUri != null -> repository.readGameLoadOrder(saveUri)
                    saveFileUri != null -> repository.readGameLoadOrderFile(saveFileUri)
                    else -> automaticResult
                }
                // Source order: the imported source save when present, else
                // the app's own persisted order, else the target save's order.
                val sourceOrder = when {
                    sourceSaveFileUri != null -> repository.readGameLoadOrderFile(sourceSaveFileUri).mods
                    appOrder.isNotEmpty() -> appOrder
                    else -> savedOrder?.mods ?: emptyList()
                }
                _uiState.update {
                    it.copy(
                        activeFolderPath = path,
                        activeFolderUriStr = uriStr,
                        gameSaveUriStr = saveUri ?: saveFileUri,
                        gameLoadOrder = savedOrder?.mods ?: emptyList(),
                        sourceGameSaveUriStr = sourceSaveFileUri,
                        sourceGameLoadOrder = sourceOrder,
                        appLoadOrder = appOrder,
                        categories = categories,
                        selectedCategoryId = selectedCategoryId
                            ?.takeIf { id -> categories.any { it.id == id } }
                            ?: categories.first().id
                    )
                }

                // Scan mods
                val scanned = repository.scanMods(path, uriStr)
                val categorized = repository.applyStoredModCategories(scanned, _uiState.value.categories)
                _uiState.update {
                    it.copy(
                        mods = orderModsForGame(categorized, it.displayedGameLoadOrder),
                        isLoading = false,
                        selectedMod = scanned.firstOrNull()
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Failed to load mods: ${e.localizedMessage ?: e.message}"
                    )
                }
            }
        }
    }

    fun setGameSaveFile(uriStr: String) {
        viewModelScope.launch {
            try {
                repository.saveActiveSaveFileUri(uriStr)
                val result = repository.readGameLoadOrderFile(uriStr)
                _uiState.update {
                    it.copy(
                        gameSaveUriStr = uriStr,
                        gameLoadOrder = result.mods,
                        mods = orderModsForGame(it.mods, result.mods),
                        errorMessage = result.warning
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = "Failed to import game save: ${e.localizedMessage ?: e.message}")
                }
            }
        }
    }

    fun setSourceGameSaveFile(uriStr: String) {
        viewModelScope.launch {
            try {
                repository.saveSourceSaveFileUri(uriStr)
                val result = repository.readGameLoadOrderFile(uriStr)
                // A new source import REPLACES the app's own load order.
                repository.saveAppLoadOrder(result.mods)
                _uiState.update {
                    it.copy(
                        sourceGameSaveUriStr = uriStr,
                        sourceGameLoadOrder = result.mods,
                        appLoadOrder = result.mods,
                        mods = orderModsForGame(it.mods, result.mods),
                        errorMessage = result.warning
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = "Failed to import source campaign: ${e.localizedMessage ?: e.message}")
                }
            }
        }
    }

    fun setGameSaveDirectory(uriStr: String) {
        viewModelScope.launch {
            try {
                repository.saveActiveSaveUri(uriStr)
                val result = repository.readGameLoadOrder(uriStr)
                _uiState.update {
                    it.copy(
                        gameSaveUriStr = uriStr,
                        gameLoadOrder = result.mods,
                        mods = orderModsForGame(it.mods, result.mods),
                        errorMessage = result.warning
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = "Failed to read game save: ${e.localizedMessage ?: e.message}")
                }
            }
        }
    }

    private fun orderModsForGame(mods: List<Mod>, gameOrder: List<String>): List<Mod> {
        if (gameOrder.isEmpty()) return mods
        val normalizedOrder = gameOrder.map { it.trim().lowercase() }
        fun position(mod: Mod): Int {
            val candidates = listOf(mod.title, mod.folderName, mod.id)
                .map { it.trim().lowercase() }
            return normalizedOrder.indexOfFirst { saved ->
                candidates.any { candidate -> candidate.replace(" ", "") == saved.replace(" ", "") }
            }.let { if (it < 0) Int.MAX_VALUE else it }
        }
        return mods.sortedWith(compareBy<Mod> { position(it) }.thenBy { it.title })
            .mapIndexed { index, mod -> mod.copy(loadOrder = index) }
    }

    fun scanMods() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val path = _uiState.value.activeFolderPath
                val uriStr = _uiState.value.activeFolderUriStr
                val scanned = repository.scanMods(path, uriStr)
                val categorized = repository.applyStoredModCategories(scanned, _uiState.value.categories)
                _uiState.update {
                    it.copy(
                        mods = orderModsForGame(categorized, it.displayedGameLoadOrder),
                        isLoading = false,
                        selectedMod = categorized.find { m -> m.id == it.selectedMod?.id } ?: categorized.firstOrNull()
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Error scanning directory: ${e.localizedMessage ?: e.message}"
                    )
                }
            }
        }
    }

    fun setTargetDirectory(path: String, uriStr: String? = null) {
        viewModelScope.launch {
            repository.saveActivePath(path, uriStr)
            _uiState.update {
                it.copy(
                    activeFolderPath = path,
                    activeFolderUriStr = uriStr
                )
            }
            scanMods()
        }
    }

    fun toggleModEnabled(modId: String) {
        viewModelScope.launch {
            val currentState = _uiState.value
            val currentMod = currentState.mods.firstOrNull { it.id == modId } ?: return@launch

            // Toggle state derives from APP-ORDER MEMBERSHIP (the source of
            // truth shown by the Active/Inactive tabs and the folder switch).
            val currentOrder = currentState.appLoadOrder
                .ifEmpty { currentState.displayedGameLoadOrder }
            fun matchesSaved(saved: String): Boolean {
                val normalized = saved.trim().lowercase()
                return listOf(currentMod.title, currentMod.folderName, currentMod.id)
                    .map { it.trim().lowercase() }
                    .any {
                        it == normalized
                    }
            }
            val modInOrder = currentOrder.any(::matchesSaved)
            val newEnabledState = !modInOrder

            // ORDER-ONLY toggle: add/remove the mod in the app's load order.
            // The mod's FOLDER is never moved — a mod is off because it is not
            // in the load order, exactly like the game's own save represents it.
            val modName = currentMod.title
            val newAppOrder = if (newEnabledState) {
                if (modInOrder) currentOrder else currentOrder + modName
            } else {
                currentOrder.filterNot(::matchesSaved)
            }
            repository.saveAppLoadOrder(newAppOrder)

            _uiState.update { state ->
                state.copy(
                    mods = orderModsForGame(state.mods, newAppOrder),
                    appLoadOrder = newAppOrder,
                    sourceGameLoadOrder = if (state.sourceGameSaveUriStr == null) {
                        state.sourceGameLoadOrder
                    } else newAppOrder,
                    selectedMod = if (state.selectedMod?.id == modId) {
                        state.mods.firstOrNull { it.id == modId }
                    } else state.selectedMod
                )
            }

            // Write the new order to the SOURCE save (when imported) — a
            // tandem-safe pure permutation of the save's own records.
            val sourceUri = _uiState.value.sourceGameSaveUriStr
            if (sourceUri != null) {
                when (val outcome = saveWriteMutex.withLock {
                            withContext(Dispatchers.IO) {
                                repository.applyLoadOrderToSave(
                                    sourceUri,
                                    newAppOrder,
                                    installedMods = currentState.mods
                                )
                            }
                        }) {
                    is TransferOutcome.Error ->
                        _uiState.update { it.copy(errorMessage = "Save was not written: ${outcome.reason}") }
                    is TransferOutcome.Refused ->
                        _uiState.update { it.copy(errorMessage = "Save write refused: ${outcome.reason}") }
                    is TransferOutcome.Success ->
                        _uiState.update {
                            it.copy(statusMessage = "Source save written: order updated (${newAppOrder.size} mods).")
                        }
                }
            }
        }
    }

    fun setFolderModSelected(modId: String, selected: Boolean) {
        _uiState.update { state ->
            val next = state.selectedFolderModIds.toMutableSet()
            if (selected) next += modId else next -= modId
            state.copy(selectedFolderModIds = next)
        }
    }

    fun clearFolderModSelection() {
        _uiState.update { it.copy(selectedFolderModIds = emptySet()) }
    }

    fun assignSelectedModsToCategory(categoryId: String?) {
        viewModelScope.launch {
            val state = _uiState.value
            val selectedIds = state.selectedFolderModIds
            if (selectedIds.isEmpty()) return@launch
            val updatedMods = state.mods.map { mod ->
                if (mod.id in selectedIds) mod.copy(categoryId = categoryId) else mod
            }
            repository.saveModCategoryAssignments(selectedIds.associateWith { categoryId })
            _uiState.update {
                it.copy(
                    mods = updatedMods,
                    selectedFolderModIds = emptySet()
                )
            }
        }
    }

    fun autoCategorizeMods() {
        viewModelScope.launch {
            val state = _uiState.value
            val result = repository.autoCategorizeUnassignedMods(state.mods, state.categories)
            _uiState.update {
                it.copy(
                    mods = result.mods,
                    categoryAssignmentReport = CategoryAssignmentReport(
                        assignedCount = result.assignedCount,
                        unassignedModNames = result.mods
                            .filter { mod -> mod.categoryId == null }
                            .map { mod -> mod.title }
                            .sorted()
                    )
                )
            }
        }
    }

    fun autoSortMods() {
        val state = _uiState.value
        val categoryOrder = state.categories.mapIndexed { index, category -> category.id to index }.toMap()
        val sorted = state.mods.sortedWith(
            compareBy<Mod> { categoryOrder[it.categoryId] ?: Int.MAX_VALUE }
                .thenBy { it.title.lowercase() }
        )
        _uiState.update {
            it.copy(
                mods = sorted,
                statusMessage = "Mods sorted by the current category order."
            )
        }
    }

    fun clearCategoryAssignmentReport() {
        _uiState.update { it.copy(categoryAssignmentReport = null) }
    }

    fun setActiveModSelected(modId: String, selected: Boolean) {
        _uiState.update { state ->
            val next = state.selectedActiveModIds.toMutableSet()
            if (selected) next += modId else next -= modId
            state.copy(selectedActiveModIds = next)
        }
    }

    fun clearActiveModSelection() {
        _uiState.update { it.copy(selectedActiveModIds = emptySet()) }
    }

    fun enableSelectedMods() {
        viewModelScope.launch {
            val state = _uiState.value
            val selected = state.mods.filter { it.id in state.selectedFolderModIds }
            if (selected.isEmpty()) return@launch

            val order = state.appLoadOrder
                .ifEmpty { state.displayedGameLoadOrder }
                .toMutableList()
            fun normalized(value: String) = value.trim().lowercase().replace(" ", "")
            selected.forEach { mod ->
                val alreadyEnabled = order.any { saved ->
                    normalized(saved) == normalized(mod.title) ||
                        normalized(saved) == normalized(mod.folderName) ||
                        normalized(saved) == normalized(mod.id)
                }
                if (!alreadyEnabled) order += mod.title
            }

            repository.saveAppLoadOrder(order)
            _uiState.update {
                it.copy(
                    mods = orderModsForGame(it.mods, order),
                    appLoadOrder = order,
                    selectedFolderModIds = emptySet(),
                    sourceGameLoadOrder = if (it.sourceGameSaveUriStr == null) it.sourceGameLoadOrder else order
                )
            }
            applyAppOrderToSourceSave(order)
        }
    }

    fun disableSelectedMods() {
        viewModelScope.launch {
            val state = _uiState.value
            val selected = state.mods.filter { it.id in state.selectedFolderModIds }
            if (selected.isEmpty()) return@launch

            fun normalized(value: String) = value.trim().lowercase().replace(" ", "")
            val selectedNames = selected
                .flatMap { mod -> listOf(mod.title, mod.folderName, mod.id) }
                .map(::normalized)
                .toSet()
            val order = state.appLoadOrder
                .ifEmpty { state.displayedGameLoadOrder }
                .filterNot { normalized(it) in selectedNames }

            repository.saveAppLoadOrder(order)
            _uiState.update {
                it.copy(
                    mods = orderModsForGame(it.mods, order),
                    appLoadOrder = order,
                    selectedFolderModIds = emptySet(),
                    sourceGameLoadOrder = if (it.sourceGameSaveUriStr == null) it.sourceGameLoadOrder else order
                )
            }
            applyAppOrderToSourceSave(order)
        }
    }

    fun enableAllMods() {
        viewModelScope.launch {
            // ORDER-ONLY: enable every installed mod in the same category
            // order shown by the folder tab. Existing save names are reused
            // where they match, while new entries use the installed title.
            val state = _uiState.value
            val currentOrder = state.appLoadOrder
                .ifEmpty { state.displayedGameLoadOrder }
            val categoryOrder = state.categories
                .mapIndexed { index, category -> category.id to index }
                .toMap()
            val folderOrder = state.mods.sortedWith(
                compareBy<Mod> { categoryOrder[it.categoryId] ?: Int.MAX_VALUE }
                    .thenBy { it.title.lowercase() }
            )
            fun normalized(value: String) = value.trim().lowercase().replace(" ", "")
            fun savedNameFor(mod: Mod): String {
                return currentOrder.firstOrNull { saved ->
                    listOf(mod.title, mod.folderName, mod.id)
                        .map(::normalized)
                        .any { it == normalized(saved) }
                } ?: mod.title
            }
            val order = folderOrder.map(::savedNameFor)

            repository.saveAppLoadOrder(order)
            _uiState.update {
                it.copy(
                    mods = orderModsForGame(it.mods, order),
                    appLoadOrder = order,
                    sourceGameLoadOrder = if (it.sourceGameSaveUriStr == null) it.sourceGameLoadOrder else order
                )
            }
            applyAppOrderToSourceSave(order)
        }
    }

    fun disableAllMods() {
        viewModelScope.launch {
            // ORDER-ONLY: clear the app order — no folders are touched.
            repository.saveAppLoadOrder(emptyList())
            _uiState.update {
                it.copy(
                    mods = it.mods,
                    appLoadOrder = emptyList(),
                    sourceGameLoadOrder = if (it.sourceGameSaveUriStr == null) it.sourceGameLoadOrder else emptyList()
                )
            }
            applyAppOrderToSourceSave(emptyList())
        }
    }

    /** Writes [order] to the imported source save, surfacing any refusal/error. */
    private suspend fun applyAppOrderToSourceSave(order: List<String>) {
        val sourceUri = _uiState.value.sourceGameSaveUriStr ?: return
        when (val outcome = saveWriteMutex.withLock {
                withContext(Dispatchers.IO) {
                    repository.applyLoadOrderToSave(
                        sourceUri,
                        order,
                        installedMods = _uiState.value.mods
                    )
                }
            }) {
            is TransferOutcome.Error ->
                _uiState.update { it.copy(errorMessage = "Save was not written: ${outcome.reason}") }
            is TransferOutcome.Refused ->
                _uiState.update { it.copy(errorMessage = "Save write refused: ${outcome.reason}") }
            is TransferOutcome.Success ->
                _uiState.update { it.copy(statusMessage = "Source save written: order updated (${order.size} mods).") }
        }
    }

    fun copySourceOrderToTarget() {
        viewModelScope.launch {
            val state = _uiState.value
            val sourceUri = state.sourceGameSaveUriStr
            val targetUri = state.gameSaveUriStr
            if (state.appLoadOrder.isEmpty() && state.sourceGameLoadOrder.isEmpty()) {
                _uiState.update { it.copy(errorMessage = "Import a source save with an enabled mod order first.") }
                return@launch
            }
            if (targetUri == null) {
                _uiState.update { it.copy(errorMessage = "Import the target persist.game.json first.") }
                return@launch
            }

            _uiState.update { it.copy(isTransferring = true) }
            val effectiveSource = sourceUri
            when (val outcome = saveWriteMutex.withLock {
                    withContext(Dispatchers.IO) {
                        repository.transferLoadOrder(effectiveSource ?: "", targetUri)
                    }
                }) {
                is TransferOutcome.Success -> {
                    val plan = outcome.plan
                    val report = TransferReport(
                        matched = plan.matched,
                        appendedNotInSource = plan.appendedNotInSource,
                        ignoredNotInstalled = plan.ignoredNotInstalled
                    )
                    _uiState.update {
                        it.copy(
                            isTransferring = false,
                            transferReport = report,
                            // The DSON transfer stores the copied order in
                            // sourceLoadOrder; orderedTargetEntries belongs to
                            // the retired heuristic writer and is empty here.
                            gameLoadOrder = plan.sourceLoadOrder,
                            errorMessage = null
                        )
                    }
                }
                is TransferOutcome.Refused -> _uiState.update {
                    it.copy(isTransferring = false, errorMessage = "Transfer refused: ${outcome.reason}")
                }
                is TransferOutcome.Error -> _uiState.update {
                    it.copy(isTransferring = false, errorMessage = "Transfer failed: ${outcome.reason}")
                }
            }
        }
    }

    fun dismissTransferReport() {
        _uiState.update { it.copy(transferReport = null) }
    }

    data class TransferReport(
        val matched: List<String>,
        val appendedNotInSource: List<String>,
        val ignoredNotInstalled: List<String>
    )

    /**
     * Moves a mod WITHIN the app's own load order (the source of truth).
     * [fromIndex]/[toIndex] are positions in the ACTIVE-tab list
     * (the app order), NOT the full mods list — buttons gray at the app
     * order's edges and moves only ever reorder the app order.
     */
    fun moveModInAppOrder(fromIndex: Int, toIndex: Int) {
        val state = _uiState.value
        val currentOrder = state.appLoadOrder
            .ifEmpty { state.displayedGameLoadOrder }
            .toMutableList()
        if (fromIndex !in currentOrder.indices || toIndex !in currentOrder.indices) return
        if (fromIndex == toIndex) return

        val moved = currentOrder.removeAt(fromIndex)
        currentOrder.add(toIndex, moved)
        val newAppOrder = currentOrder.toList()

        // Re-sort the mod list to mirror the new app order (unmatched mods keep
        // relative order at the end), with fresh 0..n loadOrder indices.
        val repositioned = orderModsForGame(state.mods, newAppOrder)

        _uiState.update {
            it.copy(
                mods = repositioned,
                appLoadOrder = newAppOrder,
                sourceGameLoadOrder = if (it.sourceGameSaveUriStr == null) {
                    it.sourceGameLoadOrder
                } else newAppOrder
            )
        }

        viewModelScope.launch {
            repository.saveAppLoadOrder(newAppOrder)

            // Write the new order to the SOURCE save (when imported) — a
            // tandem-safe pure permutation of the save's own records.
            val sourceUri = _uiState.value.sourceGameSaveUriStr
            if (sourceUri != null) {
                when (val outcome = saveWriteMutex.withLock {
                            withContext(Dispatchers.IO) {
                                repository.applyLoadOrderToSave(
                                    sourceUri,
                                    newAppOrder,
                                    installedMods = state.mods
                                )
                            }
                        }) {
                    is TransferOutcome.Error ->
                        _uiState.update { it.copy(errorMessage = "Save was not written: ${outcome.reason}") }
                    is TransferOutcome.Refused ->
                        _uiState.update { it.copy(errorMessage = "Save write refused: ${outcome.reason}") }
                    is TransferOutcome.Success -> Unit
                }
            }
        }
    }

    /**
     * Moves the selected active mods as one block to a 1-based load-order
     * position. Their current relative order is preserved; other mods remain
     * in order and are shifted down from the insertion point.
     */
    fun moveSelectedActiveModsToPosition(destinationPosition: Int) {
        val state = _uiState.value
        val selectedIds = state.selectedActiveModIds
        if (selectedIds.isEmpty() || destinationPosition < 1) return

        val currentOrder = state.appLoadOrder
            .ifEmpty { state.displayedGameLoadOrder }
        if (currentOrder.isEmpty()) return

        fun normalized(value: String): String = value.trim().lowercase().replace(" ", "")
        val selectedKeys = state.mods
            .filter { it.id in selectedIds }
            .flatMap { mod -> listOf(mod.title, mod.folderName, mod.id) }
            .map(::normalized)
            .toSet()
        if (selectedKeys.isEmpty()) return

        val selectedEntries = currentOrder.filter { normalized(it) in selectedKeys }
        if (selectedEntries.isEmpty()) return

        val remaining = currentOrder.filterNot { normalized(it) in selectedKeys }
        val insertionIndex = (destinationPosition - 1).coerceIn(0, remaining.size)
        val newAppOrder = buildList {
            addAll(remaining.take(insertionIndex))
            addAll(selectedEntries)
            addAll(remaining.drop(insertionIndex))
        }
        if (newAppOrder == currentOrder) {
            clearActiveModSelection()
            return
        }

        _uiState.update {
            it.copy(
                mods = orderModsForGame(it.mods, newAppOrder),
                appLoadOrder = newAppOrder,
                selectedActiveModIds = emptySet(),
                sourceGameLoadOrder = if (it.sourceGameSaveUriStr == null) {
                    it.sourceGameLoadOrder
                } else newAppOrder
            )
        }

        viewModelScope.launch {
            repository.saveAppLoadOrder(newAppOrder)
            applyAppOrderToSourceSave(newAppOrder)
        }
    }

    fun moveModUp(modId: String) {
        val state = _uiState.value
        // Resolve the mod's position in the APP ORDER (Active-tab list), not
        // the full mods list.
        val name = state.mods.firstOrNull { it.id == modId }?.title ?: return
        val order = state.appLoadOrder.ifEmpty { state.displayedGameLoadOrder }
        val index = order.indexOfFirst { it.equals(name, ignoreCase = true) }
        if (index > 0) moveModInAppOrder(index, index - 1)
    }

    fun moveModDown(modId: String) {
        val state = _uiState.value
        val name = state.mods.firstOrNull { it.id == modId }?.title ?: return
        val order = state.appLoadOrder.ifEmpty { state.displayedGameLoadOrder }
        val index = order.indexOfFirst { it.equals(name, ignoreCase = true) }
        if (index >= 0 && index < order.size - 1) moveModInAppOrder(index, index + 1)
    }

    fun selectMod(mod: Mod?) {
        _uiState.update { it.copy(selectedMod = mod) }
    }

    fun selectCategory(categoryId: String) {
        if (_uiState.value.categories.none { it.id == categoryId }) return
        _uiState.update { it.copy(selectedCategoryId = categoryId) }
        viewModelScope.launch { repository.saveSelectedCategoryId(categoryId) }
    }

    fun addCategory(name: String) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return
        val state = _uiState.value
        if (state.categories.any { it.name.equals(cleanName, ignoreCase = true) }) return
        val category = ModCategory(
            id = "custom_${System.currentTimeMillis()}",
            name = cleanName
        )
        val categories = state.categories + category
        _uiState.update { it.copy(categories = categories, selectedCategoryId = category.id) }
        viewModelScope.launch {
            repository.saveCategories(categories)
            repository.saveSelectedCategoryId(category.id)
        }
    }

    fun renameCustomCategory(categoryId: String, name: String) {
        val cleanName = name.trim()
        val state = _uiState.value
        val category = state.categories.firstOrNull { it.id == categoryId } ?: return
        if (category.builtIn || cleanName.isEmpty()) return
        if (state.categories.any { it.id != categoryId && it.name.equals(cleanName, ignoreCase = true) }) return
        val categories = state.categories.map {
            if (it.id == categoryId) it.copy(name = cleanName) else it
        }
        _uiState.update { it.copy(categories = categories) }
        viewModelScope.launch { repository.saveCategories(categories) }
    }

    fun setCategoryColor(categoryId: String, colorHex: String) {
        val state = _uiState.value
        if (state.categories.none { it.id == categoryId }) return
        val categories = state.categories.map {
            if (it.id == categoryId) it.copy(colorHex = colorHex) else it
        }
        _uiState.update { it.copy(categories = categories) }
        viewModelScope.launch { repository.saveCategories(categories) }
    }

    fun resetCategoryColor(categoryId: String) {
        setCategoryColor(categoryId, ModCategory.DEFAULT_COLOR_HEX)
    }

    fun moveCategoryUp(categoryId: String) {
        moveCustomCategory(categoryId, -1)
    }

    fun moveCategoryDown(categoryId: String) {
        moveCustomCategory(categoryId, 1)
    }

    fun resetBuiltInCategoryOrder() {
        val state = _uiState.value
        val builtInById = state.categories.filter { it.builtIn }.associateBy { it.id }
        val custom = state.categories.filterNot { it.builtIn }
        val categories = ModCategory.BUILT_IN.mapNotNull { builtInById[it.id] } + custom
        _uiState.update { it.copy(categories = categories) }
        viewModelScope.launch { repository.saveCategories(categories) }
    }

    private fun moveCustomCategory(categoryId: String, direction: Int) {
        val state = _uiState.value
        val index = state.categories.indexOfFirst { it.id == categoryId }
        if (index < 0) return
        val target = index + direction
        if (target < 0 || target >= state.categories.size) return
        val categories = state.categories.toMutableList().apply {
            add(target, removeAt(index))
        }
        _uiState.update { it.copy(categories = categories) }
        viewModelScope.launch { repository.saveCategories(categories) }
    }

    fun removeCustomCategory(categoryId: String) {
        val state = _uiState.value
        val category = state.categories.firstOrNull { it.id == categoryId } ?: return
        if (category.builtIn) return
        val categories = state.categories.filterNot { it.id == categoryId }
        val selectedId = if (state.selectedCategoryId == categoryId) {
            categories.firstOrNull()?.id ?: ModCategory.BUILT_IN.first().id
        } else state.selectedCategoryId
        _uiState.update {
            it.copy(
                categories = categories,
                selectedCategoryId = selectedId,
                selectedCategoryFilterId = if (state.selectedCategoryFilterId == categoryId) null
                else state.selectedCategoryFilterId
            )
        }
        viewModelScope.launch {
            repository.saveCategories(categories)
            repository.saveSelectedCategoryId(selectedId)
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun setCategoryFilter(categoryId: String?) {
        if (categoryId != null && categoryId != UNASSIGNED_CATEGORY_FILTER_ID && _uiState.value.categories.none { it.id == categoryId }) return
        _uiState.update { it.copy(selectedCategoryFilterId = categoryId) }
    }

    fun setTagFilter(tag: String?) {
        _uiState.update { it.copy(selectedTagFilter = tag) }
    }

    fun setEnabledFilter(enabled: Boolean?) {
        _uiState.update { it.copy(enabledFilter = enabled) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun clearStatus() {
        _uiState.update { it.copy(statusMessage = null) }
    }
}
