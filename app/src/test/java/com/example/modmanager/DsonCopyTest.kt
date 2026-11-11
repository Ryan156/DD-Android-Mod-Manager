package com.darkestdungeon.modmanager

import com.darkestdungeon.modmanager.data.storage.dson.DsonCodec
import com.darkestdungeon.modmanager.data.storage.dson.DsonModListEditor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * The COPY feature: a fresh-campaign target (EMPTY persistent_ugcs, NO
 * top-level list) receives the source save's 77 records, with both lists
 * CREATED, via the codec.
 */
class DsonCopyTest {

    private val sourceFile = File("C:/Users/evory/Downloads/source.persist.game.json")
    private val targetFile = File("C:/Users/evory/Downloads/target.persist.game.json")

    @Test
    fun copiesSourceRecordsIntoFreshTargetCreatingBothLists() {
        assumeTrue(sourceFile.isFile && targetFile.isFile)
        val sourceBytes = sourceFile.readBytes()
        val targetBytes = targetFile.readBytes()

        // Source records, verbatim.
        val sourceDecoded = DsonCodec.decode(sourceBytes)
        val sourceLists = DsonModListEditor.extractModLists(sourceDecoded)
        val sourceRecords = DsonModListEditor.readRecords(sourceLists.topLevel ?: sourceLists.nested)
        assertEquals(77, sourceRecords.size)

        // Target is fresh: no lists at all.
        val targetDecoded = DsonCodec.decode(targetBytes)
        val targetLists = DsonModListEditor.extractModLists(targetDecoded)
        assertEquals(null, targetLists.topLevel)
        assertEquals(null, targetLists.nested)

        DsonModListEditor.syncModLists(targetDecoded, sourceRecords)
        val rebuilt = DsonCodec.encode(targetDecoded)
        val redecoded = DsonCodec.decode(rebuilt)

        val lists = DsonModListEditor.extractModLists(redecoded)
        assertNotNull(lists.topLevel)
        assertNotNull(lists.nested)
        assertEquals(sourceRecords, DsonModListEditor.readRecords(lists.topLevel))
        assertEquals(sourceRecords, DsonModListEditor.readRecords(lists.nested))
        assertEquals(77, lists.topLevel!!.children.size)
        assertEquals(77, lists.nested!!.children.size)

        // The target's own non-mod-list content stays untouched: same root
        // children count minus the two created fields, same estatename string.
        val estatename = redecoded.root.children
            .mapNotNull { (it as? com.darkestdungeon.modmanager.data.storage.dson.DsonPrimitive)?.stringValue }
        assertEquals(
            DsonCodec.decode(targetBytes).root.children
                .mapNotNull { (it as? com.darkestdungeon.modmanager.data.storage.dson.DsonPrimitive)?.stringValue },
            estatename
        )

        // Root now has 2 more children (the created top-level list and the
        // created nested list's parent was present already).
        assertTrue(redecoded.root.children.size >= DsonCodec.decode(targetBytes).root.children.size)

        // DLC lists must NOT be touched: the fresh target had presented_dlc/dlc.
        assertEquals(
            DsonCodec.decode(targetBytes).root.children.count { it.name == "presented_dlc" },
            redecoded.root.children.count { it.name == "presented_dlc" }
        )
    }

    @Test
    fun copiedTargetIsStableUnderSecondEncode() {
        assumeTrue(sourceFile.isFile && targetFile.isFile)
        val sourceDecoded = DsonCodec.decode(sourceFile.readBytes())
        val sourceLists = DsonModListEditor.extractModLists(sourceDecoded)
        val records = DsonModListEditor.readRecords(sourceLists.topLevel ?: sourceLists.nested)

        val targetDecoded = DsonCodec.decode(targetFile.readBytes())
        DsonModListEditor.syncModLists(targetDecoded, records)
        val once = DsonCodec.encode(targetDecoded)
        val twice = DsonCodec.encode(DsonCodec.decode(once))
        assertTrue(once.contentEquals(twice))
    }

    @Test
    fun recordOrderAndSourcesArePreservedVerbatim() {
        assumeTrue(sourceFile.isFile && targetFile.isFile)
        val sourceDecoded = DsonCodec.decode(sourceFile.readBytes())
        val sourceLists = DsonModListEditor.extractModLists(sourceDecoded)
        val records = DsonModListEditor.readRecords(sourceLists.topLevel ?: sourceLists.nested)

        val targetDecoded = DsonCodec.decode(targetFile.readBytes())
        DsonModListEditor.syncModLists(targetDecoded, records)
        val redecoded = DsonCodec.decode(DsonCodec.encode(targetDecoded))
        val lists = DsonModListEditor.extractModLists(redecoded)
        val copied = DsonModListEditor.readRecords(lists.topLevel ?: lists.nested)
        assertEquals(records, copied)
        assertEquals(
            records.map { it.source }.distinct(),
            copied.map { it.source }.distinct()
        )
        assertEquals(listOf("mod_local_source"), records.map { it.source }.distinct())
    }
}