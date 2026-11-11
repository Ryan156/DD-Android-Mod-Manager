package com.darkestdungeon.modmanager.data.parser

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.darkestdungeon.modmanager.data.model.ModMetadata
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

object ModMetadataReader {

    private fun displayLanguage(raw: String?): String? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return when (value.lowercase(Locale.ROOT)) {
            "schinese", "zh_cn", "zh-cn", "simplified chinese" -> "Chinese"
            "english", "en" -> "English"
            else -> value.replaceFirstChar { it.uppercase(Locale.ROOT) }
        }
    }

    /**
     * Reads metadata from a physical directory on disk.
     */
    fun readFromFolder(folder: File): ModMetadata {
        if (!folder.isDirectory) {
            return fallbackMetadata(folder.name)
        }

        // Try project.xml first
        val projectXmlFile = File(folder, "project.xml")
        if (projectXmlFile.exists() && projectXmlFile.isFile) {
            runCatching {
                projectXmlFile.inputStream().use { inputStream ->
                    val parsed = parseProjectXml(inputStream)
                    if (parsed != null) return parsed
                }
            }
        }

        // Try modinfo.json second
        val modInfoFile = File(folder, "modinfo.json")
        if (modInfoFile.exists() && modInfoFile.isFile) {
            runCatching {
                val content = modInfoFile.readText()
                val parsed = parseModInfoJson(content)
                if (parsed != null) return parsed
            }
        }

        // Try mod.txt third
        val modTxtFile = File(folder, "mod.txt")
        if (modTxtFile.exists() && modTxtFile.isFile) {
            runCatching {
                val content = modTxtFile.readText()
                val parsed = parseModTxt(content)
                if (parsed != null) return parsed
            }
        }

        return fallbackMetadata(folder.name)
    }

    /**
     * Reads metadata from a SAF DocumentFile folder.
     */
    fun readFromDocumentFile(context: Context, docFolder: DocumentFile): ModMetadata {
        val folderName = docFolder.name ?: "UnknownMod"

        val projectXmlDoc = docFolder.findFile("project.xml")
        if (projectXmlDoc != null && projectXmlDoc.isFile) {
            runCatching {
                context.contentResolver.openInputStream(projectXmlDoc.uri)?.use { inputStream ->
                    val parsed = parseProjectXml(inputStream)
                    if (parsed != null) return parsed
                }
            }
        }

        val modInfoDoc = docFolder.findFile("modinfo.json")
        if (modInfoDoc != null && modInfoDoc.isFile) {
            runCatching {
                context.contentResolver.openInputStream(modInfoDoc.uri)?.use { inputStream ->
                    val content = inputStream.bufferedReader().readText()
                    val parsed = parseModInfoJson(content)
                    if (parsed != null) return parsed
                }
            }
        }

        val modTxtDoc = docFolder.findFile("mod.txt")
        if (modTxtDoc != null && modTxtDoc.isFile) {
            runCatching {
                context.contentResolver.openInputStream(modTxtDoc.uri)?.use { inputStream ->
                    val content = inputStream.bufferedReader().readText()
                    val parsed = parseModTxt(content)
                    if (parsed != null) return parsed
                }
            }
        }

        return fallbackMetadata(folderName)
    }

    /**
     * Parses Darkest Dungeon project.xml content using standard JDK DocumentBuilderFactory.
     */
    fun parseProjectXml(inputStream: InputStream): ModMetadata? {
        return try {
            val dbFactory = DocumentBuilderFactory.newInstance()
            dbFactory.isNamespaceAware = false
            val dBuilder = dbFactory.newDocumentBuilder()
            val doc = dBuilder.parse(inputStream)
            doc.documentElement.normalize()

            fun getTagValue(tagName: String): String? {
                val nodeList = doc.getElementsByTagName(tagName)
                for (i in 0 until nodeList.length) {
                    val item = nodeList.item(i)
                    if (item.nodeType == org.w3c.dom.Node.ELEMENT_NODE) {
                        val text = item.textContent?.trim()
                        if (!text.isNullOrEmpty()) return text
                    }
                }
                return null
            }

            val id = getTagValue("Id") ?: getTagValue("id")
            val title = getTagValue("Title") ?: getTagValue("title")
            val author = getTagValue("Author") ?: getTagValue("author")
            val versionMajor = getTagValue("VersionMajor")
            val versionMinor = getTagValue("VersionMinor")
            val targetBuild = getTagValue("TargetBuild")
            val version = if (versionMajor != null || versionMinor != null || targetBuild != null) {
                listOf(versionMajor ?: "0", versionMinor ?: "0", targetBuild ?: "0")
                    .joinToString(".")
            } else {
                getTagValue("ModVersion") ?: getTagValue("version") ?: getTagValue("Version")
            }
            val description = getTagValue("ItemDescription")
                ?: getTagValue("ItemDescriptionHtml")
                ?: getTagValue("Description")
                ?: getTagValue("description")
            val language = displayLanguage(getTagValue("Language") ?: getTagValue("language"))
            val gameVersion = getTagValue("GameVersion") ?: getTagValue("gameVersion")

            val tags = buildList {
                val tagNodes = doc.getElementsByTagName("Tags")
                for (i in 0 until tagNodes.length) {
                    val node = tagNodes.item(i)
                    if (node.parentNode?.nodeName.equals("Tags", ignoreCase = true)) continue
                    val childTags = (0 until node.childNodes.length)
                        .map { node.childNodes.item(it) }
                        .filter { it.nodeType == org.w3c.dom.Node.ELEMENT_NODE && it.nodeName.equals("Tags", ignoreCase = true) }
                    if (childTags.isNotEmpty()) {
                        childTags.forEach { child ->
                            child.textContent?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
                        }
                    } else {
                        node.textContent
                            ?.split(",", ";")
                            ?.map { it.trim() }
                            ?.filter { it.isNotEmpty() }
                            ?.let(::addAll)
                    }
                }
            }

            val depsStr = getTagValue("Dependencies") ?: getTagValue("dependencies")
            val dependencies = depsStr?.split(",", ";")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

            if (id == null && title == null && description == null && author == null) {
                return null
            }

            ModMetadata(
                id = id,
                title = title,
                description = description,
                author = author,
                version = version,
                dependencies = dependencies,
                tags = tags,
                language = language,
                gameVersion = gameVersion
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Parses JSON mod metadata format.
     */
    fun parseModInfoJson(jsonContent: String): ModMetadata? {
        return try {
            var id: String? = null
            var title: String? = null
            var description: String? = null
            var author: String? = null
            var version: String? = null
            var gameVersion: String? = null
            val tags = mutableListOf<String>()
            val dependencies = mutableListOf<String>()

            // Try JSONObject first
            try {
                val json = JSONObject(jsonContent)
                id = json.optString("id").takeIf { it.isNotEmpty() }
                    ?: json.optString("mod_id").takeIf { it.isNotEmpty() }
                title = json.optString("title").takeIf { it.isNotEmpty() }
                    ?: json.optString("name").takeIf { it.isNotEmpty() }
                description = json.optString("description").takeIf { it.isNotEmpty() }
                author = json.optString("author").takeIf { it.isNotEmpty() }
                    ?: json.optString("creator").takeIf { it.isNotEmpty() }
                version = json.optString("version").takeIf { it.isNotEmpty() }
                gameVersion = json.optString("gameVersion").takeIf { it.isNotEmpty() }

                json.optJSONArray("tags")?.let { jsonArray ->
                    for (i in 0 until jsonArray.length()) {
                        tags.add(jsonArray.getString(i))
                    }
                } ?: json.optString("tags").takeIf { it.isNotEmpty() }?.split(",")?.map { it.trim() }?.let { tags.addAll(it) }

                json.optJSONArray("dependencies")?.let { jsonArray ->
                    for (i in 0 until jsonArray.length()) {
                        dependencies.add(jsonArray.getString(i))
                    }
                }
            } catch (_: Throwable) {
                // Fallback for unmocked unit test execution
            }

            // Regex fallback for simple JSON if JSONObject is unmocked or failed
            if (id == null) {
                id = extractJsonValue(jsonContent, "id") ?: extractJsonValue(jsonContent, "mod_id")
            }
            if (title == null) {
                title = extractJsonValue(jsonContent, "title") ?: extractJsonValue(jsonContent, "name")
            }
            if (description == null) {
                description = extractJsonValue(jsonContent, "description")
            }
            if (author == null) {
                author = extractJsonValue(jsonContent, "author") ?: extractJsonValue(jsonContent, "creator")
            }
            if (version == null) {
                version = extractJsonValue(jsonContent, "version")
            }
            if (tags.isEmpty()) {
                val tagsRegex = Regex("\"tags\"\\s*:\\s*\\[([^\\]]+)\\]")
                tagsRegex.find(jsonContent)?.groupValues?.get(1)?.let { match ->
                    match.split(",").map { it.replace("\"", "").trim() }.filter { it.isNotEmpty() }.forEach { tags.add(it) }
                }
            }

            if (id == null && title == null && description == null && author == null) {
                return null
            }

            ModMetadata(
                id = id,
                title = title,
                description = description,
                author = author,
                version = version,
                dependencies = dependencies,
                tags = tags,
                gameVersion = gameVersion
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun extractJsonValue(json: String, key: String): String? {
        val regex = Regex("\"$key\"\\s*:\\s*\"([^\"]+)\"")
        return regex.find(json)?.groupValues?.get(1)
    }

    /**
     * Parses key-value txt format.
     */
    fun parseModTxt(content: String): ModMetadata? {
        return try {
            var id: String? = null
            var title: String? = null
            var description: String? = null
            var author: String? = null
            var version: String? = null
            val tags = mutableListOf<String>()
            val dependencies = mutableListOf<String>()

            content.lines().forEach { line ->
                val parts = line.split(":", limit = 2)
                if (parts.size == 2) {
                    val key = parts[0].trim().lowercase(Locale.ROOT)
                    val value = parts[1].trim()
                    when (key) {
                        "id" -> id = value
                        "title", "name" -> title = value
                        "description" -> description = value
                        "author" -> author = value
                        "version" -> version = value
                        "tags" -> tags.addAll(value.split(",").map { it.trim() })
                        "dependencies" -> dependencies.addAll(value.split(",").map { it.trim() })
                    }
                }
            }

            if (id == null && title == null && description == null && author == null) {
                return null
            }

            ModMetadata(
                id = id,
                title = title,
                description = description,
                author = author,
                version = version,
                dependencies = dependencies,
                tags = tags
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /** Preferred preview image names — the game's convention FIRST. */
    private val previewCandidates: List<String> = listOf(
        "preview_icon.png", // Wicked port / in-game load order icon
        "preview-icon.png",
        "preview_icon.jpg",
        "preview-icon.jpg",
        "preview.png",
        "preview.jpg",
        "icon.png",
        "icon.jpg",
        "cover.png",
        "cover.jpg",
        "thumb.png"
    )

    /** Image extensions accepted when matching a preview by loose prefix. */
    private val previewExtensions = listOf("png", "jpg", "jpeg", "webp")

    /**
     * Finds the mod's preview image in a physical folder: exact preferred
     * names first, then any `preview*` / `icon*` file with a known extension.
     */
    fun findPreviewImage(folder: File): String? {
        for (name in previewCandidates) {
            val imgFile = File(folder, name)
            if (imgFile.isFile) return imgFile.absolutePath
        }
        // Loose match: any file starting with preview/icon with an image ext.
        folder.listFiles()?.firstOrNull { file ->
            file.isFile && isLoosePreviewName(file.name)
        }?.let { return it.absolutePath }
        return null
    }

    /**
     * Finds image preview in a SAF DocumentFile folder.
     */
    fun findPreviewImage(docFolder: DocumentFile): Uri? {
        val children = docFolder.listFiles()
        // Exact preferred names first (case-insensitive).
        for (name in previewCandidates) {
            children.firstOrNull {
                it.isFile && it.name?.equals(name, ignoreCase = true) == true
            }?.let { return it.uri }
        }
        // Loose match fallback.
        children.firstOrNull { doc ->
            doc.isFile && doc.name?.let { isLoosePreviewName(it) } == true
        }?.let { return it.uri }
        return null
    }

    /** True for names like `preview-icon.png`, `Preview_Icon.JPEG`, `icon2.png`. */
    private fun isLoosePreviewName(name: String): Boolean {
        val lower = name.lowercase()
        val stem = lower.substringBeforeLast('.', "")
        val ext = lower.substringAfterLast('.', "")
        return ext in previewExtensions &&
            (stem.startsWith("preview") || stem.startsWith("icon"))
    }

    /**
     * Fallback metadata when no file is present.
     */
    fun fallbackMetadata(folderName: String): ModMetadata {
        val cleanName = folderName
            .replace(Regex("^\\d+_"), "") // Remove leading Workshop ID prefixes like "123456_"
            .replace("_", " ")
            .replace("-", " ")
            .split(" ")
            .joinToString(" ") { word ->
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
            }

        return ModMetadata(
            id = folderName,
            title = if (cleanName.isNotBlank()) cleanName else folderName,
            description = "Installed Darkest Dungeon mod ($folderName).",
            author = "Unknown Modder",
            version = "1.0"
        )
    }
}
