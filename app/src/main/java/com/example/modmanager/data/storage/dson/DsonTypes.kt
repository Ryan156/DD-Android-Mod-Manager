package com.darkestdungeon.modmanager.data.storage.dson

import com.darkestdungeon.modmanager.data.storage.SaveFormatException
import java.nio.charset.StandardCharsets

/**
 * Constants and bit helpers for the Wicked Android port's DSON container
 * (`persist.game.json`), byte-verified against real save files.
 */
object DsonTypes {

    /** Magic number at offset 0x00: `01 B1 00 00`. */
    const val MAGIC_NR: Int = 0x0000B101

    /** Total header size in bytes. */
    const val HEADER_SIZE: Int = 64

    /** Fixed offsets of the header fields. */
    const val OFF_MAGIC: Int = 0x00
    const val OFF_REVISION: Int = 0x04
    const val OFF_HEADER_LENGTH: Int = 0x08
    const val OFF_ZEROES_1: Int = 0x0C
    const val OFF_META1_SIZE: Int = 0x10
    const val OFF_NUM_META1: Int = 0x14
    const val OFF_META1_OFFSET: Int = 0x18
    const val OFF_ZEROES_2: Int = 0x1C // 8 bytes
    const val OFF_ZEROES_3: Int = 0x24 // 8 bytes
    const val OFF_NUM_META2: Int = 0x2C
    const val OFF_META2_OFFSET: Int = 0x30
    const val OFF_ZEROES_4: Int = 0x34
    const val OFF_DATA_LENGTH: Int = 0x38
    const val OFF_DATA_OFFSET: Int = 0x3C

    /** Size of one Meta1 entry. */
    const val META1_ENTRY_SIZE: Int = 16

    /** Size of one Meta2 entry. */
    const val META2_ENTRY_SIZE: Int = 12

    /** meta1Size == numMeta1Entries << 4. */
    const val META1_SIZE_SHIFT: Int = 4

    /** Marker strings (root object field name and the mod lists). */
    const val ROOT_FIELD_NAME: String = "base_root"
    const val MOD_LIST_FIELD_NAME: String = "applied_ugcs_1_0"
    const val PERSISTENT_FIELD_NAME: String = "persistent_ugcs"
    const val MOD_LOCAL_SOURCE: String = "mod_local_source"

    // ---- fieldInfo bit layout ----

    /** bit0: 1 = object field, 0 = primitive. */
    const val FIELD_FLAG_OBJECT: Int = 0x1

    /** bits 2..10: field name length INCLUDING the trailing `\0`. */
    const val NAME_LENGTH_MASK: Int = 0x3FC
    const val NAME_LENGTH_SHIFT: Int = 2

    /** bits 11+: index into Meta1 (only meaningful for object fields). */
    const val META1_INDEX_SHIFT: Int = 11

    /** True when [fieldInfo] marks the field as an object. */
    fun isObjectField(fieldInfo: Int): Boolean = (fieldInfo and FIELD_FLAG_OBJECT) != 0

    /** Name length (incl. `\0`) encoded in [fieldInfo]. */
    fun nameLengthOf(fieldInfo: Int): Int = (fieldInfo and NAME_LENGTH_MASK) shr NAME_LENGTH_SHIFT

    /**
     * Meta1 index encoded in [fieldInfo] (object fields only).
     *
     * Some Wicked saves set bit 31 as an additional object-field flag. That
     * bit is not part of the 20-bit Meta1 index and must be masked out.
     */
    fun meta1IndexOf(fieldInfo: Int): Int =
        (fieldInfo ushr META1_INDEX_SHIFT) and 0xFFFFF

    /** Builds a fieldInfo word. */
    fun fieldInfo(isObject: Boolean, nameLen: Int, meta1Idx: Int = 0): Int =
        (if (isObject) FIELD_FLAG_OBJECT else 0) or
            ((nameLen shl NAME_LENGTH_SHIFT) and NAME_LENGTH_MASK) or
            (meta1Idx shl META1_INDEX_SHIFT)

    /**
     * The DSON string hash (Java `int` semantics, 32-bit wraparound), used for
     * the Meta2 nameHash fields. Verified byte-identical against every stored
     * hash of both reference saves.
     */
    fun stringHash(str: String): Int {
        var hash = 0
        val arr = str.toByteArray(StandardCharsets.UTF_8)
        for (b in arr) {
            hash = hash * 53 + (b.toInt() and 0xFF)
        }
        return hash
    }

    /** Number of 0x00 pad bytes needed to bring [pos] to a multiple of 4. */
    fun padToAlign(pos: Int): Int = (4 - (pos and 0x3)) and 0x3

    /** Reads a little-endian int32. */
    fun readLe32(bytes: ByteArray, pos: Int): Int =
        (bytes[pos].toInt() and 0xFF) or
            ((bytes[pos + 1].toInt() and 0xFF) shl 8) or
            ((bytes[pos + 2].toInt() and 0xFF) shl 16) or
            ((bytes[pos + 3].toInt() and 0xFF) shl 24)

    /** Writes a little-endian int32. */
    fun writeLe32(bytes: ByteArray, pos: Int, value: Int) {
        bytes[pos] = (value and 0xFF).toByte()
        bytes[pos + 1] = ((value shr 8) and 0xFF).toByte()
        bytes[pos + 2] = ((value shr 16) and 0xFF).toByte()
        bytes[pos + 3] = ((value shr 24) and 0xFF).toByte()
    }

    /** Decodes a length-prefixed DSON string value (length INCLUDES trailing `\0`). */
    fun decodeStringValue(bytes: ByteArray, pos: Int): Pair<String, Int> {
        if (pos + 4 > bytes.size) {
            throw SaveFormatException("String length at $pos exceeds the data block")
        }
        val length = readLe32(bytes, pos)
        val after = pos + 4
        if (length <= 0 || after + length > bytes.size) {
            throw SaveFormatException("String length $length at $pos exceeds the data block")
        }
        val raw = bytes.copyOfRange(after, after + length)
        val decoded = raw.toString(StandardCharsets.UTF_8)
        return decoded.trimEnd { it == '\u0000' } to (after + length)
    }
}