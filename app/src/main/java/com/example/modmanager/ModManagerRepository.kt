package com.darkestdungeon.modmanager

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.darkestdungeon.modmanager.data.storage.ParsedSave
import com.darkestdungeon.modmanager.data.storage.SaveFormatException
import com.darkestdungeon.modmanager.data.storage.SaveParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
     * Writes [bytes] to the save file, creating a timestamped backup of the
     * current content first and verifying the written content by re-reading it.
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

            // Avoid the costly backup/write/verification round trip when a
            // caller submits bytes identical to the current save. This also
            // makes repeated UI updates effectively free without weakening
            // verification for actual changes.
            if (original.contentEquals(bytes)) return@withContext null

            val backupWarning: String? = runCatching<String?> {
                val backupName = "persist.game.json.backup.${backupTimestamp()}.json"
                if (DocumentsContract.isTreeUri(fileUri)) {
                    // A selected save directory grants permission to create a
                    // visible `backup save` folder beside persist.game.json.
                    val root = DocumentFile.fromTreeUri(context, fileUri)
                        ?: return@runCatching "Backup failed: the save folder could not be opened"
                    val backupDir = root.findFile("backup save")
                        ?: root.createDirectory("backup save")
                        ?: return@runCatching "Backup failed: the backup save folder could not be created"
                    val backupNames = backupDir.listFiles().mapNotNull { it.name }.toSet()
                    var uniqueName = backupName
                    var suffix = 1
                    while (uniqueName in backupNames) {
                        uniqueName = backupName.removeSuffix(".json") + "-$suffix.json"
                        suffix++
                    }
                    val backupFile = backupDir.createFile("application/json", uniqueName)
                        ?: return@runCatching "Backup failed: the backup file could not be created"
                    val backupStream = resolver.openOutputStream(backupFile.uri, "wt")
                        ?: return@runCatching "Backup failed: the backup file could not be opened"
                    backupStream.use { it.write(original) }
                    null
                } else if (fileUri.scheme == "file") {
                    // Direct file access can create the requested folder beside
                    // the original save without relying on SAF parent access.
                    val saveFile = File(Uri.decode(fileUri.path ?: ""))
                    val backupDir = File(saveFile.parentFile ?: context.filesDir, "backup save")
                    if (!backupDir.exists() && !backupDir.mkdirs()) {
                        return@runCatching "Backup failed: the backup save folder could not be created"
                    }
                    val backupFile = uniqueBackupFile(backupDir, backupName)
                    backupFile.writeBytes(original)
                    null
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // OpenDocument grants access to the individual file, not
                    // its parent. Store a visible fallback in public Documents.
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, backupName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                        put(
                            MediaStore.MediaColumns.RELATIVE_PATH,
                            "${Environment.DIRECTORY_DOCUMENTS}/backup save"
                        )
                    }
                    val backupUri = resolver.insert(
                        MediaStore.Files.getContentUri("external"),
                        values
                    ) ?: return@runCatching "Backup failed: Documents storage rejected the backup"
                    val backupStream = resolver.openOutputStream(backupUri, "wt")
                    if (backupStream == null) {
                        resolver.delete(backupUri, null, null)
                        return@runCatching "Backup failed: the Documents backup could not be opened"
                    }
                    backupStream.use { it.write(original) }
                    null
                } else {
                    val backupDir = File(
                        context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
                        "backup save"
                    )
                    if (!backupDir.exists() && !backupDir.mkdirs()) {
                        return@runCatching "Backup failed: the backup save folder could not be created"
                    }
                    uniqueBackupFile(backupDir, backupName).writeBytes(original)
                    null
                }
            }.getOrElse { e ->
                "Backup failed: ${e.message ?: e.javaClass.simpleName}"
            }

            // Do not overwrite the save unless its fallback copy was created.
            if (backupWarning != null) return@withContext backupWarning

            // 2. Write the new content.
            resolver.openOutputStream(actualUri, "wt")?.use { it.write(bytes) }
                ?: return@withContext "The save file could not be opened for writing"

            // 3. Read back and verify: the written bytes must match exactly.
            val written = resolver.openInputStream(actualUri)?.use { it.readBytes() }
                ?: return@withContext "Verification failed: the save file could not be re-read"
            if (!written.contentEquals(bytes)) {
                return@withContext "Verification failed: the save file content does not match what was written"
            }

            null
        }

    private fun backupTimestamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())

    private fun uniqueBackupFile(directory: File, baseName: String): File {
        var candidate = File(directory, baseName)
        var suffix = 1
        while (candidate.exists()) {
            candidate = File(directory, baseName.removeSuffix(".json") + "-$suffix.json")
            suffix++
        }
        return candidate
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
