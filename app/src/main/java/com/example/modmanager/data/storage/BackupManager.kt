package com.darkestdungeon.modmanager.data.storage

import com.darkestdungeon.modmanager.data.model.Mod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream

data class ModProfileEntry(
    val id: String,
    val folderName: String,
    val title: String,
    val isEnabled: Boolean,
    val loadOrder: Int,
    val version: String = "1.0",
    val author: String = "Unknown"
)

data class ModBackupProfile(
    val version: Int = 1,
    val timestamp: Long = System.currentTimeMillis(),
    val profileName: String = "Darkest Dungeon Mod Setup",
    val mods: List<ModProfileEntry> = emptyList()
)

data class RestoreResult(
    val restoredCount: Int,
    val missingCount: Int,
    val missingModTitles: List<String> = emptyList()
)

class BackupManager {

    /**
     * Serializes mod configuration profile into JSON and writes to OutputStream.
     */
    suspend fun exportProfile(
        mods: List<Mod>,
        outputStream: OutputStream,
        profileName: String = "Darkest Dungeon Mod Setup"
    ) = withContext(Dispatchers.IO) {
        val rootJson = JSONObject()
        rootJson.put("version", 1)
        rootJson.put("timestamp", System.currentTimeMillis())
        rootJson.put("profileName", profileName)

        val modsArray = JSONArray()
        mods.sortedBy { it.loadOrder }.forEach { mod ->
            val modJson = JSONObject()
            modJson.put("id", mod.id)
            modJson.put("folderName", mod.folderName)
            modJson.put("title", mod.title)
            modJson.put("isEnabled", mod.isEnabled)
            modJson.put("loadOrder", mod.loadOrder)
            modJson.put("version", mod.version)
            modJson.put("author", mod.author)
            modsArray.put(modJson)
        }
        rootJson.put("mods", modsArray)

        outputStream.bufferedWriter().use { writer ->
            writer.write(rootJson.toString(2))
        }
    }

    /**
     * Parses JSON backup profile from InputStream.
     */
    suspend fun importProfile(inputStream: InputStream): ModBackupProfile = withContext(Dispatchers.IO) {
        val text = inputStream.bufferedReader().use { it.readText() }
        parseProfileJson(text)
    }

    fun parseProfileJson(jsonText: String): ModBackupProfile {
        val json = JSONObject(jsonText)
        val version = json.optInt("version", 1)
        val timestamp = json.optLong("timestamp", System.currentTimeMillis())
        val profileName = json.optString("profileName", "Darkest Dungeon Mod Setup")

        val modsArray = json.optJSONArray("mods") ?: JSONArray()
        val entries = mutableListOf<ModProfileEntry>()

        for (i in 0 until modsArray.length()) {
            val modObj = modsArray.getJSONObject(i)
            entries.add(
                ModProfileEntry(
                    id = modObj.optString("id", ""),
                    folderName = modObj.optString("folderName", ""),
                    title = modObj.optString("title", "Unknown Mod"),
                    isEnabled = modObj.optBoolean("isEnabled", true),
                    loadOrder = modObj.optInt("loadOrder", i),
                    version = modObj.optString("version", "1.0"),
                    author = modObj.optString("author", "Unknown")
                )
            )
        }

        return ModBackupProfile(
            version = version,
            timestamp = timestamp,
            profileName = profileName,
            mods = entries
        )
    }

    /**
     * Re-applies enabled/disabled states and load order priority from profile to installed mods in targetModDir.
     */
    suspend fun restoreProfile(
        profile: ModBackupProfile,
        targetModDir: File,
        storageManager: ModStorageManager
    ): RestoreResult = withContext(Dispatchers.IO) {
        val installedMods = storageManager.scanMods(targetModDir.absolutePath)
        val installedMap = installedMods.associateBy { it.id }
        val installedByFolderMap = installedMods.associateBy { it.folderName }

        val sortedProfileEntries = profile.mods.sortedBy { it.loadOrder }
        val restoredModIds = mutableListOf<String>()
        val missingTitles = mutableListOf<String>()

        sortedProfileEntries.forEach { profileEntry ->
            val matchedMod = installedMap[profileEntry.id] ?: installedByFolderMap[profileEntry.folderName]
            if (matchedMod != null) {
                storageManager.setModEnabled(
                    modId = matchedMod.id,
                    modFolderName = matchedMod.folderName,
                    isEnabled = profileEntry.isEnabled,
                    targetPath = targetModDir.absolutePath
                )
                restoredModIds.add(matchedMod.id)
            } else {
                missingTitles.add(profileEntry.title.ifEmpty { profileEntry.folderName })
            }
        }

        // Add any currently installed mods that were NOT in the profile to the end of load order
        val unmatchedInstalled = installedMods.filter { it.id !in restoredModIds }
        unmatchedInstalled.forEach { mod ->
            restoredModIds.add(mod.id)
        }

        // Save new load order priority
        storageManager.saveLoadOrder(restoredModIds, targetModDir.absolutePath)

        RestoreResult(
            restoredCount = restoredModIds.size - unmatchedInstalled.size,
            missingCount = missingTitles.size,
            missingModTitles = missingTitles
        )
    }
}
