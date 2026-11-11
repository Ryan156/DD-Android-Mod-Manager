package com.darkestdungeon.modmanager

import com.darkestdungeon.modmanager.data.storage.ModEntry
import com.darkestdungeon.modmanager.data.storage.ParsedSave
import com.darkestdungeon.modmanager.data.storage.SaveFormatException
import com.darkestdungeon.modmanager.data.storage.SaveParser
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class SaveWriteResult(
    val bytes: ByteArray? = null,
    val error: String? = null
)

/**
 * Writer for the persistent_ugcs section of the Wicked port's binary
 * `persist.game.json`.
 *
 * Two write modes:
 *  - **reorder** (safe): a pure permutation of the save's own records. Section
 *    length is preserved, so nothing after it shifts and the container header
 *    stays valid untouched.
 *  - **fullCopy** (experimental): makes the target's persistent mod list
 *    identical to a source save's list — including records the target campaign
 *    doesn't have. This grows the file and patches the container header size
 *    fields; it must be validated against the game's loader on-device.
 */
object BinarySaveWriter {

    /** Container header offsets (validated against real save files). */
    private const val PART_TWO_OFFSET_FIELD = 0x38
    private const val PART_TWO_SIZE_FIELD = 0x3C
    private const val DEFAULT_CATALOG_KEY = "applied_ugcs_1_0"

    /**
     * Rebuilds [save] with its records laid out in [newOrder] (a permutation of
     * `save.entries`). Record value bytes are copied verbatim; only the record
     * keys (`<idx>\0`) are rewritten (0, 1, 2, ...) to match the new positions,
     * with the 4-byte alignment pads recomputed.
     *
     * Returns [SaveWriteResult.error] (nothing written) if:
     *  - [newOrder] is not a permutation of the save's entries;
     *  - the rebuilt section length differs from the original section length;
     *  - the rebuilt output does not strictly re-parse to [newOrder].
     */
    fun reorder(save: ParsedSave, newOrder: List<ModEntry>): SaveWriteResult {
        if (newOrder.size != save.entries.size ||
            newOrder.toHashSet().size != newOrder.size ||
            !newOrder.toHashSet().containsAll(save.entries)
        ) {
            val missing = save.entries.count { it !in newOrder.toHashSet() }
            val extra = newOrder.count { it !in save.entries.toHashSet() }
            return SaveWriteResult(
                error = "The new load order is not a permutation of the save's mod records " +
                    "(missing $missing, unrecognized $extra)."
            )
        }

        val rebuilt = rebuildSection(save, newOrder)
        val originalLength = save.sectionEnd - save.sectionStart

        if (rebuilt.size != originalLength) {
            return SaveWriteResult(
                error = "Reordering would change the persistent_ugcs section size " +
                    "(${rebuilt.size} vs $originalLength bytes); refusing to write."
            )
        }

        // Strictly re-parse the rebuilt output and verify it reproduces newOrder.
        return try {
            val output = save.bytes.copyOf()
            rebuilt.copyInto(output, save.sectionStart)
            val parsedOrder = SaveParser.parse(output).loadOrder
            if (parsedOrder != newOrder.map { it.name }) {
                return SaveWriteResult(
                    error = "Verification failed: re-parsed order does not match the intended order."
                )
            }
            SaveWriteResult(bytes = output)
        } catch (e: SaveFormatException) {
            SaveWriteResult(error = "Verification failed: rebuilt section did not re-parse cleanly: ${e.message}")
        }
    }

    /**
     * MEMBERSHIP SYNC (experimental, both-lists): rewrites BOTH mod lists —
     * the top-level `applied_ugcs_1_0` and the nested `persistent_ugcs`
     * mirror — to exactly [newOrder] (the app's current active order).
     * Mods absent from [newOrder] are REMOVED from both lists; both lists
     * are kept identical in order and membership, matching how the game's
     * own mod menu writes a save after toggling.
     *
     * Container-header handling (validated on real files):
     *  - 0x38 = offset of the save's second part (the byte right after the
     *    top-level mod list region) — RECOMPUTED when the top list's length
     *    changes;
     *  - 0x3C = size of the second part — recomputed as (fileSize - second-part
     *    offset).
     *
     * Gates (any failure = nothing written):
     *  1. Both lists must already exist and agree in membership (game-written
     *     state) — else the save comes from a nonstandard writer, refuse;
     *  2. Every [newOrder] name must exist in the save (no synthesis);
     *  3. Output must strictly re-parse with BOTH lists exactly [newOrder];
     *  4. Everything before the top-level list must stay byte-identical.
     */
    fun syncLists(save: ParsedSave, newOrder: List<String>): SaveWriteResult {
        val topStart = save.topLevelStart
        val topEnd = save.topLevelEnd
        if (save.topLevelEntries.isEmpty() || topStart == null || topEnd == null) {
            return SaveWriteResult(
                error = "This save has no top-level mod list to sync (fresh campaign)."
            )
        }

        // Gate 1: both lists must already agree in membership (same set).
        val nestedNames = save.entries.map { it.name }.toSet()
        val topNames = save.topLevelEntries.map { it.name }.toSet()
        if (nestedNames != topNames) {
            return SaveWriteResult(
                error = "The save's two mod lists disagree (nested ${nestedNames.size} vs " +
                    "top-level ${topNames.size}); refusing to write."
            )
        }

        // Map the new order onto the save's own records (by name).
        val nestedByName = save.entries.associateBy { it.name }
        val topByName = save.topLevelEntries.associateBy { it.name }
        val newNested = mutableListOf<ModEntry>()
        val newTop = mutableListOf<ModEntry>()
        val seen = HashSet<String>()
        for (name in newOrder) {
            if (!seen.add(name)) continue
            val nested = nestedByName[name]
                ?: return SaveWriteResult(
                    error = "Mod not found in this save: $name"
                )
            val top = topByName[name]
                ?: return SaveWriteResult(
                    error = "Mod missing from the save's top-level list: $name"
                )
            newNested += nested
            newTop += top
        }

        // Rebuild both list regions. The top list keeps its own catalog key;
        // the nested list keeps its catalog key. Both regions' bytes change
        // length, so everything after each region shifts — assemble fresh.
        val rebuiltTop = rebuildSection(
            save.copy(sectionStart = topStart, catalogKey = DEFAULT_CATALOG_KEY),
            newTop
        )
        val rebuiltNested = rebuildSection(
            save.copy(sectionStart = save.sectionStart, catalogKey = save.catalogKey.ifEmpty { DEFAULT_CATALOG_KEY }),
            newNested
        )

        // Assemble: header (unchanged) + pre-top prefix + new top + middle
        // (unchanged bytes between the two regions) + new nested + suffix.
        val output = ByteArray(
            topStart + rebuiltTop.size +
                (save.sectionStart - topEnd) +
                rebuiltNested.size +
                (save.bytes.size - save.sectionEnd)
        )
        var pos = 0
        fun put(bytes: ByteArray) {
            bytes.copyInto(output, pos)
            pos += bytes.size
        }
        put(save.bytes.copyOfRange(0, topStart))           // header + everything before top list
        put(rebuiltTop)                                     // new top-level list
        put(save.bytes.copyOfRange(topEnd, save.sectionStart)) // unchanged middle bytes
        put(rebuiltNested)                                  // new nested list
        put(save.bytes.copyOfRange(save.sectionEnd, save.bytes.size)) // suffix (presented_dlc onward)

        // Patch the container header: second-part offset (0x38) and size (0x3C).
        // 0x38 = start of the save's second part = the offset right after the
        // (now rebuilt) top-level list region's ORIGINAL start is NOT it —
        // validated meaning: 0x38 held topStart-of-second-part = 12468/1164 in
        // the real files, i.e. the byte offset where the second part begins.
        // After our rewrite that offset shifts by the top list's length delta.
        if (PART_TWO_OFFSET_FIELD + 4 <= output.size) {
            val oldPartTwoOffset = readLe32(save.bytes, PART_TWO_OFFSET_FIELD)
            val oldPartTwoSize = readLe32(save.bytes, PART_TWO_SIZE_FIELD)
            if (oldPartTwoOffset == topEnd && oldPartTwoOffset + oldPartTwoSize == save.bytes.size) {
                val topDelta = rebuiltTop.size - (topEnd - topStart)
                writeLe32(output, PART_TWO_OFFSET_FIELD, topEnd + topDelta)
                writeLe32(output, PART_TWO_SIZE_FIELD, output.size - (topEnd + topDelta))
            }
            // If the real-file header variant didn't match our model exactly,
            // leave both fields untouched rather than write a guess.
        }

        // Gate 3: strict re-parse with BOTH lists equal to newOrder.
        return try {
            val reparsed = SaveParser.parse(output)
            if (reparsed.loadOrder != newOrder.distinct() ||
                reparsed.topLevelLoadOrder != newOrder.distinct()
            ) {
                return SaveWriteResult(
                    error = "Verification failed: rebuilt lists do not match the intended order."
                )
            }
            // Gate 4: everything before the top-level list must be byte-identical.
            if (!output.copyOfRange(0, topStart)
                .contentEquals(save.bytes.copyOfRange(0, topStart))
            ) {
                return SaveWriteResult(error = "Verification failed: header changed unexpectedly.")
            }
            SaveWriteResult(bytes = output)
        } catch (e: SaveFormatException) {
            SaveWriteResult(
                error = "Verification failed: rebuilt save did not re-parse cleanly: ${e.message}"
            )
        }
    }

    /**
     * TANDEM reorder (the safe write): reorders the nested persistent_ugcs
     * list AND the top-level applied_ugcs_1_0 mirror list in lockstep, so the
     * game's invariant — both lists hold the same entries in the same order —
     * is preserved exactly as the game itself writes it.
     *
     * Each list is a pure permutation of its own records: both sections keep
     * their exact byte lengths (nothing grows, nothing shifts, no header
     * patching), so this is as safe as [reorder].
     *
     * Gates (any failure = nothing written):
     *  1. [newOrder] must be a permutation of `save.entries`;
     *  2. both rebuilt sections must keep their exact original lengths;
     *  3. the output must strictly re-parse with BOTH lists equal to newOrder's names;
     *  4. when both lists exist, they must already be in the same order (sanity).
     */
    fun tandemReorder(save: ParsedSave, newOrder: List<ModEntry>): SaveWriteResult {
        // Gate 1: permutation of the nested list.
        if (newOrder.size != save.entries.size ||
            newOrder.toHashSet().size != newOrder.size ||
            !newOrder.toHashSet().containsAll(save.entries)
        ) {
            return SaveWriteResult(
                error = "The new load order is not a permutation of the save's mod records."
            )
        }

        // Map the new order onto the top-level mirror entries by name.
        val topByEntryKey = save.topLevelEntries.associateBy { it.name }
        val newTopOrder = newOrder.mapNotNull { topByEntryKey[it.name] }
        val hasTop = save.topLevelEntries.isNotEmpty()
        if (hasTop && newTopOrder.size != save.topLevelEntries.size) {
            return SaveWriteResult(
                error = "The top-level applied_ugcs_1_0 mirror list does not match the " +
                    "persistent list; refusing to write."
            )
        }
        // Gate 4: sanity — lists must already agree in order.
        if (hasTop && save.topLevelLoadOrder != save.loadOrder) {
            return SaveWriteResult(
                error = "The save's persistent and top-level mod lists disagree in order; refusing to write."
            )
        }

        // Gate 2: both rebuilt sections must keep their exact lengths.
        val rebuiltNested = rebuildSection(save, newOrder)
        val nestedLen = save.sectionEnd - save.sectionStart
        if (rebuiltNested.size != nestedLen) {
            return SaveWriteResult(
                error = "Reordering would change the persistent_ugcs section size " +
                    "(${rebuiltNested.size} vs $nestedLen bytes); refusing to write."
            )
        }
        val topStart = save.topLevelStart
        val topEnd = save.topLevelEnd
        if (hasTop && topStart != null && topEnd != null) {
            val topLen = topEnd - topStart
            val rebuiltTop = rebuildSection(
                save.copy(
                    sectionStart = topStart,
                    catalogKey = DEFAULT_CATALOG_KEY
                ),
                newTopOrder
            )
            if (rebuiltTop.size != topLen) {
                return SaveWriteResult(
                    error = "Reordering would change the top-level applied_ugcs_1_0 size " +
                        "(${rebuiltTop.size} vs $topLen bytes); refusing to write."
                )
            }
        }

        // Gate 3: strict re-parse of the full output.
        return try {
            val output = save.bytes.copyOf()
            rebuiltNested.copyInto(output, save.sectionStart)
            if (hasTop && topStart != null && topEnd != null) {
                val rebuiltTop = rebuildSection(
                    save.copy(
                        sectionStart = topStart,
                        catalogKey = DEFAULT_CATALOG_KEY
                    ),
                    newTopOrder
                )
                rebuiltTop.copyInto(output, topStart)
            }
            val reparsed = SaveParser.parse(output)
            if (reparsed.loadOrder != newOrder.map { it.name }) {
                return SaveWriteResult(
                    error = "Verification failed: re-parsed order does not match the intended order."
                )
            }
            if (hasTop && reparsed.topLevelLoadOrder != reparsed.loadOrder) {
                return SaveWriteResult(
                    error = "Verification failed: the two mod lists disagree after the write."
                )
            }
            SaveWriteResult(bytes = output)
        } catch (e: SaveFormatException) {
            SaveWriteResult(
                error = "Verification failed: rebuilt save did not re-parse cleanly: ${e.message}"
            )
        }
    }

    /**
     * Rebuilds the section bytes: catalog key + records in [newOrder], keys
     * renumbered 0..n-1, alignment pads recomputed, values verbatim.
     */
    internal fun rebuildSection(save: ParsedSave, newOrder: List<ModEntry>): ByteArray {
        val out = ByteArrayOutputStream()

        // Catalog key (e.g. "applied_ugcs_1_0\0")
        save.catalogKey.encodeToByteArray().forEach { out.write(it.toInt()) }
        out.write(0)

        newOrder.forEachIndexed { index, entry ->
            index.toString().encodeToByteArray().forEach { out.write(it.toInt()) }
            out.write(0)

            for (value in listOf(entry.nameBytes, entry.sourceBytes)) {
                val key = if (value === entry.nameBytes) "name" else "source"
                key.encodeToByteArray().forEach { out.write(it.toInt()) }
                out.write(0)

                // Absolute file offset of the section = save.sectionStart; track
                // the length field's absolute position to recompute alignment.
                var lengthFieldAbsoluteOffset = save.sectionStart + out.size()
                while (lengthFieldAbsoluteOffset % 4 != 0) {
                    out.write(0)
                    lengthFieldAbsoluteOffset++
                }
                out.write(le32(value.size))
                value.forEach { out.write(it.toInt()) }
            }
        }
        return out.toByteArray()
    }

    private fun readLe32(bytes: ByteArray, offset: Int): Int =
        ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int

    private fun writeLe32(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value and 0xFF).toByte()
        bytes[offset + 1] = ((value shr 8) and 0xFF).toByte()
        bytes[offset + 2] = ((value shr 16) and 0xFF).toByte()
        bytes[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }

    private fun le32(v: Int): ByteArray =
        byteArrayOf(
            (v and 0xFF).toByte(),
            ((v shr 8) and 0xFF).toByte(),
            ((v shr 16) and 0xFF).toByte(),
            ((v shr 24) and 0xFF).toByte()
        )
}