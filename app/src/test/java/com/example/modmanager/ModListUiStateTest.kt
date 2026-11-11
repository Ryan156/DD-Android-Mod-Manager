package com.darkestdungeon.modmanager

import com.darkestdungeon.modmanager.data.model.Mod
import com.darkestdungeon.modmanager.ui.viewmodel.ModListUiState
import org.junit.Assert.assertEquals
import org.junit.Test

class ModListUiStateTest {

    private val sampleMods = listOf(
        Mod(
            id = "falconer",
            title = "The Falconer Class Mod",
            description = "Marksman hero class",
            author = "Marney",
            version = "2.4",
            folderName = "falconer",
            folderPath = "/mods/falconer",
            isEnabled = true,
            loadOrder = 0,
            tags = listOf("Hero Class", "Trinkets")
        ),
        Mod(
            id = "mariner",
            title = "The Mariner Hero",
            description = "Seafaring warrior",
            author = "ActionJack",
            version = "1.8",
            folderName = "mariner",
            folderPath = "/mods/mariner",
            isEnabled = true,
            loadOrder = 1,
            tags = listOf("Hero Class")
        ),
        Mod(
            id = "speed_mod",
            title = "Faster Dungeon Speed",
            description = "Quick movement QoL",
            author = "ModderX",
            version = "1.0",
            folderName = "speed_mod",
            folderPath = "/mods/speed_mod",
            isEnabled = false,
            loadOrder = 2,
            tags = listOf("QoL")
        )
    )

    @Test
    fun testFilteredModsSearchQuery() {
        val state = ModListUiState(mods = sampleMods, searchQuery = "Mariner")
        val filtered = state.filteredMods

        assertEquals(1, filtered.size)
        assertEquals("The Mariner Hero", filtered.first().title)
    }
    @Test
    fun testFilteredModsEnabledFilter() {
        val stateEnabled = ModListUiState(mods = sampleMods, enabledFilter = true)
        assertEquals(2, stateEnabled.filteredMods.size)

        val stateDisabled = ModListUiState(mods = sampleMods, enabledFilter = false)
        assertEquals(1, stateDisabled.filteredMods.size)
        assertEquals("speed_mod", stateDisabled.filteredMods.first().id)
    }
    @Test
    fun testCounts() {
        val state = ModListUiState(mods = sampleMods)
        assertEquals(2, state.enabledCount)
        assertEquals(1, state.disabledCount)
    }
}
