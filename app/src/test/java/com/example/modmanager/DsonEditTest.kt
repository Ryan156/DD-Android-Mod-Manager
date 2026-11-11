package com.darkestdungeon.modmanager

import com.darkestdungeon.modmanager.data.storage.SaveFormatException
import com.darkestdungeon.modmanager.data.storage.SaveParser
import com.darkestdungeon.modmanager.data.storage.dson.DsonCodec
import com.darkestdungeon.modmanager.data.storage.dson.DsonModListEditor
import com.darkestdungeon.modmanager.data.storage.dson.DsonModRecord
import com.darkestdungeon.modmanager.data.storage.dson.DsonTypes
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Membership-changing edits on the REAL source save via the codec: rebuild
 * both mod lists to a subset, re-decode, and verify nothing else changed.
 */
class DsonEditTest {

    private val sourceFile = File("C:/Users/evory/Downloads/source.persist.game.json")

    @Test
    fun syncToSubsetAndReDecodeIsConsistent() {
        assumeTrue(sourceFile.isFile)
        val source = sourceFile.readBytes()
        val original = DsonCodec.decode(source)

        // 3-record subset, in a deliberately different order.
        val names = listOf(
            "ZIMIK Arbalest skin",
            "【ZIMIK】 Abomination Skin ",
            "Marika NSFW SKIN",
        )
        val records = names.map { DsonModRecord(it, DsonTypes.MOD_LOCAL_SOURCE) }

        DsonModListEditor.syncModLists(original, records)
        val rebuilt = DsonCodec.encode(original)
        val redecoded = DsonCodec.decode(rebuilt)

        val lists = DsonModListEditor.extractModLists(redecoded)
        val top = DsonModListEditor.readRecords(lists.topLevel)
        val nested = DsonModListEditor.readRecords(lists.nested)
        assertEquals(records, top)
        assertEquals(records, nested)

        // Header invariants hold on the rebuilt file.
        assertEquals(64, redecoded.header.headerLength)
        assertEquals(redecoded.header.numMeta1Entries shl 4, redecoded.header.meta1Size)
        assertEquals(
            64 + redecoded.header.meta1Size + redecoded.header.numMeta2Entries * 12,
            redecoded.header.dataOffset
        )
        assertEquals(rebuilt.size, redecoded.header.dataOffset + redecoded.header.dataLength)
        // The tree shrank: 2 lists of 77 records became 2 lists of 3 records.
        assertTrue(redecoded.header.numMeta2Entries < 535)

        // estatename / game_mode strings are untouched.
        val stringsBefore = DsonCodec.decode(source).root.children
            .mapNotNull { (it as? com.darkestdungeon.modmanager.data.storage.dson.DsonPrimitive)?.stringValue }
        val stringsAfter = redecoded.root.children
            .mapNotNull { (it as? com.darkestdungeon.modmanager.data.storage.dson.DsonPrimitive)?.stringValue }
        assertEquals(stringsBefore, stringsAfter)

        // Re-encode → decode again → identical (encode is a pure function of the tree).
        val twice = DsonCodec.encode(DsonCodec.decode(rebuilt))
        assertArrayEquals(rebuilt, twice)

        // The legacy heuristic parser still reads the edited file correctly.
        val legacy = SaveParser.parse(rebuilt)
        assertEquals(names, legacy.loadOrder)
        assertEquals(names, legacy.topLevelLoadOrder)
    }

    @Test
    fun untouchedEncodeOfDecodedFileIsByteIdentical() {
        assumeTrue(sourceFile.isFile)
        val source = sourceFile.readBytes()
        val decoded = DsonCodec.decode(source)
        assertArrayEquals(source, DsonCodec.encode(decoded))
    }

    @Test
    fun malformedRecordThrowsSaveFormatException() {
        assumeTrue(sourceFile.isFile)
        val decoded = DsonCodec.decode(sourceFile.readBytes())
        // Corrupt a list object into holding a primitive child.
        val lists = DsonModListEditor.extractModLists(decoded)
        lists.topLevel!!.children.clear()
        lists.topLevel.children += com.darkestdungeon.modmanager.data.storage.dson.DsonPrimitive(
            name = "oops",
            kind = com.darkestdungeon.modmanager.data.storage.dson.DsonPrimitiveKind.STRING,
            valueBytes = "x".encodeToByteArray() + 0,
        )
        var threw = false
        try {
            DsonModListEditor.readRecords(lists.topLevel)
        } catch (_: SaveFormatException) {
            threw = true
        }
        assertTrue(threw)
    }
}