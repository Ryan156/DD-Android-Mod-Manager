package com.darkestdungeon.modmanager.data.storage

import com.darkestdungeon.modmanager.data.storage.dson.DsonCodec
import com.darkestdungeon.modmanager.data.storage.dson.DsonModListEditor
import com.darkestdungeon.modmanager.data.storage.dson.DsonObject
import com.darkestdungeon.modmanager.data.storage.dson.DsonTypes
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Strict parser for the Wicked Android port's binary `persist.game.json`.
 *
 * The file is a binary archive despite its `.json` extension. This parser only
 * understands the `persistent_ugcs` section (the persistent mod load order)
 * bounded by the `presented_dlc` marker.
 *
 * Validated format (verified byte-exact against real save files):
 * ```
 * persistent_ugcs\0
 * <catalog key>\0                      e.g. applied_ugcs_1_0\0
 * <record 0>
 * <record 1>
 * ...
 * presented_dlc...
 * ```
 *
 * Each record (the 4-byte length fields land on ABSOLUTE file offsets that are
 * divisible by 4 — 0-3 zero pad bytes are inserted before them as needed):
 * ```
 * <idx>\0  name\0  [pad] <len32LE> <name bytes incl. trailing \0>
 *          source\0 [pad] <len32LE> <source bytes incl. trailing \0>
 * ```
 * e.g. record 0 of a real save:
 * `30 00 | 6E 61 6D 65 00 | 00 00 00 | 12 00 00 00 | "Scourge NSFW Skin\0" | 73 6F 75 72 63 65 00 | 00 00 00 | 11 00 00 00 | "mod_local_source\0"`
 */
class SaveFormatException(message: String) : Exception(message)

data class ParsedSave(
    val bytes: ByteArray,
    val sectionStart: Int,
    val sectionEnd: Int,
    val catalogKey: String,
    val entries: List<ModEntry>,
    /** The TOP-LEVEL applied_ugcs_1_0 list (mirror of `entries` in modded saves). */
    val topLevelEntries: List<ModEntry> = emptyList(),
    /** Start offset of the top-level list's content (its catalog key), or null if absent. */
    val topLevelStart: Int? = null,
    /** End offset of the top-level list's content (first byte AFTER its records), or null. */
    val topLevelEnd: Int? = null
) {
    val loadOrder: List<String> get() = entries.map { it.name }
    val topLevelLoadOrder: List<String> get() = topLevelEntries.map { it.name }
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)
}

/** One persistent_ugcs record with its raw value bytes preserved verbatim. */
data class ModEntry(
    val key: String,
    val name: String,
    val source: String,
    val start: Int,
    val end: Int,
    val nameBytes: ByteArray,
    val sourceBytes: ByteArray
) {
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)
}

object SaveParser {

    private val persistentMarker = "persistent_ugcs".encodeToByteArray()
    private val presentedMarker = "presented_dlc".encodeToByteArray()
    private val catalogMarker = "applied_ugcs_1_0".encodeToByteArray()

    /**
     * Strictly parses the persistent_ugcs section of a save file, plus the
     * TOP-LEVEL applied_ugcs_1_0 mirror list found earlier in modded saves.
     * Throws [SaveFormatException] on ANY structural deviation.
     */
    fun parse(bytes: ByteArray): ParsedSave {
        // Parse the top-level mirror list FIRST (it lies before the section).
        val topLevel = parseTopLevelList(bytes)

        val persistentStart = bytes.lastIndexOf(persistentMarker)
        if (persistentStart < 0) {
            throw SaveFormatException("persistent_ugcs was not found")
        }

        var pos = persistentStart + persistentMarker.size
        // The marker string is null-terminated; the section content follows.
        if (pos >= bytes.size || bytes[pos] != 0.toByte()) {
            throw SaveFormatException("persistent_ugcs marker is not null-terminated")
        }
        pos++
        val sectionStart = pos

        // A fresh campaign with no persistent mods has an EMPTY section:
        // `persistent_ugcs\0` is immediately followed by `presented_dlc`.
        // This is a legal state (no mods enabled in that campaign yet).
        if (bytes.startsWith(presentedMarker, pos)) {
            return ParsedSave(
                bytes = bytes,
                sectionStart = sectionStart,
                sectionEnd = pos,
                catalogKey = "",
                entries = emptyList(),
                topLevelEntries = topLevel?.entries ?: emptyList(),
                topLevelStart = topLevel?.contentStart,
                topLevelEnd = topLevel?.contentEnd
            )
        }

        val catalogKey = readNullTerminated(bytes, pos)
        pos += catalogKey.length + 1
        if (catalogKey.isBlank()) {
            throw SaveFormatException("persistent_ugcs catalog key is empty")
        }

        val entries = mutableListOf<ModEntry>()
        while (true) {
            if (bytes.startsWith(presentedMarker, pos)) break
            if (pos >= bytes.size) {
                throw SaveFormatException("persistent_ugcs section ran off the end of the file")
            }
            val entry = parseEntry(bytes, pos, persistentStart)
            entries.add(entry)
            pos = entry.end
        }

        return ParsedSave(
            bytes = bytes,
            sectionStart = sectionStart,
            sectionEnd = pos,
            catalogKey = catalogKey,
            entries = entries,
            topLevelEntries = topLevel?.entries ?: emptyList(),
            topLevelStart = topLevel?.contentStart,
            topLevelEnd = topLevel?.contentEnd
        )
    }

    private class TopLevelList(
        val entries: List<ModEntry>,
        /** Offset of the list's catalog key (start of replaceable content). */
        val contentStart: Int,
        /** First byte AFTER the list's records (end of replaceable content). */
        val contentEnd: Int
    )

    /**
     * Finds and parses the TOP-LEVEL `applied_ugcs_1_0` list (the mirror of
     * persistent_ugcs in modded saves) BEFORE the persistent_ugcs section.
     * Returns null when the save has none (fresh campaigns).
     */
    private fun parseTopLevelList(bytes: ByteArray): TopLevelList? {
        val persistentStart = bytes.lastIndexOf(persistentMarker)
        if (persistentStart < 0) return null

        val catalogMarker = this.catalogMarker
        val searchEnd = persistentStart
        var catalogStart = -1
        var i = 0
        while (true) {
            val next = bytes.indexOfFrom(catalogMarker, i, searchEnd)
            if (next < 0) break
            // Only a real DSON key: must be preceded by \0 (start of a key) and
            // followed by \0.
            val preceded = next > 0 && bytes[next - 1] == 0.toByte()
            val followed = (next + catalogMarker.size < bytes.size) && bytes[next + catalogMarker.size] == 0.toByte()
            if (preceded && followed) { catalogStart = next }
            i = next + 1
        }
        if (catalogStart < 0) return null

        var pos = catalogStart + catalogMarker.size + 1
        val entries = mutableListOf<ModEntry>()
        while (pos < persistentStart) {
            val entry = parseEntry(bytes, pos, catalogStart)
            entries.add(entry)
            pos = entry.end
        }
        return TopLevelList(entries, catalogStart, pos)
    }

    /** Mod load-order names in file order (strict parse, throws on corrupt input). */
    fun loadOrder(bytes: ByteArray): List<String> = parse(bytes).loadOrder

    /**
     * FULL-DSON parse (strict): decodes the whole save via [DsonCodec] and
     * projects the two `applied_ugcs_1_0` mod lists onto [ParsedSave].
     *
     * Unlike [parse] (a heuristic marker-scanner that only understands the
     * persistent_ugcs region), this reads the real container metadata, so it
     * also accepts saves whose byte layout the heuristic cannot bound. The
     * offsets/byte spans come straight from the decoded tree.
     */
    fun parseViaDson(bytes: ByteArray): ParsedSave {
        val file = DsonCodec.decode(bytes)
        val lists = DsonModListEditor.extractModLists(file)
        val dataOffset = file.header.dataOffset

        fun toEntries(list: DsonObject?): List<ModEntry> {
            if (list == null) return emptyList()
            return list.children.mapIndexed { index, child ->
                if (child !is DsonObject) {
                    throw SaveFormatException(
                        "The mod list '${list.name}' holds a primitive field '${child.name}'"
                    )
                }
                val record = child
                val dsonRecord = DsonModListEditor.readRecord(record)
                val recordStart = dataOffset + record.fieldOffset
                val recordSpan = record.fieldSpan + record.children.sumOf { it.fieldSpan }
                ModEntry(
                    key = record.name,
                    name = dsonRecord.name,
                    source = dsonRecord.source,
                    start = recordStart,
                    end = recordStart + recordSpan,
                    nameBytes = dsonRecord.name.toByteArray(Charsets.UTF_8) + 0,
                    sourceBytes = dsonRecord.source.toByteArray(Charsets.UTF_8) + 0
                )
            }.also { entries ->
                // The record keys must be the canonical index strings.
                entries.forEachIndexed { i, e ->
                    if (e.key != i.toString()) {
                        throw SaveFormatException(
                            "Mod record $i of '${list.name}' has key '${e.key}' (expected '$i')"
                        )
                    }
                }
            }
        }

        val topLevel = toEntries(lists.topLevel)
        val nested = toEntries(lists.nested)
        val top = lists.topLevel
        return ParsedSave(
            bytes = bytes,
            sectionStart = if (lists.nested != null) dataOffset + lists.nested.fieldOffset else 0,
            sectionEnd = if (lists.nested != null) {
                dataOffset + lists.nested.fieldOffset + lists.nested.fieldSpan +
                    lists.nested.children.sumOf { it.fieldSpan }
            } else 0,
            catalogKey = DsonTypes.MOD_LIST_FIELD_NAME,
            entries = nested,
            topLevelEntries = topLevel,
            topLevelStart = top?.let { dataOffset + it.fieldOffset },
            topLevelEnd = top?.let {
                dataOffset + it.fieldOffset + it.fieldSpan + it.children.sumOf { c -> c.fieldSpan }
            }
        )
    }

    private fun parseEntry(bytes: ByteArray, start: Int, persistentStart: Int): ModEntry {
        var pos = start

        val key = readNullTerminated(bytes, pos)
        pos += key.length + 1
        if (key.isEmpty() || key.any { it !in '0'..'9' }) {
            throw SaveFormatException(
                "Expected numeric record key at offset $start but found '$key' " +
                    "(persistent_ugcs at $persistentStart, file size ${bytes.size} bytes)"
            )
        }

        val name = readValue(bytes, pos, "name")
        pos = name.endExclusive
        val source = readValue(bytes, pos, "source")
        pos = source.endExclusive

        return ModEntry(
            key = key,
            name = name.value,
            source = source.value,
            start = start,
            end = pos,
            nameBytes = name.raw,
            sourceBytes = source.raw
        )
    }

    private class Field(
        val value: String,
        val raw: ByteArray,
        val endExclusive: Int
    )

    /**
     * Reads `<key>\0 [pad] <len32LE> <value bytes>` with strict validation that
     * the length field lands on an ABSOLUTE file offset divisible by 4.
     * Value bytes may include a trailing `\0` (the string terminator is
     * counted in the length); interior nulls and invalid UTF-8 are rejected.
     */
    private fun readValue(bytes: ByteArray, pos: Int, expectedKey: String): Field {
        var p = pos
        val key = readNullTerminated(bytes, p)
        if (key != expectedKey) {
            throw SaveFormatException("Expected field '$expectedKey' at offset $p but found '$key'")
        }
        p += key.length + 1

        // Alignment pad: 0-3 zero bytes so that the absolute offset of the
        // length field is divisible by 4.
        while (p % 4 != 0) {
            if (p >= bytes.size || bytes[p] != 0.toByte()) {
                throw SaveFormatException("Expected alignment pad byte 0 at offset $p but found ${bytes.getOrElse(p) { -1 }}")
            }
            p++
        }

        if (p + 4 > bytes.size) {
            throw SaveFormatException("Length field at offset $p exceeds file bounds")
        }
        val length = readLe32(bytes, p)
        p += 4
        if (length <= 0) {
            throw SaveFormatException("Non-positive length $length at offset ${p - 4}")
        }
        if (p + length > bytes.size) {
            throw SaveFormatException("Length $length at offset ${p - 4} exceeds file bounds (file size ${bytes.size})")
        }
        val raw = bytes.copyOfRange(p, p + length)
        p += length

        val decoded = raw.toString(Charsets.UTF_8).trimEnd('\u0000')
        if (decoded.any { it == '\u0000' || it == '\uFFFD' }) {
            throw SaveFormatException("Field '$expectedKey' at offset $pos contains invalid UTF-8")
        }
        return Field(decoded, raw, p)
    }

    private fun readNullTerminated(bytes: ByteArray, pos: Int): String {
        var p = pos
        val sb = StringBuilder()
        while (p < bytes.size && bytes[p] != 0.toByte()) {
            sb.append(bytes[p].toInt().toChar())
            p++
        }
        if (p >= bytes.size) {
            throw SaveFormatException("Unterminated string at offset $pos (missing \\0)")
        }
        return sb.toString()
    }

    private fun readLe32(bytes: ByteArray, pos: Int): Int =
        ByteBuffer.wrap(bytes, pos, 4).order(ByteOrder.LITTLE_ENDIAN).int

    private fun ByteArray.startsWith(needle: ByteArray, offset: Int): Boolean {
        if (offset < 0 || offset + needle.size > size) return false
        return needle.indices.all { this[offset + it] == needle[it] }
    }

    private fun ByteArray.lastIndexOf(needle: ByteArray): Int {
        if (needle.isEmpty() || size < needle.size) return -1
        for (i in size - needle.size downTo 0) {
            if (needle.indices.all { this[i + it] == needle[it] }) return i
        }
        return -1
    }

    /** Finds [needle] in the receiver, searching [from]..<[to]. */
    private fun ByteArray.indexOfFrom(needle: ByteArray, from: Int, to: Int): Int {
        if (needle.isEmpty() || to - from < needle.size) return -1
        for (i in from..(to - needle.size)) {
            if (needle.indices.all { this[i + it] == needle[it] }) return i
        }
        return -1
    }
}