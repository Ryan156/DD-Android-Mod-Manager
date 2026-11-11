package com.darkestdungeon.modmanager

import com.darkestdungeon.modmanager.data.storage.SaveFormatException
import com.darkestdungeon.modmanager.data.storage.SaveParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SaveParserTest {

    @Test
    fun parsesFullRecordStructure() {
        val bytes = SaveTestArchive.buildArchive(
            "First Mod",
            "「灾厄」Scourge Class",
            "Multi\nline? No — tabs\tok"
        )

        val save = SaveParser.parse(bytes)

        assertEquals(3, save.entries.size)
        assertEquals(listOf("First Mod", "「灾厄」Scourge Class", "Multi\nline? No — tabs\tok"), save.loadOrder)
        assertEquals("applied_ugcs_1_0", save.catalogKey)
        save.entries.forEachIndexed { i, entry ->
            assertEquals(i.toString(), entry.key)
            assertEquals("mod_local_source", entry.source)
        }
    }

    @Test
    fun parsesEmptySectionAsFreshCampaign() {
        // Real-world case: a fresh campaign save has persistent_ugcs\0 immediately
        // followed by presented_dlc — no catalog, no records. Must parse as empty.
        val bytes = SaveTestArchive.buildEmptyArchive()

        val save = SaveParser.parse(bytes)

        assertEquals(0, save.entries.size)
        assertEquals(emptyList<String>(), save.loadOrder)
        assertEquals("", save.catalogKey)
    }

    @Test
    fun parsedRecordsRebuildByteExactly() {
        val bytes = SaveTestArchive.buildArchive("Alpha", "Beta", "Gamma", "丹瑾")
        val save = SaveParser.parse(bytes)

        val rebuilt = BinarySaveWriter.rebuildSection(save, save.entries)

        // The rebuilt section content must be byte-identical to the original.
        val originalSection = bytes.copyOfRange(save.sectionStart, save.sectionEnd)
        assertTrue(rebuilt.contentEquals(originalSection))
    }

    @Test
    fun rejectsGarbage() {
        val cases = listOf(
            "not a save at all".encodeToByteArray(),
            "persistent_ugcs".encodeToByteArray(), // marker without content
            SaveTestArchive.buildArchive("Alpha").let {
                // corrupt: truncate mid-record (inside the name value)
                it.copyOf(45)
            },
            // wrong field key
            ByteArrayOutputStream().apply {
                write("persistent_ugcs".encodeToByteArray())
                write(0)
                write("applied_ugcs_1_0".encodeToByteArray())
                write(0)
                write("0".encodeToByteArray()); write(0)
                write("wrong".encodeToByteArray()); write(0)
                // no alignment pad needed at this offset in this synthetic case
                write(le32(6)); write("Alpha\u0000".encodeToByteArray())
            }.toByteArray()
        )

        cases.forEach { bytes ->
            var threw = false
            try {
                SaveParser.parse(bytes)
            } catch (_: SaveFormatException) {
                threw = true
            }
            assertTrue("expected SaveFormatException", threw)
        }
    }

    @Test
    fun alignmentPadsAreDeterministic() {
        // With varied name lengths the length fields land at odd absolute offsets,
        // forcing 1-3 pad bytes; parse must still succeed and rebuild must match.
        val bytes = SaveTestArchive.buildArchive(
            "a", "ab", "abc", "abcd", "Müsä", "「长名字测试」"
        )
        val save = SaveParser.parse(bytes)
        assertEquals(6, save.entries.size)

        val rebuilt = BinarySaveWriter.rebuildSection(save, save.entries)
        assertTrue(
            rebuilt.contentEquals(bytes.copyOfRange(save.sectionStart, save.sectionEnd))
        )
    }

    private fun le32(v: Int): ByteArray = ByteBuffer.allocate(4)
        .order(ByteOrder.LITTLE_ENDIAN)
        .putInt(v)
        .array()
}