package com.darkestdungeon.modmanager.data.storage

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.darkestdungeon.modmanager.data.model.Mod
import com.darkestdungeon.modmanager.data.model.ModMetadata
import com.darkestdungeon.modmanager.data.parser.ModMetadataReader
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ModStorageManager(private val context: Context) {

    companion object {
        const val MOD_LIST_FILE_NAME = "mod_list.json"
        const val DISABLED_MODS_FOLDER = "Disabled Mods"
        private val HIDDEN_GENERATED_FOLDERS = setOf(".port-nx-original", ".port-switch")
    }

    // Metadata parsing is the most expensive part of a rescan. Keep a small
    // in-memory cache for unchanged physical mod folders; icon discovery and
    // folder enumeration still run so newly added/removed files are visible.
    private data class CachedMetadata(
        val fingerprint: String,
        val metadata: ModMetadata
    )

    private val metadataCache = HashMap<String, CachedMetadata>()

    private fun readCachedMetadata(folder: File): ModMetadata {
        val metadataFiles = listOf("project.xml", "modinfo.json", "mod.txt")
            .map { File(folder, it) }
        val fingerprint = metadataFiles.joinToString("|") { file ->
            "${file.name}:${file.exists()}:${file.lastModified()}:${file.length()}"
        }
        val key = folder.absolutePath
        val cached = metadataCache[key]
        if (cached != null && cached.fingerprint == fingerprint) return cached.metadata

        val metadata = ModMetadataReader.readFromFolder(folder)
        metadataCache[key] = CachedMetadata(fingerprint, metadata)
        return metadata
    }

    /**
     * Scans installed mods from a File directory path or SAF DocumentFile.
     *
     * ALL mod folders are listed as installed — including anything inside
     * "Disabled Mods/" — because enabling/disabling is ORDER-ONLY in this app:
     * a mod is off because it is not in the load order, never because its
     * folder was moved. (The legacy folder-move toggle is gone.)
     */
    fun scanMods(targetPath: String, targetUriStr: String? = null): List<Mod> {
        val mods = mutableListOf<Mod>()

        // 1. Check if SAF Tree Uri is provided
        if (!targetUriStr.isNullOrEmpty()) {
            val uri = Uri.parse(targetUriStr)
            val docDir = DocumentFile.fromTreeUri(context, uri)
            if (docDir != null && docDir.exists() && docDir.isDirectory) {
                return scanDocumentFileMods(docDir)
            }
        }

        // 2. Scan standard File directory
        val rootDir = File(targetPath)
        if (!rootDir.exists()) {
            rootDir.mkdirs()
        }

        val config = loadModListConfig(rootDir)
        val loadOrderMap = config.loadOrder.withIndex().associate { it.value to it.index }

        // All folders on disk list as installed — enable/disable is order-only.
        // Legacy "Disabled Mods/" folders are surfaced like any other mod.
        val disabledDir = File(rootDir, DISABLED_MODS_FOLDER)
        val subFolders = (
            rootDir.listFiles()?.filter {
                it.isDirectory &&
                    it.name != DISABLED_MODS_FOLDER &&
                    it.name !in HIDDEN_GENERATED_FOLDERS
            } ?: emptyList()
            ) + (
            disabledDir.listFiles()?.filter {
                it.isDirectory && it.name !in HIDDEN_GENERATED_FOLDERS
            } ?: emptyList()
            )

        subFolders.forEach { folder ->
            val metadata = readCachedMetadata(folder)
            val modId = metadata.id ?: folder.name

            val iconPath = ModMetadataReader.findPreviewImage(folder)

            val order = loadOrderMap[modId] ?: Int.MAX_VALUE

            mods.add(
                Mod(
                    id = modId,
                    title = metadata.title ?: ModMetadataReader.fallbackMetadata(folder.name).title!!,
                    description = metadata.description.orEmpty(),
                    author = metadata.author ?: "Unknown Modder",
                    version = metadata.version ?: "1.0",
                    folderName = folder.name,
                    folderPath = folder.absolutePath,
                    isEnabled = true,
                    loadOrder = order,
                    iconPath = iconPath,
                    dependencies = metadata.dependencies,
                    tags = metadata.tags.ifEmpty { listOf("Mod") },
                    language = metadata.language,
                    gameVersion = metadata.gameVersion,
                    lastModified = folder.lastModified(),
                    hasPreviewImage = iconPath != null
                )
            )
        }

        // Sort by load order, then by title
        return sortModsByLoadOrder(mods)
    }

    private fun scanDocumentFileMods(docDir: DocumentFile): List<Mod> {
        val mods = mutableListOf<Mod>()
        // ALL folders list as installed — enable/disable is ORDER-ONLY in this
        // app (a mod is off because it is not in the load order, never because
        // its folder was moved). Legacy "Disabled Mods/" folders are surfaced
        // like any other mod.
        val subFiles = (
            docDir.listFiles()
                .filter {
                    it.isDirectory &&
                        it.name != DISABLED_MODS_FOLDER &&
                        it.name !in HIDDEN_GENERATED_FOLDERS
                }
            ) + (
            docDir.findFile(DISABLED_MODS_FOLDER)
                ?.listFiles()
                ?.filter {
                    it.isDirectory && it.name !in HIDDEN_GENERATED_FOLDERS
                }
                ?: emptyList()
            )

        subFiles.forEachIndexed { index, folder ->
            val folderName = folder.name ?: "Mod_$index"
            val metadata = ModMetadataReader.readFromDocumentFile(context, folder)
            val modId = metadata.id ?: folderName

            val previewUri = ModMetadataReader.findPreviewImage(folder)

            mods.add(
                Mod(
                    id = modId,
                    title = metadata.title ?: ModMetadataReader.fallbackMetadata(folderName).title!!,
                    description = metadata.description.orEmpty(),
                    author = metadata.author ?: "Unknown Modder",
                    version = metadata.version ?: "1.0",
                    folderName = folderName,
                    folderPath = folder.uri.toString(),
                    isEnabled = true,
                    loadOrder = index,
                    iconPath = previewUri?.toString(),
                    dependencies = metadata.dependencies,
                    tags = metadata.tags.ifEmpty { listOf("Mod") },
                    language = metadata.language,
                    gameVersion = metadata.gameVersion,
                    lastModified = folder.lastModified(),
                    hasPreviewImage = previewUri != null
                )
            )
        }

        return sortModsByLoadOrder(mods)
    }

    /**
     * Toggles a mod enabled/disabled status.
     */
    /**
     * Toggles a mod enabled/disabled by moving its folder in/out of the
     * disabled directory. Returns a null error on success, or a user-readable
     * reason — callers MUST surface the reason instead of ignoring it.
     *
     * Matching precedence: exact folder-name match first (the UI knows the
     * folder name), then metadata-id match (parses project.xml per folder).
     */
    fun setModEnabled(
        modId: String,
        modFolderName: String? = null,
        isEnabled: Boolean,
        targetPath: String,
        targetUriStr: String? = null
    ): String? {
        if (!targetUriStr.isNullOrEmpty()) {
            val root = DocumentFile.fromTreeUri(context, Uri.parse(targetUriStr))
                ?: return "The mod folder could not be opened (SAF)."
            val disabledRoot = root.findFile(DISABLED_MODS_FOLDER)
                ?: root.createDirectory(DISABLED_MODS_FOLDER)
                ?: return "The disabled-mods folder could not be created."
            val sourceParent = if (isEnabled) disabledRoot else root
            val targetParent = if (isEnabled) root else disabledRoot
            // Folder-name match first, then metadata-id match.
            val source = sourceParent.listFiles().firstOrNull { document ->
                document.isDirectory && document.name == modFolderName
            } ?: sourceParent.listFiles().firstOrNull { document ->
                document.isDirectory && (
                    document.name == modId ||
                        ModMetadataReader.readFromDocumentFile(context, document).id == modId
                    )
            } ?: return "The mod folder was not found " +
                "(looked for '${modFolderName ?: modId}')."
            val moved = runCatching {
                DocumentsContract.moveDocument(
                    context.contentResolver,
                    source.uri,
                    sourceParent.uri,
                    targetParent.uri
                ) != null
            }.getOrElse { e ->
                return "The mod folder could not be moved: ${e.message ?: e.javaClass.simpleName}"
            }
            return if (moved) null else "The mod folder could not be moved (SAF refused)."
        }

        val rootDir = File(targetPath)
        if (!rootDir.exists()) return "The mod folder path does not exist: $targetPath"
        val disabledDir = File(rootDir, DISABLED_MODS_FOLDER)
        if (!disabledDir.exists()) disabledDir.mkdirs()

        val sourceParent = if (isEnabled) disabledDir else rootDir
        val targetParent = if (isEnabled) rootDir else disabledDir
        // Folder-name match first, then metadata-id match.
        val source = sourceParent.listFiles()?.firstOrNull { file ->
            file.isDirectory && file.name == modFolderName
        } ?: sourceParent.listFiles()?.firstOrNull { file ->
            if (!file.isDirectory) return@firstOrNull false
            val meta = ModMetadataReader.readFromFolder(file)
            meta.id == modId || file.name == modId
        } ?: return "The mod folder was not found (looked for '${modFolderName ?: modId}')."

        if (source.parentFile?.path == targetParent.path) return null // already in place
        val target = File(targetParent, source.name)
        val renamed = source.renameTo(target)
        return if (renamed) null
        else "The mod folder could not be moved " +
            "(rename failed — check storage permissions for $targetPath)."
    }

    /**
     * Saves custom load order index.
     */
    fun saveLoadOrder(orderedModIds: List<String>, targetPath: String) {
        val rootDir = File(targetPath)
        if (!rootDir.exists()) rootDir.mkdirs()

        val config = loadModListConfig(rootDir)
        saveModListConfig(rootDir, ModListConfig(orderedModIds, config.disabledMods))
    }

    private fun sortModsByLoadOrder(mods: List<Mod>): List<Mod> {
        val sorted = mods.sortedWith(
            compareBy<Mod> { it.loadOrder }
                .thenBy { it.title }
        )
        return sorted.mapIndexed { idx, mod -> mod.copy(loadOrder = idx) }
    }

    /**
     * Load order & disabled list configuration file.
     */
    private data class ModListConfig(
        val loadOrder: List<String> = emptyList(),
        val disabledMods: List<String> = emptyList()
    )

    private fun loadModListConfig(rootDir: File): ModListConfig {
        val configFile = File(rootDir, MOD_LIST_FILE_NAME)
        if (!configFile.exists() || !configFile.isFile) {
            return ModListConfig()
        }

        return try {
            val text = configFile.readText()
            val json = JSONObject(text)
            val orderArray = json.optJSONArray("mod_order") ?: JSONArray()
            val disabledArray = json.optJSONArray("disabled_mods") ?: JSONArray()

            val order = mutableListOf<String>()
            for (i in 0 until orderArray.length()) {
                order.add(orderArray.getString(i))
            }

            val disabled = mutableListOf<String>()
            for (i in 0 until disabledArray.length()) {
                disabled.add(disabledArray.getString(i))
            }

            ModListConfig(order, disabled)
        } catch (e: Exception) {
            e.printStackTrace()
            ModListConfig()
        }
    }

    private fun saveModListConfig(rootDir: File, config: ModListConfig) {
        val configFile = File(rootDir, MOD_LIST_FILE_NAME)
        try {
            val json = JSONObject()
            val orderArray = JSONArray(config.loadOrder)
            val disabledArray = JSONArray(config.disabledMods)
            json.put("mod_order", orderArray)
            json.put("disabled_mods", disabledArray)
            configFile.writeText(json.toString(2))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Creates realistic sample Darkest Dungeon mods into target folder for immediate testing.
     */
    fun populateSampleMods(rootDir: File) {
        if (!rootDir.exists()) rootDir.mkdirs()

        val sampleMods = listOf(
            SampleModInfo(
                folderName = "falconer_class_mod",
                title = "The Falconer Class Mod",
                id = "219381001",
                author = "Marney & Team",
                version = "2.4",
                description = "Adds the Falconer hero class to Darkest Dungeon with custom sprites, trinkets, and town events.",
                tags = listOf("Hero Class", "Trinkets", "Audio"),
                xmlContent = """
                    <project>
                        <ModData>
                            <Id>219381001</Id>
                            <Title>The Falconer Class Mod</Title>
                            <Author>Marney &amp; Team</Author>
                            <ModVersion>2.4</ModVersion>
                            <Tags>Hero Class, Trinkets, Audio</Tags>
                            <ItemDescriptionHtml>Adds the Falconer hero class to Darkest Dungeon with custom skills and trinkets.</ItemDescriptionHtml>
                        </ModData>
                    </project>
                """.trimIndent()
            ),
            SampleModInfo(
                folderName = "mariner_hero",
                title = "The Mariner Class",
                id = "219381002",
                author = "ActionJack",
                version = "1.8",
                description = "Seafaring front-line warrior using harpoons and tidal anchors to disrupt enemy positions.",
                tags = listOf("Hero Class", "Skins"),
                xmlContent = """
                    <project>
                        <ModData>
                            <Id>219381002</Id>
                            <Title>The Mariner Class</Title>
                            <Author>ActionJack</Author>
                            <ModVersion>1.8</ModVersion>
                            <Tags>Hero Class, Skins</Tags>
                            <ItemDescriptionHtml>Seafaring front-line warrior using harpoons and tidal anchors.</ItemDescriptionHtml>
                        </ModData>
                    </project>
                """.trimIndent()
            ),
            SampleModInfo(
                folderName = "faster_walking_qol",
                title = "Faster Dungeon Walking & Combat",
                id = "219381003",
                author = "QoL Masters",
                version = "3.0",
                description = "Increases dungeon exploration walk speed by 50% and speeds up battle animations.",
                tags = listOf("QoL", "Speed"),
                xmlContent = """
                    <project>
                        <ModData>
                            <Id>219381003</Id>
                            <Title>Faster Dungeon Walking &amp; Combat</Title>
                            <Author>QoL Masters</Author>
                            <ModVersion>3.0</ModVersion>
                            <Tags>QoL, Speed</Tags>
                            <ItemDescriptionHtml>Increases dungeon exploration walk speed by 50%.</ItemDescriptionHtml>
                        </ModData>
                    </project>
                """.trimIndent()
            ),
            SampleModInfo(
                folderName = "inventory_stack_expansion",
                title = "Expanded Inventory Stacks",
                id = "219381004",
                author = "GoldHoarder",
                version = "1.2",
                description = "Increases maximum stack sizes for gold, heirlooms, gemstones, and supplies.",
                tags = listOf("QoL", "Inventory", "Balance"),
                xmlContent = """
                    <project>
                        <ModData>
                            <Id>219381004</Id>
                            <Title>Expanded Inventory Stacks</Title>
                            <Author>GoldHoarder</Author>
                            <ModVersion>1.2</ModVersion>
                            <Tags>QoL, Inventory</Tags>
                            <ItemDescriptionHtml>Increases maximum stack sizes for gold, heirlooms, and gems.</ItemDescriptionHtml>
                        </ModData>
                    </project>
                """.trimIndent()
            ),
            SampleModInfo(
                folderName = "pitch_black_dungeon_overhaul",
                title = "Pitch Black Dungeon Overhaul",
                id = "219381005",
                author = "Maester Silvio",
                version = "5.1",
                description = "Massive gameplay overhaul introducing tactical depth, dangerous enemy AI, and new potion mechanics.",
                tags = listOf("Overhaul", "Dungeon", "Hardcore"),
                xmlContent = """
                    <project>
                        <ModData>
                            <Id>219381005</Id>
                            <Title>Pitch Black Dungeon Overhaul</Title>
                            <Author>Maester Silvio</Author>
                            <ModVersion>5.1</ModVersion>
                            <Tags>Overhaul, Dungeon, Hardcore</Tags>
                            <ItemDescriptionHtml>Massive gameplay overhaul introducing tactical depth.</ItemDescriptionHtml>
                        </ModData>
                    </project>
                """.trimIndent()
            )
        )

        sampleMods.forEach { sample ->
            val modFolder = File(rootDir, sample.folderName)
            if (!modFolder.exists()) {
                modFolder.mkdirs()
                val xmlFile = File(modFolder, "project.xml")
                xmlFile.writeText(sample.xmlContent)
            }
        }
    }

    private data class SampleModInfo(
        val folderName: String,
        val title: String,
        val id: String,
        val author: String,
        val version: String,
        val description: String,
        val tags: List<String>,
        val xmlContent: String
    )
}
