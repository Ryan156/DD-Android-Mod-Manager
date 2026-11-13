package com.darkestdungeon.modmanager.data.repository

import android.content.Context
import android.net.Uri
import com.darkestdungeon.modmanager.LoadOrderResult
import com.darkestdungeon.modmanager.ModManagerRepository
import com.darkestdungeon.modmanager.TransferPlan
import com.darkestdungeon.modmanager.data.model.Mod
import com.darkestdungeon.modmanager.data.model.ModCategory
import com.darkestdungeon.modmanager.data.storage.ModStorageManager
import com.darkestdungeon.modmanager.data.storage.ParsedSave
import com.darkestdungeon.modmanager.data.storage.SaveFormatException
import com.darkestdungeon.modmanager.data.storage.dson.DsonCodec
import com.darkestdungeon.modmanager.data.storage.dson.DsonModListEditor
import com.darkestdungeon.modmanager.data.storage.dson.DsonModRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

private val CATEGORY_SEPARATOR_REGEX = Regex("[^\\p{L}\\p{N}]+")
private val WHITESPACE_REGEX = Regex("\\s+")
private val CATEGORY_KEYWORDS = mapOf(
    "ui" to listOf("ui", "user interface", "interface", "quality of life", "qol", "hud"),
    "districts" to listOf("district", "districts", "town district"),
    "dungeons" to listOf("dungeon", "dungeons"),
    "quirks" to listOf("quirk", "quirks"),
    "trinkets" to listOf("trinket", "trinkets"),
    "enemies" to listOf("enemy", "enemies", "monster", "monsters", "boss", "bosses"),
    "class_patch" to listOf("class patch", "classpatch", "patch class", "patch"),
    "class" to listOf("new class", "hero class", "class", "hero", "character"),
    "skins" to listOf("skin", "skins", "cosmetic", "cosmetics", "outfit")
)

class ModRepository(private val context: Context) {

    private val storageManager = ModStorageManager(context)
    private val prefs = context.getSharedPreferences("dd_mod_manager_prefs", Context.MODE_PRIVATE)

    companion object {
        const val PREF_TARGET_PATH = "target_mod_path"
        const val PREF_TARGET_URI = "target_mod_uri"
        const val DEFAULT_MOD_DIRECTORY = "/storage/emulated/0/Android/Media/com.darkestdungeon/mods"
        const val PREF_SAVE_URI = "game_save_uri"
        const val PREF_SAVE_FILE_URI = "game_save_file_uri"
        const val PREF_SOURCE_SAVE_FILE_URI = "source_game_save_file_uri"
        const val DEFAULT_SAVE_FILE = "/storage/emulated/0/Android/Data/com.darkestdungeon/files/persist.game.json"
        const val PREF_APP_LOAD_ORDER = "app_load_order"
        const val PREF_FOLDER_ORDER = "folder_reserve_order"
        const val PREF_CATEGORIES = "mod_categories"
        const val PREF_SELECTED_CATEGORY = "selected_category"
        const val PREF_MOD_CATEGORY_ASSIGNMENTS = "mod_category_assignments"
        private const val PREF_CATEGORY_CLASSIFIER_VERSION = "category_classifier_version"
        private const val CATEGORY_CLASSIFIER_VERSION = 6
        private const val SEP = "\u0000"

        fun getDefaultModDirectory(context: Context): String {
            val wickedMods = File(DEFAULT_MOD_DIRECTORY)
            if (wickedMods.exists()) return wickedMods.absolutePath
            return DEFAULT_MOD_DIRECTORY
        }
    }

    suspend fun getActivePath(): String = withContext(Dispatchers.IO) {
        prefs.getString(PREF_TARGET_PATH, null) ?: getDefaultModDirectory(context)
    }

    suspend fun getActiveUriStr(): String? = withContext(Dispatchers.IO) {
        prefs.getString(PREF_TARGET_URI, null)
    }

    suspend fun saveActivePath(path: String, uriStr: String? = null) = withContext(Dispatchers.IO) {
        prefs.edit()
            .putString(PREF_TARGET_PATH, path)
            .putString(PREF_TARGET_URI, uriStr)
            .apply()
    }

    suspend fun getActiveSaveUri(): String? = withContext(Dispatchers.IO) {
        prefs.getString(PREF_SAVE_URI, null)
    }

    suspend fun getActiveSaveFileUri(): String? = withContext(Dispatchers.IO) {
        prefs.getString(PREF_SAVE_FILE_URI, null)
    }

    suspend fun getSourceSaveFileUri(): String? = withContext(Dispatchers.IO) {
        prefs.getString(PREF_SOURCE_SAVE_FILE_URI, null)
    }

    suspend fun saveActiveSaveUri(uriStr: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString(PREF_SAVE_URI, uriStr).apply()
    }

    suspend fun saveActiveSaveFileUri(uriStr: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString(PREF_SAVE_FILE_URI, uriStr).apply()
    }

    suspend fun saveSourceSaveFileUri(uriStr: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString(PREF_SOURCE_SAVE_FILE_URI, uriStr).apply()
    }

    /**
     * The app's OWN load order (source of truth): active mod titles/names in
     * the current order, persisted so it survives restarts. Initialized from
     * the imported source save; updated by toggles/swaps; only replaced by a
     * new source import.
     */
    suspend fun getAppLoadOrder(): List<String> = withContext(Dispatchers.IO) {
        prefs.getString(PREF_APP_LOAD_ORDER, null)
            ?.split(SEP)
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
    }

    suspend fun saveAppLoadOrder(order: List<String>) = withContext(Dispatchers.IO) {
        prefs.edit().putString(PREF_APP_LOAD_ORDER, order.joinToString(SEP)).apply()
    }

    suspend fun hasAppLoadOrder(): Boolean = withContext(Dispatchers.IO) {
        prefs.contains(PREF_APP_LOAD_ORDER)
    }

    /** Order staged in the Mods Folder tab; it never writes the game save by itself. */
    suspend fun getFolderOrder(): List<String> = withContext(Dispatchers.IO) {
        prefs.getString(PREF_FOLDER_ORDER, null)
            ?.split(SEP)
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
    }

    suspend fun saveFolderOrder(order: List<String>) = withContext(Dispatchers.IO) {
        prefs.edit().putString(PREF_FOLDER_ORDER, order.joinToString(SEP)).apply()
    }

    suspend fun getCategories(): List<ModCategory> = withContext(Dispatchers.IO) {
        val stored = prefs.getString(PREF_CATEGORIES, null) ?: return@withContext ModCategory.defaults()
        runCatching {
            val json = JSONArray(stored)
            val builtInsById = ModCategory.BUILT_IN.associateBy { it.id }
            val savedBuiltIns = buildMap {
                for (index in 0 until json.length()) {
                    val item = json.optJSONObject(index) ?: continue
                    val id = item.optString("id").trim()
                    if (id in builtInsById) put(id, item)
                }
            }
            val builtIns = builtInsById.mapValues { (id, category) ->
                category.copy(
                    colorHex = savedBuiltIns[id]?.optString("colorHex")
                        ?.takeIf { it.isNotBlank() }
                        ?: category.colorHex
                )
            }
            val categories = buildList {
                val seen = mutableSetOf<String>()
                for (index in 0 until json.length()) {
                    val item = json.optJSONObject(index) ?: continue
                    val id = item.optString("id").trim()
                    if (id.isEmpty() || !seen.add(id)) continue
                    val builtIn = builtIns[id]
                    if (builtIn != null) {
                        add(builtIn)
                    } else if (!item.optBoolean("builtIn", false)) {
                        val name = item.optString("name").trim()
                        if (name.isNotEmpty()) {
                            add(
                                ModCategory(
                                    id = id,
                                    name = name,
                                    colorHex = item.optString("colorHex", ModCategory.DEFAULT_COLOR_HEX),
                                    builtIn = false
                                )
                            )
                        }
                    }
                }
                ModCategory.BUILT_IN.forEach { category ->
                    if (seen.add(category.id)) add(builtIns.getValue(category.id))
                }
            }
            categories
        }.getOrElse { ModCategory.defaults() }
    }

    suspend fun saveCategories(categories: List<ModCategory>) = withContext(Dispatchers.IO) {
        val json = JSONArray()
        categories.forEach { category ->
            json.put(
                JSONObject()
                    .put("id", category.id)
                    .put("name", category.name)
                    .put("colorHex", category.colorHex)
                    .put("builtIn", category.builtIn)
            )
        }
        prefs.edit().putString(PREF_CATEGORIES, json.toString()).apply()
    }

    suspend fun getSelectedCategoryId(): String? = withContext(Dispatchers.IO) {
        prefs.getString(PREF_SELECTED_CATEGORY, ModCategory.BUILT_IN.first().id)
    }

    suspend fun saveSelectedCategoryId(categoryId: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString(PREF_SELECTED_CATEGORY, categoryId).apply()
    }

    suspend fun readGameLoadOrder(uriStr: String): LoadOrderResult =
        ModManagerRepository(context).readLoadOrder(Uri.parse(uriStr))

    suspend fun readGameLoadOrderFile(uriStr: String): LoadOrderResult =
        ModManagerRepository(context).readLoadOrderFile(Uri.parse(uriStr))

        /**
     * Parses a save (tree or file URI) strictly. Returns the [ParsedSave] or a
     * user-readable error.
     */
    suspend fun parseSave(uriStr: String): Pair<ParsedSave?, String?> =
        ModManagerRepository(context).parseSave(Uri.parse(uriStr))

    /** Reads the raw save bytes from a file or tree URI, or null with a reason. */
    private suspend fun readSaveBytes(repo: ModManagerRepository, uriStr: String): Pair<ByteArray?, String?> =
        withContext(Dispatchers.IO) {
            val (bytes, error) = repo.readSaveBytes(Uri.parse(uriStr))
            if (bytes == null) {
                null to (error ?: "The save file could not be read")
            } else {
                bytes to null
            }
        }
    /**
     * The COPY feature: makes the target save's TWO mod lists identical to the
     * SOURCE save's persistent mod list — including records the target campaign
     * does not have.
     *
     * This is now a REAL membership write: the source's records (verbatim
     * name/source strings — nothing synthesized) are written into the target
     * via [DsonModListEditor.syncModLists], and [DsonCodec] rebuilds ALL
     * container metadata (Meta1/Meta2/header), so growing the target's lists
     * is safe — the container is rebuilt from the tree, not patched.
     */
    suspend fun transferLoadOrder(sourceUriStr: String, targetUriStr: String): TransferOutcome {
        if (sourceUriStr.isBlank()) {
            return TransferOutcome.Error("SOURCE: import a source persist.game.json first")
        }
        if (sourceUriStr == targetUriStr) {
            return TransferOutcome.Error("SOURCE and TARGET refer to the same save file")
        }
        val repo = ModManagerRepository(context)

        val (sourceBytes, sourceError) = readSaveBytes(repo, sourceUriStr)
        if (sourceBytes == null) {
            return TransferOutcome.Error("SOURCE: " + (sourceError ?: "The source save could not be read"))
        }
        val (targetBytes, targetError) = readSaveBytes(repo, targetUriStr)
        if (targetBytes == null) {
            return TransferOutcome.Error("TARGET: " + (targetError ?: "The target save could not be read"))
        }

        // Decode both saves with the real codec.
        val sourceDecoded = try {
            DsonCodec.decode(sourceBytes)
        } catch (e: SaveFormatException) {
            return TransferOutcome.Error("SOURCE: " + (e.message ?: "The source save could not be decoded"))
        }
        val targetDecoded = try {
            DsonCodec.decode(targetBytes)
        } catch (e: SaveFormatException) {
            return TransferOutcome.Error("TARGET: " + (e.message ?: "The target save could not be decoded"))
        }

        // The source's own records, VERBATIM (name + source strings).
        val sourceLists = DsonModListEditor.extractModLists(sourceDecoded)
        val newRecords = DsonModListEditor.readRecords(sourceLists.topLevel ?: sourceLists.nested)
        if (newRecords.isEmpty()) {
            return TransferOutcome.Refused(
                "The source save has an empty persistent mod list (no mods enabled in that campaign)."
            )
        }

        val targetBefore = DsonModListEditor.extractModLists(targetDecoded)
            .let { DsonModListEditor.readRecords(it.topLevel ?: it.nested) }

        DsonModListEditor.syncModLists(targetDecoded, newRecords)

        // Encode; verify the rebuilt container strictly re-decodes to the same
        // records before anything is written.
        val rebuilt = try {
            DsonCodec.encode(targetDecoded)
        } catch (e: SaveFormatException) {
            return TransferOutcome.Refused("The target save could not be rebuilt: ${e.message}")
        }
        val redecoded = try {
            DsonCodec.decode(rebuilt)
        } catch (e: SaveFormatException) {
            return TransferOutcome.Refused("Verification failed: the rebuilt save did not re-decode: ${e.message}")
        }
        val verifyLists = DsonModListEditor.extractModLists(redecoded)
        val verifyRecords = DsonModListEditor.readRecords(verifyLists.topLevel ?: verifyLists.nested)
        if (verifyRecords != newRecords) {
            return TransferOutcome.Refused("Verification failed: the rebuilt mod lists do not match the source order.")
        }

        // Same write path as before: .bak backup + write + read-back verify.
        val writeError = repo.writeSaveBytes(Uri.parse(targetUriStr), rebuilt)
        if (writeError != null) {
            return TransferOutcome.Error(writeError)
        }

        val targetNames = targetBefore.map { it.name }.toSet()
        val newNames = newRecords.map { it.name }.toSet()
        val added = newRecords.filter { it.name !in targetNames }
        val removed = targetBefore.filter { it.name !in newNames }
        return TransferOutcome.Success(
            TransferPlan(
                sourceLoadOrder = newRecords.map { it.name },
                targetLoadOrder = targetBefore.map { it.name },
                orderedTargetEntries = emptyList(),
                matched = newRecords.map { it.name }.filter { it in targetNames },
                appendedNotInSource = emptyList(),
                ignoredNotInstalled = emptyList(),
                canTransfer = true,
                reason = "Copied ${newRecords.size} mod(s) from the source save: " +
                    "${added.size} added, ${removed.size} removed, " +
                    "${newRecords.size - added.size} already present (kept/reordered)."
            )
        )
    }

    /**
     * Applies the app's OWN load order ([orderedNames]) to a save file (the
     * source save) via the REAL codec: decode → resolve the app order against
     * the save's own records → sync BOTH mod lists → encode (rebuilding all
     * container metadata) → the standard write path (.bak + read-back verify).
     *
     *  - names NOT present in the save are SKIPPED and reported
     *    (`ignoredNotInstalled` — nothing is ever synthesized);
     *  - save records NOT in the app order are REMOVED
     *    (`appendedNotInSource`, matching the game's own toggle-and-save);
     *  - both lists end up exactly the resolved records in app order.
     */
    suspend fun applyLoadOrderToSave(
        saveUriStr: String,
        orderedNames: List<String>,
        installedMods: List<Mod> = emptyList(),
    ): TransferOutcome {
        val repo = ModManagerRepository(context)
        val (saveBytes, readError) = readSaveBytes(repo, saveUriStr)
        if (saveBytes == null) {
            return TransferOutcome.Error("SOURCE: " + (readError ?: "The save could not be read"))
        }
        val decoded = try {
            DsonCodec.decode(saveBytes)
        } catch (e: SaveFormatException) {
            return TransferOutcome.Error("SOURCE: " + (e.message ?: "The save could not be decoded"))
        } catch (e: Exception) {
            return TransferOutcome.Error("SOURCE: the save could not be decoded: ${e.javaClass.simpleName}: ${e.message}")
        }

        val lists = DsonModListEditor.extractModLists(decoded)
        val saveRecords = try {
            DsonModListEditor.readRecords(lists.topLevel ?: lists.nested)
        } catch (e: SaveFormatException) {
            return TransferOutcome.Error("SOURCE: " + (e.message ?: "The save's mod lists are malformed"))
        } catch (e: Exception) {
            return TransferOutcome.Error("SOURCE: the save's mod lists are malformed: ${e.javaClass.simpleName}: ${e.message}")
        }
        // Resolve the app order against the save's own records first. Mods
        // that are present in the selected mods folder but not yet recorded in
        // this campaign are synthesized as local records; this is required for
        // enabling a mod in a fresh campaign. Unknown names are still skipped.
        val normalized = { s: String -> s.trim().lowercase().replace(" ", "") }
        // The ViewModel normally supplies its already-scanned mod list.
        // Scanning the SAF tree here would re-read every metadata file and
        // preview entry on every toggle, which is very slow on Android 16.
        val availableMods = installedMods.ifEmpty {
            runCatching { scanMods() }.getOrDefault(emptyList())
        }
        fun matchesInstalled(name: String, mod: Mod): Boolean {
            val requested = normalized(name)
            return listOf(mod.title, mod.folderName, mod.id).any { normalized(it) == requested }
        }
        val used = HashSet<Int>()
        val ordered = mutableListOf<DsonModRecord>()
        val matched = mutableListOf<String>()
        val appended = mutableListOf<String>()
        val ignoredNames = mutableListOf<String>()
        for (name in orderedNames.distinct()) {
            val idx = saveRecords.indices.firstOrNull { i ->
                (saveRecords[i].name == name || normalized(saveRecords[i].name) == normalized(name)) && used.add(i)
            } ?: -1
            if (idx >= 0) {
                ordered += saveRecords[idx]
                matched += name
            } else {
                val installed = availableMods.firstOrNull { matchesInstalled(name, it) }
                if (installed != null) {
                    ordered += DsonModRecord(
                        name = installed.title,
                        source = com.darkestdungeon.modmanager.data.storage.dson.DsonTypes.MOD_LOCAL_SOURCE
                    )
                    appended += installed.title
                    matched += installed.title
                } else {
                    ignoredNames += name
                }
            }
        }
        val removed = saveRecords.filterIndexed { i, _ -> !used.contains(i) }

        // Rebuild BOTH lists to exactly the resolved records. Existing records
        // retain their verbatim source strings; newly enabled folder mods use
        // the game's local-mod source identifier.
        try {
            DsonModListEditor.syncModLists(decoded, ordered)
        } catch (e: Exception) {
            return TransferOutcome.Refused("The save could not be rebuilt: ${e.javaClass.simpleName}: ${e.message}")
        }

        val rebuilt = try {
            DsonCodec.encode(decoded)
        } catch (e: SaveFormatException) {
            return TransferOutcome.Refused("The save could not be rebuilt: ${e.message}")
        } catch (e: Exception) {
            return TransferOutcome.Refused("The save could not be encoded: ${e.javaClass.simpleName}: ${e.message}")
        }
        // Verify the rebuilt container strictly re-decodes to the same records.
        val redecodedLists = try {
            DsonModListEditor.extractModLists(DsonCodec.decode(rebuilt))
        } catch (e: SaveFormatException) {
            return TransferOutcome.Refused("Verification failed: the rebuilt save did not re-decode: ${e.message}")
        } catch (e: Exception) {
            return TransferOutcome.Refused("Verification failed: the rebuilt save did not re-decode: ${e.javaClass.simpleName}: ${e.message}")
        }
        val redecoded = try {
            DsonModListEditor.readRecords(redecodedLists.topLevel ?: redecodedLists.nested)
        } catch (e: Exception) {
            return TransferOutcome.Refused("Verification failed: the rebuilt save's mod lists are malformed: ${e.message}")
        }
        if (redecoded != ordered) {
            return TransferOutcome.Refused("Verification failed: the rebuilt mod lists do not match the intended order.")
        }

        val writeError = repo.writeSaveBytes(Uri.parse(saveUriStr), rebuilt)
        if (writeError != null) {
            return TransferOutcome.Error(writeError)
        }
        return TransferOutcome.Success(
            TransferPlan(
                sourceLoadOrder = orderedNames,
                targetLoadOrder = saveRecords.map { it.name },
                orderedTargetEntries = emptyList(),
                matched = matched,
                appendedNotInSource = appended,
                ignoredNotInstalled = ignoredNames,
                canTransfer = true,
                reason = "${matched.size} matched, ${appended.size} newly added, " +
                    "${removed.size} removed, ${ignoredNames.size} unknown."
            )
        )
    }

    sealed interface TransferOutcome {
        data class Success(val plan: TransferPlan) : TransferOutcome
        data class Refused(val reason: String) : TransferOutcome
        data class Error(val reason: String) : TransferOutcome
    }

    suspend fun tryReadDefaultGameLoadOrder(): LoadOrderResult? = withContext(Dispatchers.IO) {
        val file = File(DEFAULT_SAVE_FILE)
        if (!file.isFile) return@withContext null
        runCatching { LoadOrderResult(com.darkestdungeon.modmanager.data.storage.SaveParser.loadOrder(file.readBytes())) }
            .getOrElse { e -> LoadOrderResult(emptyList(), e.message ?: "The save file could not be parsed") }
    }

    /**
     * Applies saved folder-tab category assignments. Mods without an existing
     * assignment are classified once from their metadata keywords and then
     * persisted, so later rescans never overwrite a user's manual choice.
     */
    suspend fun applyStoredModCategories(
        mods: List<Mod>,
        categories: List<ModCategory>
    ): List<Mod> = withContext(Dispatchers.IO) {
        val stored = readModCategoryAssignments().toMutableMap()
        var assignmentsChanged = false
        val classifierVersion = prefs.getInt(PREF_CATEGORY_CLASSIFIER_VERSION, 0)
        val migrateClassifierAssignments = classifierVersion < CATEGORY_CLASSIFIER_VERSION
        if (migrateClassifierAssignments) {
            // Clear old unassigned results. Strong XML title/tag matches are
            // corrected below; ordinary non-empty assignments remain intact.
            assignmentsChanged = stored.entries.removeIf { it.value.isBlank() }
            prefs.edit().putInt(PREF_CATEGORY_CLASSIFIER_VERSION, CATEGORY_CLASSIFIER_VERSION).apply()
        }
        val validCategoryIds = categories.map { it.id }.toSet()
        val updated = mods.map { mod ->
            val hasStoredAssignment = stored.containsKey(mod.id)
            val saved = stored[mod.id]
            val savedCategoryId = saved?.takeIf { it.isNotBlank() && it in validCategoryIds }
            val migratedCategoryId = if (migrateClassifierAssignments && savedCategoryId != null) {
                strongAutoCategoryId(mod, categories) ?: savedCategoryId
            } else {
                savedCategoryId
            }
            val categoryId = if (hasStoredAssignment) {
                migratedCategoryId
            } else {
                autoCategoryId(mod, categories)
            }
            if (!hasStoredAssignment) {
                stored[mod.id] = categoryId.orEmpty()
                assignmentsChanged = true
            } else if (migrateClassifierAssignments && stored[mod.id] != categoryId.orEmpty()) {
                stored[mod.id] = categoryId.orEmpty()
                assignmentsChanged = true
            }
            mod.copy(categoryId = categoryId)
        }
        if (assignmentsChanged) saveModCategoryAssignments(stored)
        updated
    }

    suspend fun saveModCategoryAssignments(assignments: Map<String, String?>) = withContext(Dispatchers.IO) {
        val merged = readModCategoryAssignments().toMutableMap()
        assignments.forEach { (modId, categoryId) -> merged[modId] = categoryId.orEmpty() }
        val json = JSONObject()
        merged.forEach { (modId, categoryId) -> json.put(modId, categoryId) }
        prefs.edit().putString(PREF_MOD_CATEGORY_ASSIGNMENTS, json.toString()).apply()
    }

    data class AutoCategoryResult(
        val mods: List<Mod>,
        val assignedCount: Int
    )

    /** Assigns only currently unassigned mods; manual assignments are preserved. */
    suspend fun autoCategorizeUnassignedMods(
        mods: List<Mod>,
        categories: List<ModCategory>
    ): AutoCategoryResult = withContext(Dispatchers.IO) {
        val assignments = readModCategoryAssignments().toMutableMap()
        var assignedCount = 0
        val updated = mods.map { mod ->
            if (mod.categoryId != null) {
                mod
            } else {
                val categoryId = autoCategoryId(mod, categories)
                if (categoryId != null) {
                    assignments[mod.id] = categoryId
                    assignedCount++
                }
                mod.copy(categoryId = categoryId)
            }
        }
        if (assignedCount > 0) saveModCategoryAssignments(assignments)
        AutoCategoryResult(updated, assignedCount)
    }

    private fun readModCategoryAssignments(): Map<String, String> {
        val raw = prefs.getString(PREF_MOD_CATEGORY_ASSIGNMENTS, null) ?: return emptyMap()
        return runCatching {
            val json = JSONObject(raw)
            buildMap {
                json.keys().forEach { key -> put(key, json.optString(key)) }
            }
        }.getOrDefault(emptyMap())
    }

    private fun autoCategoryId(mod: Mod, categories: List<ModCategory>): String? {
        // Mod.title is populated from project.xml <Title>. Treat it as a
        // dedicated high-confidence source instead of hiding it in a combined
        // string. Folder names are useful too, while descriptions are weak
        // because most Darkest Dungeon mods mention generic words like dungeon.
        val projectTitle = normalizeCategoryText(mod.title)
        val folderName = normalizeCategoryText(mod.folderName)
        val description = normalizeCategoryText(mod.description)
        val normalizedTags = mod.tags
            .map(::normalizeCategoryText)
            .filter(String::isNotBlank)
            .toSet()

        var bestId: String? = null
        var bestScore = 0
        categories.forEach { category ->
            if (!category.builtIn) return@forEach
            val keywords = CATEGORY_KEYWORDS[category.id] ?: return@forEach
            val score = keywords.maxOfOrNull { keyword ->
                val normalizedKeyword = normalizeCategoryText(keyword)
                val compactKeyword = normalizedKeyword.replace(" ", "")
                val tagMatch = normalizedTags.any { tag ->
                    tag == normalizedKeyword || tag.contains(normalizedKeyword)
                }
                val titleMatch = projectTitle.containsWord(normalizedKeyword) ||
                    projectTitle.replace(" ", "").contains(compactKeyword)
                val folderMatch = folderName.containsWord(normalizedKeyword) ||
                    folderName.replace(" ", "").contains(compactKeyword)
                val descriptionMatch = description.containsWord(normalizedKeyword)
                when {
                    // The XML title identifies what the mod is; it wins
                    // over a conflicting generic tag such as Enemies.
                    titleMatch -> 1_200
                    tagMatch -> 1_000
                    folderMatch -> 800
                    descriptionMatch -> 50
                    else -> 0
                }
            } ?: 0
            if (score > bestScore) {
                bestScore = score
                bestId = category.id
            }
        }
        return bestId
    }

    private fun strongAutoCategoryId(mod: Mod, categories: List<ModCategory>): String? {
        val detected = autoCategoryId(mod, categories) ?: return null
        val projectTitle = normalizeCategoryText(mod.title)
        val folderName = normalizeCategoryText(mod.folderName)
        val normalizedTags = mod.tags.map(::normalizeCategoryText).filter(String::isNotBlank)
        val keywords = CATEGORY_KEYWORDS[detected] ?: return null
        val hasStrongMatch = keywords.any { keyword ->
            val normalizedKeyword = normalizeCategoryText(keyword)
            val compactKeyword = normalizedKeyword.replace(" ", "")
            val titleMatch = projectTitle.containsWord(normalizedKeyword) ||
                projectTitle.replace(" ", "").contains(compactKeyword)
            val folderMatch = folderName.containsWord(normalizedKeyword) ||
                folderName.replace(" ", "").contains(compactKeyword)
            val tagMatch = normalizedTags.any { tag ->
                tag == normalizedKeyword || tag.contains(normalizedKeyword)
            }
            titleMatch || folderMatch || tagMatch
        }
        return detected.takeIf { hasStrongMatch }
    }

    private fun normalizeCategoryText(value: String): String =
        value.lowercase(Locale.ROOT)
            .replace(CATEGORY_SEPARATOR_REGEX, " ")
            .trim()
            .replace(WHITESPACE_REGEX, " ")

    private fun String.containsWord(word: String): Boolean =
        " $this ".contains(" $word ")


    suspend fun scanMods(targetPath: String? = null, uriStr: String? = null): List<Mod> = withContext(Dispatchers.IO) {
        val path = targetPath ?: getActivePath()
        val uri = uriStr ?: getActiveUriStr()
        storageManager.scanMods(path, uri)
    }

    suspend fun toggleModEnabled(
        modId: String,
        modFolderName: String? = null,
        isEnabled: Boolean,
        targetPath: String? = null
    ): List<Mod> = withContext(Dispatchers.IO) {
        val path = targetPath ?: getActivePath()
        val error = storageManager.setModEnabled(
            modId = modId,
            modFolderName = modFolderName,
            isEnabled = isEnabled,
            targetPath = path,
            targetUriStr = getActiveUriStr()
        )
        if (error != null) {
            throw IllegalStateException(error)
        }
        scanMods(path, getActiveUriStr())
    }

    suspend fun saveLoadOrder(modOrder: List<String>, targetPath: String? = null) = withContext(Dispatchers.IO) {
        val path = targetPath ?: getActivePath()
        storageManager.saveLoadOrder(modOrder, path)
    }

    suspend fun createSampleModsIfEmpty(targetPath: String? = null) = withContext(Dispatchers.IO) {
        val path = targetPath ?: getActivePath()
        val dir = File(path)
        storageManager.populateSampleMods(dir)
    }
}
