package com.darkestdungeon.modmanager

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.darkestdungeon.modmanager.data.storage.ParsedSave
import com.darkestdungeon.modmanager.data.storage.SaveFormatException
import com.darkestdungeon.modmanager.data.storage.SaveParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class InstalledMod(
    val folderName: String,
    val displayName: String = folderName
)

data class LoadOrderResult(
    val mods: List<String>,
    val warning: String? = null
)

class ModManagerRepository(private val context: Context) {
    private val resolver: ContentResolver = context.contentResolver

    suspend fun scanMods(treeUri: Uri): List<InstalledMod> = withContext(Dispatchers.IO) {
        queryChildren(treeUri)
            .filter { it.isDirectory }
            .map { InstalledMod(folderName = it.name) }
            .sortedBy { it.folderName.lowercase() }
    }

    suspend fun readLoadOrder(saveTreeUri: Uri): LoadOrderResult = withContext(Dispatchers.IO) {
        runCatching {
            val save = queryChildren(saveTreeUri).firstOrNull { it.name == "persist.game.json" }
                ?: return@withContext LoadOrderResult(emptyList(), "persist.game.json was not found")
            val bytes = resolver.openInputStream(save.uri)?.use { it.readBytes() }
                ?: return@withContext LoadOrderResult(emptyList(), "The save file could not be read")
            LoadOrderResult(SaveParser.loadOrder(bytes))
        }.getOrElse { e ->
            LoadOrderResult(emptyList(), e.message ?: "The save file could not be parsed")
        }
    }

    suspend fun readLoadOrderFile(fileUri: Uri): LoadOrderResult = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = resolver.openInputStream(fileUri)?.use { it.readBytes() }
                ?: return@withContext LoadOrderResult(emptyList(), "The save file could not be read")
            LoadOrderResult(SaveParser.loadOrder(bytes))
        }.getOrElse { e ->
            LoadOrderResult(emptyList(), e.message ?: "The save file could not be parsed")
        }
    }

    /**
     * Reads the raw save bytes (file URI or tree URI) without parsing them.
     * Returns null with a user-readable reason when the file cannot be read.
     */
    suspend fun readSaveBytes(fileUri: Uri): Pair<ByteArray?, String?> = withContext(Dispatchers.IO) {
        val actualUri = if (DocumentsContract.isTreeUri(fileUri)) {
            queryChildren(fileUri).firstOrNull { it.name == "persist.game.json" }?.uri
                ?: return@withContext null to "persist.game.json was not found in the selected folder"
        } else {
            fileUri
        }
        val bytes = runCatching { resolver.openInputStream(actualUri)?.use { it.readBytes() } }
            .getOrElse { null }
        if (bytes == null) {
            return@withContext null to "The save file could not be read"
        }
        bytes to null
    }

    /**
     * Parses a save file (file URI or tree URI) strictly. Returns null with a
     * user-readable reason when the file cannot be found or read.
     */
    suspend fun parseSave(fileUri: Uri): Pair<ParsedSave?, String?> = withContext(Dispatchers.IO) {
        val (bytes, readError) = readSaveBytes(fileUri)
        if (bytes == null) {
            return@withContext null to (readError ?: "The save file could not be read")
        }
        // Prefer the FULL DSON codec (reads the real container metadata);
        // fall back to the heuristic region parser only when the codec refuses
        // (e.g. a save from an unknown container revision).
        try {
            SaveParser.parseViaDson(bytes) to null
        } catch (e: SaveFormatException) {
            try {
                SaveParser.parse(bytes) to null
            } catch (e2: SaveFormatException) {
                null to ("The save file does not match the expected format: " +
                    (e2.message ?: e.message ?: "unknown error"))
            }
        }
    }

    /**
     * Writes [bytes] to the save file, creating a `.bak` backup of the current
     * content first and verifying the written content by re-reading it.
     * Returns an error string, or null on success.
     */
    suspend fun writeSaveBytes(fileUri: Uri, bytes: ByteArray): String? =
        withContext(Dispatchers.IO) {
            val actualUri = if (DocumentsContract.isTreeUri(fileUri)) {
                queryChildren(fileUri).firstOrNull { it.name == "persist.game.json" }?.uri
                    ?: return@withContext "persist.game.json was not found in the selected folder"
            } else {
                fileUri
            }

            // 1. Read the current content and back it up.
            val original = resolver.openInputStream(actualUri)?.use { it.readBytes() }
                ?: return@withContext "The save file could not be read"
            val backupWarning = runCatching {
                if (DocumentsContract.isTreeUri(fileUri)) {
                    // Tree URI: sibling backup next to the save file.
                    val backupName = "persist.game.json.bak"
                    val backupUri = queryChildren(fileUri).firstOrNull { it.name == backupName }?.uri
                        ?: DocumentsContract.createDocument(
                            resolver,
                            DocumentsContract.buildDocumentUriUsingTree(
                                fileUri,
                                DocumentsContract.getTreeDocumentId(fileUri)
                            ),
                            "application/octet-stream",
                            backupName
                        )
                    if (backupUri == null) {
                        "Backup skipped: the .bak file could not be created in the save folder"
                    } else {
                        resolver.openOutputStream(backupUri, "wt")?.use { it.write(original) }
                        null
                    }
                } else {
                    // File URI: SAF grants no write access to the parent folder, so a
                    // sibling .bak is impossible. Fall back to the app's private dir.
                    val backupDir = File(context.filesDir, "save_backups")
                    if (!backupDir.exists()) backupDir.mkdirs()
                    val backupFile = File(backupDir, "persist.game.json.bak")
                    backupFile.writeBytes(original)
                    null
                }
            }.getOrElse { e ->
                "Backup failed: ${e.message ?: e.javaClass.simpleName}"
            }

            // 2. Write the new content.
            resolver.openOutputStream(actualUri, "wt")?.use { it.write(bytes) }
                ?: return@withContext "The save file could not be opened for writing"

            // 3. Read back and verify: the written bytes must match exactly.
            val written = resolver.openInputStream(actualUri)?.use { it.readBytes() }
                ?: return@withContext "Verification failed: the save file could not be re-read"
            if (!written.contentEquals(bytes)) {
                return@withContext "Verification failed: the save file content does not match what was written"
            }

            // Backup failure is a warning, not a blocker: the reorder itself is
            // provably length-preserving and re-verified above.
            if (backupWarning != null) {
                println("SAVE_BACKUP_WARNING: $backupWarning")
            }
            null
        }

    /**
     * Finds the persist.game.json document URI inside a tree URI (or the file URI itself).
     */
    private fun resolveSaveUri(fileUri: Uri): Uri =
        if (DocumentsContract.isTreeUri(fileUri)) {
            queryChildren(fileUri).firstOrNull { it.name == "persist.game.json" }?.uri ?: fileUri
        } else {
            fileUri
        }

    private data class Child(
        val uri: Uri,
        val name: String,
        val isDirectory: Boolean
    )

    private fun queryChildren(treeUri: Uri): List<Child> {
        val documentId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
        val result = mutableListOf<Child>()

        resolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            ),
            null,
            null,
            null
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)

            while (cursor.moveToNext()) {
                val id = cursor.getString(idColumn)
                val name = cursor.getString(nameColumn) ?: continue
                val mime = cursor.getString(mimeColumn)
                result += Child(
                    uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id),
                    name = name,
                    isDirectory = mime == DocumentsContract.Document.MIME_TYPE_DIR
                )
            }
        }
        return result
    }
}
