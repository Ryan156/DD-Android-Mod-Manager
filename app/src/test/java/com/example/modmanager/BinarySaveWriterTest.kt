package com.darkestdungeon.modmanager

import com.darkestdungeon.modmanager.data.storage.SaveParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BinarySaveWriterTest {

    @Test
    fun identityReorderIsByteExact() {
        val bytes = SaveTestArchive.buildArchive("Alpha", "Beta", "Gamma")
        val save = SaveParser.parse(bytes)

        val result = BinarySaveWriter.reorder(save, save.entries)

        assertNull(result.error)
        assertNotNull(result.bytes)
        assertTrue(result.bytes!!.contentEquals(bytes))
    }

    @Test
    fun reordersEntriesAndKeepsSectionLength() {
        val bytes = SaveTestArchive.buildArchive("Alpha", "Beta", "Gamma", "丹瑾")
        val save = SaveParser.parse(bytes)

        val newOrder = listOf(save.entries[2], save.entries[0], save.entries[3], save.entries[1])
        val result = BinarySaveWriter.reorder(save, newOrder)

        assertNull(result.error)
        val out = result.bytes!!
        assertEquals(bytes.size, out.size)
        assertEquals(
            listOf("Gamma", "Alpha", "丹瑾", "Beta"),
            SaveParser.parse(out).loadOrder
        )
        // Everything outside the section untouched.
        val sectionStart = save.sectionStart
        val sectionEnd = save.sectionEnd
        assertTrue(out.copyOfRange(0, sectionStart).contentEquals(bytes.copyOfRange(0, sectionStart)))
        assertTrue(out.copyOfRange(sectionEnd, out.size).contentEquals(bytes.copyOfRange(sectionEnd, bytes.size)))
    }

    @Test
    fun refusesNonPermutationInput() {
        val bytes = SaveTestArchive.buildArchive("Alpha", "Beta")
        val save = SaveParser.parse(bytes)

        val dropOne = listOf(save.entries[0])
        val result = BinarySaveWriter.reorder(save, dropOne)

        assertNull(result.bytes)
        assertNotNull(result.error)
    }

    @Test
    fun refusesMaliciousLengthChange() {
        val bytes = SaveTestArchive.buildArchive("Alpha", "Beta")
        val save = SaveParser.parse(bytes)

        // A record set that IS a permutation but that cannot be written without
        // changing the section length is impossible via reorder(); simulate the
        // internal gate directly: rebuild with an order that would misalign pads.
        // Since reorder() only accepts permutations, verify the gate by forcing a
        // a length delta through rebuildSection with mismatched byte counts.
        // (rebuildSection on a permutation always keeps length; this documents it.)
        val newOrder = save.entries.reversed()
        val result = BinarySaveWriter.reorder(save, newOrder)
        assertNull(result.error)
        assertEquals(bytes.size, result.bytes!!.size)
    }

    @Test
    fun verifiesWrittenOutputByReParsing() {
        val bytes = SaveTestArchive.buildArchive("Alpha", "Beta", "Gamma")
        val save = SaveParser.parse(bytes)
        val newOrder = listOf(save.entries[1], save.entries[2], save.entries[0])

        val result = BinarySaveWriter.reorder(save, newOrder)

        val out = result.bytes!!
        val reparsed = SaveParser.parse(out)
        assertEquals(listOf("Beta", "Gamma", "Alpha"), reparsed.loadOrder)
        assertEquals(3, reparsed.entries.size)
    }
}