package com.darkestdungeon.modmanager.data.storage.dson

import com.darkestdungeon.modmanager.data.storage.SaveFormatException

/** A mod record located in one of the save's `applied_ugcs_1_0` lists. */
data class DsonModRecord(
    val name: String,
    val source: String,
)

/** The two `applied_ugcs_1_0` mod lists of a save. */
data class DsonModLists(
    /** Field of the root object; null when the save has no top-level list. */
    val topLevel: DsonObject?,
    /** Field inside `persistent_ugcs`; null when the save has no nested list. */
    val nested: DsonObject?,
    /** The `persistent_ugcs` object itself, or null when absent. */
    val persistentUgcs: DsonObject?,
)

/**
 * Location + reconstruction helpers for the save's TWO mod lists (the game
 * keeps both in sync):
 *  1. the TOP-LEVEL `applied_ugcs_1_0` field of the root object;
 *  2. the `applied_ugcs_1_0` field INSIDE `persistent_ugcs`.
 *
 * Each list object's children are record objects named by their index
 * strings ("0", "1", ...), each holding exactly two string fields,
 * `name` and `source` (source value `mod_local_source`).
 */
object DsonModListEditor {

    const val MOD_LIST_FIELD_NAME: String = DsonTypes.MOD_LIST_FIELD_NAME
    const val PERSISTENT_FIELD_NAME: String = DsonTypes.PERSISTENT_FIELD_NAME

    /**
     * Locates both mod-list objects. Null-safe: any missing object yields
     * null in its slot.
     */
    fun extractModLists(file: DsonFile): DsonModLists {
        val root = file.root
        val topLevel = root.children
            .filterIsInstance<DsonObject>()
            .firstOrNull { it.name == MOD_LIST_FIELD_NAME }
        val persistent = root.children
            .filterIsInstance<DsonObject>()
            .firstOrNull { it.name == PERSISTENT_FIELD_NAME }
        val nested = persistent?.children
            ?.filterIsInstance<DsonObject>()
            ?.firstOrNull { it.name == MOD_LIST_FIELD_NAME }
        return DsonModLists(topLevel = topLevel, nested = nested, persistentUgcs = persistent)
    }

    /**
     * Reads one list's records in order as (name, source) pairs. A missing
     * list reads as empty; malformed records throw [SaveFormatException].
     */
    fun readRecords(list: DsonObject?): List<DsonModRecord> {
        if (list == null) return emptyList()
        val records = ArrayList<DsonModRecord>(list.children.size)
        for (child in list.children) {
            if (child !is DsonObject) {
                throw SaveFormatException(
                    "The mod list '${list.name}' holds a primitive field '${child.name}'; expected a record object"
                )
            }
            records += readRecord(child)
        }
        return records
    }

    /** Reads one record object's `name` and `source` string fields. */
    fun readRecord(record: DsonObject): DsonModRecord {
        var name: String? = null
        var source: String? = null
        for (field in record.children) {
            val primitive = field as? DsonPrimitive
                ?: throw SaveFormatException(
                    "Mod record '${record.name}' holds an object field '${field.name}'; " +
                        "expected only name/source strings"
                )
            when (field.name) {
                "name" -> name = primitive.stringValue
                    ?: throw SaveFormatException("Mod record '${record.name}' has a non-string 'name' field")
                "source" -> source = primitive.stringValue
                    ?: throw SaveFormatException("Mod record '${record.name}' has a non-string 'source' field")
                else -> throw SaveFormatException(
                    "Mod record '${record.name}' holds an unexpected field '${field.name}'"
                )
            }
        }
        if (name == null) throw SaveFormatException("Mod record '${record.name}' has no 'name' field")
        if (source == null) throw SaveFormatException("Mod record '${record.name}' has no 'source' field")
        return DsonModRecord(name, source)
    }

    /**
     * Rebuilds BOTH mod lists to exactly [records] (same records, same order,
     * keys renumbered "0".."n-1") and reports the diff.
     *
     * Lists the save does not have are CREATED:
     *  - the top-level list is appended at the END of the root's children;
     *  - the nested list is appended at the END of `persistent_ugcs`'s
     *    children (creating `persistent_ugcs` itself when absent, appended
     *    at the root's end too).
     *
     * Everything else in the tree is left untouched; the codec recomputes
     * Meta1/Meta2/header metadata from the tree on encode.
     */
    fun syncModLists(
        file: DsonFile,
        records: List<DsonModRecord>,
    ): DsonModListSyncReport {
        val listsBefore = extractModLists(file)
        val before = listsBefore.readAll()
        val root = file.root

        // Top-level list.
        val top = listsBefore.topLevel ?: DsonObject(name = MOD_LIST_FIELD_NAME).also {
            root.children += it
        }
        // persistent_ugcs + nested list.
        val persistent = listsBefore.persistentUgcs ?: DsonObject(name = PERSISTENT_FIELD_NAME).also {
            root.children += it
        }
        val nested = listsBefore.nested ?: DsonObject(name = MOD_LIST_FIELD_NAME).also {
            persistent.children += it
        }

        rebuild(top, records)
        rebuild(nested, records)

        val after = extractModLists(file).readAll()
        return DsonModListSyncReport(
            added = after.filter { it !in before },
            removed = before.filter { it !in after },
            reordered = after != before,
        )
    }

    /** Reads the save's current mod records (top-level preferred, nested fallback). */
    private fun DsonModLists.readAll(): List<DsonModRecord> =
        readRecords(topLevel ?: nested)

    /**
     * Replaces [list]'s children with fresh record objects for [records],
     * keys renumbered "0".."n-1", each record object holding exactly two
     * string fields: `name` and `source`.
     */
    private fun rebuild(list: DsonObject, records: List<DsonModRecord>) {
        list.children.clear()
        records.forEachIndexed { index, record ->
            list.children += DsonObject(
                name = index.toString(),
                children = mutableListOf(
                    stringField("name", record.name),
                    stringField("source", record.source),
                ),
            )
        }
    }

    /** Builds a string field with a clean fieldInfo word. */
    private fun stringField(name: String, value: String): DsonPrimitive =
        DsonPrimitive(
            name = name,
            kind = DsonPrimitiveKind.STRING,
            valueBytes = value.toByteArray(Charsets.UTF_8) + 0,
            rawFieldInfo = null,
            stringValue = value,
        )
}

/** The membership diff of a [DsonModListEditor.syncModLists] run. */
data class DsonModListSyncReport(
    val added: List<DsonModRecord>,
    val removed: List<DsonModRecord>,
    val reordered: Boolean,
)