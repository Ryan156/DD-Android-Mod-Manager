package com.darkestdungeon.modmanager.ui.viewmodel

import com.darkestdungeon.modmanager.data.model.Mod
import com.darkestdungeon.modmanager.ui.viewmodel.ModManagerViewModel.TransferReport

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
    /** Folder-tab batch selection; this is separate from active/inactive state. */
    val selectedFolderModIds: Set<String> = emptySet(),
    val selectedMod: Mod? = null,
    val searchQuery: String = "",
    val selectedTagFilter: String? = null,
    val enabledFilter: Boolean? = null, // null = All, true = Enabled, false = Disabled
    val isTransferring: Boolean = false,
    val transferReport: TransferReport? = null,
    /** Transient non-error status (e.g. "save written") — shown in the snackbar. */
    val statusMessage: String? = null
) {
    /** Display classification order: app order wins, else source save, else target save. */
    val displayedGameLoadOrder: List<String>
        get() = appLoadOrder.ifEmpty { sourceGameLoadOrder.ifEmpty { gameLoadOrder } }

    val filteredMods: List<Mod>
        get() = mods.filter { mod ->
            val matchesQuery = searchQuery.isBlank() ||
                    mod.title.contains(searchQuery, ignoreCase = true) ||
                    mod.author.contains(searchQuery, ignoreCase = true) ||
                    mod.description.contains(searchQuery, ignoreCase = true) ||
                    mod.folderName.contains(searchQuery, ignoreCase = true)

            val matchesEnabled = enabledFilter == null || mod.isEnabled == enabledFilter

            matchesQuery && matchesEnabled
        }

    private fun matchesSearch(mod: Mod): Boolean =
        searchQuery.isBlank() ||
            mod.title.contains(searchQuery, ignoreCase = true) ||
            mod.author.contains(searchQuery, ignoreCase = true) ||
            mod.description.contains(searchQuery, ignoreCase = true) ||
            mod.folderName.contains(searchQuery, ignoreCase = true)

    private fun gamePosition(mod: Mod): Int {
        val candidates = listOf(mod.title, mod.folderName, mod.id)
            .map { it.trim().lowercase() }
        return displayedGameLoadOrder.indexOfFirst { saved ->
            val normalized = saved.trim().lowercase().replace(" ", "")
            candidates.any { candidate ->
                candidate.replace(" ", "") == normalized
            }
        }
    }

    val folderMods: List<Mod>
        get() = mods.filter(::matchesSearch)

    /** Active = present in the current source-of-truth order, in that order. */
    val activeMods: List<Mod>
        get() = mods
            .filter { matchesSearch(it) && gamePosition(it) >= 0 }
            .sortedBy { gamePosition(it) }

    /** Inactive = not present in the current source-of-truth order. */
    val inactiveMods: List<Mod>
        get() = mods
            .filter { matchesSearch(it) && gamePosition(it) < 0 }
            .sortedBy { it.title.lowercase() }

    val enabledCount: Int
        get() = if (displayedGameLoadOrder.isNotEmpty()) activeMods.size else mods.count { it.isEnabled }

    val disabledCount: Int
        get() = if (displayedGameLoadOrder.isNotEmpty()) inactiveMods.size else mods.count { !it.isEnabled }
}