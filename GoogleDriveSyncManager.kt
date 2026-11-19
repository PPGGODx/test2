package com.hybridmusic.player.data.remote.cloud

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.hybridmusic.player.data.local.entity.PlaylistEntity
import com.hybridmusic.player.data.local.entity.PlaylistSongCrossRef
import com.hybridmusic.player.data.local.entity.SongEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class CloudLibrarySnapshot(
    val exportedAt: Long = System.currentTimeMillis(),
    val songs: List<SongEntity> = emptyList(),
    val playlists: List<PlaylistEntity> = emptyList(),
    val crossRefs: List<PlaylistSongCrossRef> = emptyList()
)

data class DriveFolderConnection(
    val treeUri: Uri,
    val folderName: String,
    val isGoogleDriveProvider: Boolean
)

data class DriveFileEntry(
    val documentId: String,
    val documentUri: Uri,
    val displayName: String,
    val mimeType: String
)

class GoogleDriveSyncManager(
    private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "playzongx_google_drive_prefs"
        private const val KEY_TREE_URI = "drive_tree_uri"
        private const val KEY_FOLDER_NAME = "drive_folder_name"
        private const val BACKUP_JSON_NAME = "PlayZongX_Library_Backup.json"
        const val GOOGLE_DRIVE_AUTHORITY = "com.google.android.apps.docs.storage"
    }

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    private val _connectedFolder = MutableStateFlow<DriveFolderConnection?>(loadSavedConnection())
    val connectedFolder: StateFlow<DriveFolderConnection?> = _connectedFolder.asStateFlow()

    private fun loadSavedConnection(): DriveFolderConnection? {
        val uriString = prefs.getString(KEY_TREE_URI, null) ?: return null
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return null
        val hasPerm = context.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission && it.isWritePermission
        }
        if (!hasPerm) {
            prefs.edit().remove(KEY_TREE_URI).remove(KEY_FOLDER_NAME).apply()
            return null
        }
        val savedName = prefs.getString(KEY_FOLDER_NAME, null) ?: resolveFolderDisplayName(uri)
        return DriveFolderConnection(
            treeUri = uri,
            folderName = savedName,
            isGoogleDriveProvider = uri.authority?.contains("docs.storage") == true
        )
    }

    fun getSuggestedDriveInitialUri(): Uri? {
        return runCatching {
            DocumentsContract.buildRootUri(GOOGLE_DRIVE_AUTHORITY, "primary")
        }.getOrNull()
    }

    fun connectFolderUri(treeUri: Uri): Result<DriveFolderConnection> {
        return runCatching {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(treeUri, flags)
            val folderName = resolveFolderDisplayName(treeUri)
            prefs.edit()
                .putString(KEY_TREE_URI, treeUri.toString())
                .putString(KEY_FOLDER_NAME, folderName)
                .apply()

            val connection = DriveFolderConnection(
                treeUri = treeUri,
                folderName = folderName,
                isGoogleDriveProvider = treeUri.authority?.contains("docs.storage") == true
            )
            _connectedFolder.value = connection
            connection
        }
    }

    fun disconnectFolder() {
        _connectedFolder.value?.let { current ->
            runCatching {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.releasePersistableUriPermission(current.treeUri, flags)
            }
        }
        prefs.edit().remove(KEY_TREE_URI).remove(KEY_FOLDER_NAME).apply()
        _connectedFolder.value = null
    }

    private fun resolveFolderDisplayName(treeUri: Uri): String {
        return runCatching {
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
            context.contentResolver.query(
                docUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)?.takeIf { it.isNotBlank() }
                } else null
            } ?: "Google Drive / PlayZongX"
        }.getOrDefault("Google Drive / PlayZongX")
    }

    suspend fun listFilesInConnectedFolder(): List<DriveFileEntry> = withContext(Dispatchers.IO) {
        val conn = _connectedFolder.value ?: return@withContext emptyList()
        val treeId = DocumentsContract.getTreeDocumentId(conn.treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(conn.treeUri, treeId)
        val result = mutableListOf<DriveFileEntry>()

        runCatching {
            context.contentResolver.query(
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
                val idIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (cursor.moveToNext()) {
                    val docId = cursor.getString(idIdx) ?: continue
                    val name = cursor.getString(nameIdx) ?: continue
                    val mime = cursor.getString(mimeIdx) ?: "application/octet-stream"
                    val docUri = DocumentsContract.buildDocumentUriUsingTree(conn.treeUri, docId)
                    result.add(DriveFileEntry(docId, docUri, name, mime))
                }
            }
        }
        result
    }

    suspend fun uploadMetadataSnapshot(snapshot: CloudLibrarySnapshot): Boolean =
        withContext(Dispatchers.IO) {
            val conn = _connectedFolder.value ?: return@withContext false
            val jsonPayload = json.encodeToString(snapshot)
            val existingFiles = listFilesInConnectedFolder()
            val existingBackup = existingFiles.firstOrNull { it.displayName == BACKUP_JSON_NAME }

            runCatching {
                val targetUri = existingBackup?.documentUri ?: run {
                    val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(
                        conn.treeUri,
                        DocumentsContract.getTreeDocumentId(conn.treeUri)
                    )
                    DocumentsContract.createDocument(
                        context.contentResolver,
                        parentDocUri,
                        "application/json",
                        BACKUP_JSON_NAME
                    )
                } ?: return@withContext false

                context.contentResolver.openOutputStream(targetUri, "wt")?.use { out ->
                    out.write(jsonPayload.toByteArray(Charsets.UTF_8))
                    out.flush()
                } ?: return@withContext false
                true
            }.getOrDefault(false)
        }

    suspend fun downloadMetadataSnapshot(): CloudLibrarySnapshot? =
        withContext(Dispatchers.IO) {
            val existingFiles = listFilesInConnectedFolder()
            val backupFile = existingFiles.firstOrNull { it.displayName == BACKUP_JSON_NAME }
                ?: return@withContext null

            readSnapshotFromUri(backupFile.documentUri)
        }

    suspend fun exportSnapshotToSingleUri(targetUri: Uri, snapshot: CloudLibrarySnapshot): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val payload = json.encodeToString(snapshot)
                context.contentResolver.openOutputStream(targetUri, "wt")?.use { out ->
                    out.write(payload.toByteArray(Charsets.UTF_8))
                    out.flush()
                } ?: return@withContext false
                true
            }.getOrDefault(false)
        }

    suspend fun readSnapshotFromUri(sourceUri: Uri): CloudLibrarySnapshot? =
        withContext(Dispatchers.IO) {
            runCatching {
                val content = context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    input.bufferedReader(Charsets.UTF_8).readText()
                } ?: return@withContext null
                json.decodeFromString<CloudLibrarySnapshot>(content)
            }.getOrNull()
        }

    suspend fun uploadAudioFile(videoId: String, title: String, localFile: File): String? =
        withContext(Dispatchers.IO) {
            if (!localFile.exists() || localFile.length() == 0L) return@withContext null
            val conn = _connectedFolder.value ?: return@withContext null

            val safeTitle = title.replace(Regex("[^a-zA-Z0-9ก-๙ _-]"), "").trim().take(40)
            val remoteFileName = if (safeTitle.isNotEmpty()) {
                "${videoId}_${safeTitle}.m4a"
            } else {
                "${videoId}.m4a"
            }

            val existing = listFilesInConnectedFolder().firstOrNull {
                it.displayName.startsWith(videoId)
            }
            if (existing != null) {
                return@withContext existing.documentUri.toString()
            }

            runCatching {
                val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(
                    conn.treeUri,
                    DocumentsContract.getTreeDocumentId(conn.treeUri)
                )
                val createdUri = DocumentsContract.createDocument(
                    context.contentResolver,
                    parentDocUri,
                    "audio/mp4",
                    remoteFileName
                ) ?: return@withContext null

                context.contentResolver.openOutputStream(createdUri, "wt")?.use { out ->
                    localFile.inputStream().use { input ->
                        input.copyTo(out)
                    }
                    out.flush()
                } ?: return@withContext null

                createdUri.toString()
            }.getOrNull()
        }

    suspend fun downloadAudioFile(driveUriOrId: String, destinationFile: File): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val uri = if (driveUriOrId.startsWith("content://")) {
                    Uri.parse(driveUriOrId)
                } else {
                    listFilesInConnectedFolder()
                        .firstOrNull { it.displayName.startsWith(driveUriOrId) }
                        ?.documentUri
                } ?: return@withContext false

                context.contentResolver.openInputStream(uri)?.use { input ->
                    destinationFile.parentFile?.mkdirs()
                    destinationFile.outputStream().use { out ->
                        input.copyTo(out)
                    }
                } ?: return@withContext false

                destinationFile.exists() && destinationFile.length() > 0L
            }.getOrDefault(false)
        }

    fun resolveDisplayNameFromUri(uri: Uri): String {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
        }.getOrNull() ?: uri.lastPathSegment ?: "Drive Audio Track"
    }
}
