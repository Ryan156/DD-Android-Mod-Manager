package com.darkestdungeon.modmanager

import com.darkestdungeon.modmanager.data.storage.SaveParser
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The old `BinarySaveScanner` heuristic reader was removed; its test now covers
 * the strict `SaveParser` with the old scanner's public contract:
 * ordered mod names + warnings for unreadable files.
 */
class BinarySaveScannerTest {

    @Test
    fun extractsPersistentModNamesInOrder() {
        val bytes = SaveTestArchive.buildArchive(
            "First Mod",
            "第二个 Mod",
            "First Mod" // duplicate names are preserved by the strict parser
        )

        val save = SaveParser.parse(bytes)

        assertEquals(listOf("First Mod", "第二个 Mod", "First Mod"), save.loadOrder)
    }

    @Test
    fun duplicateNamesDoNotBreakParsing() {
        val bytes = SaveTestArchive.buildArchive("Same", "Same")
        val save = SaveParser.parse(bytes)
        assertEquals(listOf("Same", "Same"), save.loadOrder)
        assertEquals(2, save.entries.size)
    }

    @Test
    fun reportsMissingPersistentSection() {
        // Not a save: parse throws, surfaced as a load-order result upstream.
        val bytes = "not a save".encodeToByteArray()
        var message: String? = null
        try {
            SaveParser.parse(bytes)
        } catch (e: com.darkestdungeon.modmanager.data.storage.SaveFormatException) {
            message = e.message
        }
        assertEquals("persistent_ugcs was not found", message)
    }

    @Test
    fun handlesMultiDigitRecordKeys() {
        val names = (0 until 12).map { "Mod $it" }
        val bytes = SaveTestArchive.buildArchive(*names.toTypedArray())
        val save = SaveParser.parse(bytes)
        assertEquals(names, save.loadOrder)
        assertEquals("11", save.entries.last().key)
    }
}