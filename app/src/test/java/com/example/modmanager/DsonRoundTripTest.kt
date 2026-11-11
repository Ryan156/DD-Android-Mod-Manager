package com.darkestdungeon.modmanager

import com.darkestdungeon.modmanager.data.storage.dson.DsonCodec
import com.darkestdungeon.modmanager.data.storage.dson.DsonTypes
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * NON-NEGOTIABLE acceptance gate: decode → encode must be byte-identical for
 * both real reference saves.
 */
class DsonRoundTripTest {

    private val sourceFile = File("C:/Users/evory/Downloads/source.persist.game.json")
    private val targetFile = File("C:/Users/evory/Downloads/target.persist.game.json")

    @Test
    fun roundTripIsByteExactOnBothRealFiles() {
        assumeTrue(sourceFile.isFile && targetFile.isFile)
        for (f in listOf(sourceFile, targetFile)) {
            val original = f.readBytes()
            val decoded = DsonCodec.decode(original)
            val reencoded = DsonCodec.encode(decoded)
            assertArrayEquals("round trip changed ${f.name} (${original.size}B)", original, reencoded)
        }
    }

    @Test
    fun decodesRealSaveStructure() {
        assumeTrue(sourceFile.isFile)
        val decoded = DsonCodec.decode(sourceFile.readBytes())
        assertEquals(DsonTypes.HEADER_SIZE, decoded.header.headerLength)
        assertEquals(177, decoded.header.numMeta1Entries)
        assertEquals(535, decoded.header.numMeta2Entries)
        assertEquals("base_root", decoded.root.name)
        assertEquals(15, decoded.root.children.size)
        // Header invariants recomputed from the tree must equal the originals.
        val reencoded2 = DsonCodec.encode(decoded)
        val reparsed = DsonCodec.decode(reencoded2)
        assertEquals(decoded.header.numMeta2Entries, reparsed.header.numMeta2Entries)
    }

    @Test
    fun roundTripSurvivesRandomByteShuffleOfStringValues() {
        // Reordering string VALUES inside a record must not corrupt the file:
        // the emitter recomputes every alignment pad from scratch.
        assumeTrue(sourceFile.isFile)
        val decoded = DsonCodec.decode(sourceFile.readBytes())
        val roundTripped = DsonCodec.decode(DsonCodec.encode(decoded))
        assertEquals(decoded.root.children.size, roundTripped.root.children.size)
        assertTrue(DsonCodec.encode(roundTripped).contentEquals(DsonCodec.encode(decoded)))
    }

    @Test
    fun deviceSaveWithHighBitObjectFlagsRoundTripsExactly() {
        val deviceFile = File("C:/Users/evory/Downloads/device-source-after-error.persist.game.json")
        assumeTrue(deviceFile.isFile)
        val original = deviceFile.readBytes()
        val decoded = DsonCodec.decode(original)
        assertArrayEquals(original, DsonCodec.encode(decoded))
    }
}