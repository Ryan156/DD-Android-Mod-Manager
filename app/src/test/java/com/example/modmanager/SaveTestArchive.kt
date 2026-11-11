package com.darkestdungeon.modmanager

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Shared archive builder producing the exact validated byte format:
 * `persistent_ugcs\0 <catalog>\0 (<idx>\0 name\0 [pad] len32 value\0 source\0 [pad] len32 value\0)* presented_dlc + 32 zero bytes`
 * with 4-byte ABSOLUTE alignment of the length fields.
 */
object SaveTestArchive {

    fun buildArchive(vararg names: String): ByteArray {
        val out = ByteArrayOutputStream()
        val persistent = "persistent_ugcs".encodeToByteArray()
        out.write(persistent)
        out.write(0) // null terminator of the marker string
        val sectionStart = persistent.size + 1

        val catalog = "applied_ugcs_1_0".encodeToByteArray()
        out.write(catalog)
        out.write(0)

        names.forEachIndexed { index, name ->
            out.write(index.toString().encodeToByteArray())
            out.write(0)

            val nameBytes = (name + "\u0000").encodeToByteArray()
            writeAlignedValue(out, "name", nameBytes, sectionStart)
            val sourceBytes = "mod_local_source\u0000".encodeToByteArray()
            writeAlignedValue(out, "source", sourceBytes, sectionStart)
        }

        val presented = "presented_dlc".encodeToByteArray()
        out.write(presented)
        out.write(ByteArray(32))
        return out.toByteArray()
    }

    /** A fresh campaign save: persistent_ugcs\0 immediately followed by presented_dlc. */
    fun buildEmptyArchive(): ByteArray {
        val out = ByteArrayOutputStream()
        out.write("persistent_ugcs".encodeToByteArray())
        out.write(0)
        out.write("presented_dlc".encodeToByteArray())
        out.write(ByteArray(32))
        return out.toByteArray()
    }

    private fun writeAlignedValue(out: ByteArrayOutputStream, key: String, value: ByteArray, sectionStart: Int) {
        out.write(key.encodeToByteArray())
        out.write(0)
        // Absolute file offset of the length field must be divisible by 4.
        while ((sectionStart + out.size()) % 4 != 0) out.write(0)
        out.write(le32(value.size))
        out.write(value)
    }

    private fun le32(v: Int): ByteArray = ByteBuffer.allocate(4)
        .order(ByteOrder.LITTLE_ENDIAN)
        .putInt(v)
        .array()
}