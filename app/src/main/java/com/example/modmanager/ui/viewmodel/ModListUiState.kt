package com.darkestdungeon.modmanager.ui.viewmodel

import com.darkestdungeon.modmanager.data.model.Mod
import com.darkestdungeon.modmanager.data.model.ModCategory
import com.darkestdungeon.modmanager.ui.viewmodel.ModManagerViewModel.TransferReport

const val UNASSIGNED_CATEGORY_FILTER_ID = "__unassigned__"

data class CategoryAssignmentReport(
    val assignedCount: Int,
    val unassignedModNames: List<String>
)

data class ModListUiState(
    val mods: List<Mod> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val activeFolderPath: String = "",
    val activeFolderUriStr: String? = null,
    val gameSaveUriStr: String? = null,
    val gameLoadOrder: List<String> = emptyList(),
    val sourceGameSaveUriStr: String? = null,
    val sourceGameLoadOrder: List<String> = emptyList(),
    /** The APP's OWN load order (source of truth): active mods in current order. */
    val appLoadOrder: List<String> = emptyList(),
    /** True after the app has explicitly initialized or changed its own order. */
    val appLoadOrderIsAuthoritative: Boolean = false,
    /** Staged folder-tab order; changing it does not write the source save. */
    val folderOrder: List<String> = emptyList(),
    /** Folder-tab batch selection; this is separate from active/inactive state. */
    val selectedFolderModIds: Set<String> = emptySet(),
    /** Active-tab selection for grouped load-order moves. */
    val selectedActiveModIds: Set<String> = emptySet(),
    val categories: List<ModCategory> = ModCategory.defaults(),
    val selectedCategoryId: String = ModCategory.BUILT_IN.first().id,
    val selectedMod: Mod? = null,
    val searchQuery: String = "",
    /** null means All categories; otherwise filters every mod list tab. */
    val selectedCategoryFilterId: String? = null,
    val selectedTagFilter: String? = null,
    val enabledFilter: Boolean? = null, // null = All, true = Enabled, false = Disabled
    val isTransferring: Boolean = false,
    val transferReport: TransferReport? = null,
    /** Transient non-error status (e.g. "save written") — shown in the snackbar. */
    val statusMessage: String? = null,
    val categoryAssignmentReport: CategoryAssignmentReport? = null
) {
    /**
     * Display classification order.
     *
     * Once a source save is imported, the app order is authoritative even
     * when it is empty. Falling back to the target save after disabling all
     * mods would incorrectly make every target-save mod appear active.
     */
    val displayedGameLoadOrder: List<String>
        get() = when {
            appLoadOrderIsAuthoritative -> appLoadOrder
            sourceGameSaveUriStr != null -> sourceGameLoadOrder
            else -> appLoadOrder.ifEmpty { sourceGameLoadOrder.ifEmpty { gameLoadOrder } }
        }

    val filteredMods: List<Mod>
        get() = mods.filter { mod ->
            matchesSearch(mod) && (enabledFilter == null || mod.isEnabled == enabledFilter)
        }

    private fun matchesSearch(mod: Mod): Boolean {
        val matchesCategory = when (selectedCategoryFilterId) {
            null -> true
            UNASSIGNED_CATEGORY_FILTER_ID -> mod.categoryId == null
            else -> mod.categoryId == selectedCategoryFilterId
        }
        val matchesQuery = searchQuery.isBlank() ||
            mod.title.contains(searchQuery, ignoreCase = true) ||
            mod.author.contains(searchQuery, ignoreCase = true) ||
            mod.description.contains(searchQuery, ignoreCase = true) ||
            mod.folderName.contains(searchQuery, ignoreCase = true)
        return matchesCategory && matchesQuery
    }

    private fun normalized(value: String): String =
        value.trim().lowercase().replace(" ", "")

    private fun matchesSaved(saved: String, mod: Mod): Boolean =
        listOf(mod.title, mod.folderName, mod.id)
            .any { normalized(it) == normalized(saved) }

    /**
     * Matches save entries to installed mods one-to-one. A duplicate folder
     * title or identifier must not make several installed folders appear active
     * from one save record.
     */
    private fun matchedActiveMods(): List<Pair<Mod, Int>> {
        val remainingMods = mods.toMutableList()
        return displayedGameLoadOrder.mapIndexedNotNull { position, saved ->
            val match = remainingMods.firstOrNull { mod -> matchesSaved(saved, mod) }
                ?: return@mapIndexedNotNull null
            remainingMods.remove(match)
            match to position
        }
    }

    /** Folder view is grouped by the user-configured category order. */
    val folderMods: List<Mod>
        get() {
            val categoryOrder = categories.mapIndexed { index, category -> category.id to index }.toMap()
            val stagedPosition = folderOrder.withIndex().associate { it.value to it.index }
            return mods
                .filter(::matchesSearch)
                .sortedWith(
                    compareBy<Mod> { stagedPosition[it.id] ?: Int.MAX_VALUE }
                        .thenBy { categoryOrder[it.categoryId] ?: Int.MAX_VALUE }
                        .thenBy { it.title.lowercase() }
                )
        }

    /** Active = matched one-to-one against the current source-save order. */
    val activeMods: List<Mod>
        get() = matchedActiveMods()
            .filter { (mod, _) -> matchesSearch(mod) }
            .sortedBy { (_, position) -> position }
            .map { (mod, _) -> mod }

    /**
     * Inactive = installed mods left after one-to-one active matching.
     * Their visual order follows the reserve/folder order so the user can
     * compare the inactive list directly with the staged order.
     */
    val inactiveMods: List<Mod>
        get() {
            val activeMods = matchedActiveMods().map { it.first }.toSet()
            val stagedPosition = folderOrder.withIndex().associate { it.value to it.index }
            val categoryOrder = categories.mapIndexed { index, category -> category.id to index }.toMap()
            return mods
                .filter { matchesSearch(it) && it !in activeMods }
                .sortedWith(
                    compareBy<Mod> { stagedPosition[it.id] ?: Int.MAX_VALUE }
                        .thenBy { categoryOrder[it.categoryId] ?: Int.MAX_VALUE }
                        .thenBy { it.title.lowercase() }
                )
        }

    /**
     * Statistics always describe the complete mod collection. They must not be
     * affected by the search field or category selector, which only control
     * what is visible in the list tabs.
     */
    val enabledCount: Int
        get() = if (appLoadOrderIsAuthoritative || displayedGameLoadOrder.isNotEmpty()) {
            matchedActiveMods().size
        } else {
            mods.count { it.isEnabled }
        }

    val disabledCount: Int
        get() = if (appLoadOrderIsAuthoritative || displayedGameLoadOrder.isNotEmpty()) {
            mods.size - matchedActiveMods().size
        } else {
            mods.count { !it.isEnabled }
        }
}