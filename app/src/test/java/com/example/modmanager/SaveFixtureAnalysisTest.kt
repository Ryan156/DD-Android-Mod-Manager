package com.darkestdungeon.modmanager

import com.darkestdungeon.modmanager.data.storage.SaveParser
import com.darkestdungeon.modmanager.data.storage.dson.DsonCodec
import com.darkestdungeon.modmanager.data.storage.dson.DsonModListEditor
import com.darkestdungeon.modmanager.data.storage.dson.DsonModRecord
import com.darkestdungeon.modmanager.data.storage.dson.DsonTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class SaveFixtureAnalysisTest {
    @Test
    fun inspectFixtures() {
        val sourceFile = File("C:/Users/evory/Downloads/source.persist.game.json")
        val targetFile = File("C:/Users/evory/Downloads/target.persist.game.json")
        assumeTrue(sourceFile.isFile && targetFile.isFile)

        val source = sourceFile.readBytes()
        val target = targetFile.readBytes()

        val sourceSave = SaveParser.parse(source)
        val targetSave = SaveParser.parse(target)

        println("SOURCE_SIZE=" + source.size)
        println("TARGET_SIZE=" + target.size)
        println("SOURCE_ORDER_COUNT=" + sourceSave.entries.size)
        println("TARGET_ORDER_COUNT=" + targetSave.entries.size)
        println("SOURCE_TOP_COUNT=" + sourceSave.topLevelEntries.size)
        println("TARGET_TOP_COUNT=" + targetSave.topLevelEntries.size)
        println("SOURCE_ORDER=" + sourceSave.loadOrder.joinToString(" | "))
        println("TARGET_ORDER=" + targetSave.loadOrder.joinToString(" | "))

        // Regression: the real source file must fully parse, BOTH lists.
        assertEquals(77, sourceSave.entries.size)
        assertEquals(77, sourceSave.topLevelEntries.size)
        // The game's invariant: both lists hold the same entries in the same order.
        assertEquals(sourceSave.loadOrder, sourceSave.topLevelLoadOrder)

        // MEMBERSHIP-SYNC regression against the real source save (which has
        // both lists) via the REAL codec: drop mods + reorder — the rebuilt
        // container must strictly re-decode with BOTH lists exactly equal to
        // the new order, and all header invariants must hold.
        run {
            val kept = sourceSave.loadOrder.dropLast(2)
            val decoded = DsonCodec.decode(source)
            val records = kept.map { name ->
                DsonModRecord(name, DsonTypes.MOD_LOCAL_SOURCE)
            }
            DsonModListEditor.syncModLists(decoded, records)
            val rebuilt = DsonCodec.encode(decoded)
            val reparsed0 = SaveParser.parse(rebuilt)
            assertEquals(kept, reparsed0.loadOrder)
            assertEquals(kept, reparsed0.topLevelLoadOrder)
        }

        // TANDEM-reorder regression via the REAL codec: a permutation of the
        // source's own records re-encodes byte-safely and keeps the game's
        // two-lists-in-sync invariant.
        run {
            val shuffled = sourceSave.entries.shuffled(java.util.Random(42))
            val decoded = DsonCodec.decode(source)
            val records = shuffled.map { DsonModRecord(it.name, it.source) }
            DsonModListEditor.syncModLists(decoded, records)
            val out = DsonCodec.encode(decoded)
            val reparsed = SaveParser.parse(out)
            assertEquals(shuffled.map { it.name }, reparsed.loadOrder)
            assertEquals(reparsed.loadOrder, reparsed.topLevelLoadOrder)
        }

        // Reorder regression (nested-only, legacy path): a populated real
        // target reorders byte-safely (target-only mods appended, nothing dropped).
        if (targetSave.entries.isNotEmpty()) {
            val plan = SaveTransferPlanner.buildPlan(sourceSave, targetSave)
            assertTrue(plan.canTransfer)
            val write = BinarySaveWriter.reorder(targetSave, plan.orderedTargetEntries)
            assertEquals(null, write.error)
            val written = write.bytes ?: error("reorder failed")
            assertEquals(target.size, written.size)
            assertEquals(
                plan.orderedTargetEntries.map { it.name },
                SaveParser.parse(written).loadOrder
            )
        }
    }
}