package com.hybridmusic.player.data.local

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.hybridmusic.player.data.local.entity.SongEntity
import com.hybridmusic.player.data.local.entity.SyncStatus
import java.io.File
import java.security.MessageDigest

class ZongStorageManager(private val context: Context) {

    companion object {
        const val ZONG_FOLDER_NAME = "Zong"
        val ZONG_RELATIVE_PATH: String = "${Environment.DIRECTORY_MUSIC}/$ZONG_FOLDER_NAME"
    }

    private val internalCoverCacheDir: File by lazy {
        File(context.filesDir, "zong_covers").apply { mkdirs() }
    }

    /**
     * Creates Internal Storage/Music/Zong immediately when the app is installed/launched.
     */
    fun ensureZongFolderExists(): File {
        val publicMusicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        val zongDir = File(publicMusicDir, ZONG_FOLDER_NAME)
        runCatching {
            if (!zongDir.exists()) {
                zongDir.mkdirs()
            }
        }
        return zongDir
    }

    fun getDisplayFolderPath(): String = "Music/$ZONG_FOLDER_NAME"

    /**
     * Saves the tagged .m4a file (containing Cover Art + Song Title + Artist)
     * into the device's Music/Zong folder and registers it in Android MediaStore.
     */
    fun saveTaggedSongToZongFolder(
        taggedAudioFile: File,
        title: String,
        artist: String,
        album: String = ZONG_FOLDER_NAME
    ): String? {
        ensureZongFolderExists()
        val safeBaseName = buildSafeFileName(title, artist)
        val displayName = "$safeBaseName.m4a"

        // Android 10+ (API 29+): Write directly into Music/Zong via MediaStore
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

            // Replace existing duplicate in Music/Zong if present
            runCatching {
                resolver.delete(
                    collection,
                    "${MediaStore.Audio.Media.DISPLAY_NAME} = ? AND ${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?",
                    arrayOf(displayName, "%$ZONG_FOLDER_NAME%")
                )
            }

            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Audio.Media.TITLE, title)
                put(MediaStore.Audio.Media.ARTIST, artist)
                put(MediaStore.Audio.Media.ALBUM, album.ifBlank { ZONG_FOLDER_NAME })
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                put(MediaStore.Audio.Media.IS_MUSIC, 1)
                put(MediaStore.Audio.Media.RELATIVE_PATH, ZONG_RELATIVE_PATH)
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }

            val itemUri = resolver.insert(collection, values)
            if (itemUri != null) {
                val writeOk = runCatching {
                    resolver.openOutputStream(itemUri)?.use { out ->
                        taggedAudioFile.inputStream().use { input ->
                            input.copyTo(out)
                        }
                    }
                    true
                }.getOrDefault(false)

                if (writeOk) {
                    val doneValues = ContentValues().apply {
                        put(MediaStore.Audio.Media.IS_PENDING, 0)
                    }
                    resolver.update(itemUri, doneValues, null, null)
                    return itemUri.toString()
                } else {
                    runCatching { resolver.delete(itemUri, null, null) }
                }
            }
        }

        // Android 8-9 or direct filesystem fallback: /storage/emulated/0/Music/Zong
        val zongDir = ensureZongFolderExists()
        val destFile = File(zongDir, displayName)
        return runCatching {
            taggedAudioFile.copyTo(destFile, overwrite = true)
            MediaScannerConnection.scanFile(
                context,
                arrayOf(destFile.absolutePath),
                arrayOf("audio/mp4"),
                null
            )
            destFile.absolutePath
        }.getOrNull()
    }

    /**
     * Scans the Music/Zong folder and extracts embedded Cover Art + Title + Artist
     * from every audio file inside it.
     */
    fun scanExistingZongSongs(): List<SongEntity> {
        ensureZongFolderExists()
        val discovered = mutableListOf<SongEntity>()
        val seenKeys = mutableSetOf<String>()

        // 1. Query MediaStore for Music/Zong entries
        runCatching {
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }

            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATA
            )
            val selection = "${MediaStore.Audio.Media.DATA} LIKE ?"
            val selectionArgs = arrayOf("%/$ZONG_FOLDER_NAME/%")

            context.contentResolver.query(
                collection,
                projection,
                selection,
                selectionArgs,
                "${MediaStore.Audio.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val contentUri = ContentUris.withAppendedId(collection, id)
                    val filePath = cursor.getString(dataCol) ?: ""
                    val key = filePath.ifBlank { contentUri.toString() }
                    if (!seenKeys.add(key)) continue

                    val extracted = extractEmbeddedInfo(
                        uri = contentUri,
                        fallbackTitle = cursor.getString(titleCol) ?: "Unknown Title",
                        fallbackArtist = cursor.getString(artistCol) ?: "Unknown Artist",
                        fallbackAlbum = cursor.getString(albumCol) ?: ZONG_FOLDER_NAME,
                        fallbackDurationMs = cursor.getLong(durCol),
                        uniqueSeed = key
                    )
                    discovered.add(extracted)
                }
            }
        }

        // 2. Direct scan of Music/Zong directory
        val zongDir = ensureZongFolderExists()
        zongDir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in setOf("m4a", "mp3", "aac", "flac") }
            ?.forEach { file ->
                if (seenKeys.add(file.absolutePath)) {
                    val extracted = extractEmbeddedInfo(
                        uri = Uri.fromFile(file),
                        fallbackTitle = file.nameWithoutExtension.substringBefore(" - "),
                        fallbackArtist = file.nameWithoutExtension.substringAfter(" - ", "Zong"),
                        fallbackAlbum = ZONG_FOLDER_NAME,
                        fallbackDurationMs = 0L,
                        uniqueSeed = file.absolutePath
                    )
                    discovered.add(extracted)
                }
            }

        return discovered
    }

    private fun extractEmbeddedInfo(
        uri: Uri,
        fallbackTitle: String,
        fallbackArtist: String,
        fallbackAlbum: String,
        fallbackDurationMs: Long,
        uniqueSeed: String
    ): SongEntity {
        val stableId = "zong_" + sha1Short(uniqueSeed)
        var title = fallbackTitle
        var artist = fallbackArtist
        var album = fallbackAlbum
        var duration = fallbackDurationMs
        var thumbPath: String? = null

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() }?.let { title = it }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?.takeIf { it.isNotBlank() }?.let { artist = it }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?.takeIf { it.isNotBlank() }?.let { album = it }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()?.let { if (it > 0L) duration = it }

            val embeddedCover = retriever.embeddedPicture
            if (embeddedCover != null && embeddedCover.isNotEmpty()) {
                val coverFile = File(internalCoverCacheDir, "$stableId.jpg")
                coverFile.writeBytes(embeddedCover)
                thumbPath = coverFile.absolutePath
            }
        } catch (_: Exception) {
        } finally {
            runCatching { retriever.release() }
        }

        return SongEntity(
            videoId = stableId,
            title = title,
            artist = artist,
            album = album,
            durationMs = duration,
            localAudioPath = uri.toString(),
            localThumbPath = thumbPath,
            syncStatus = SyncStatus.DOWNLOADED_LOCAL
        )
    }

    fun deleteFromZongFolder(localPathOrUri: String?) {
        if (localPathOrUri.isNullOrBlank()) return
        if (localPathOrUri.startsWith("content://")) {
            runCatching { context.contentResolver.delete(Uri.parse(localPathOrUri), null, null) }
        } else {
            runCatching { File(localPathOrUri).delete() }
        }
    }

    private fun buildSafeFileName(title: String, artist: String): String {
        val raw = if (artist.isNotBlank()) "$title - $artist" else title
        return raw.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(100)
            .ifBlank { "Zong_Track_${System.currentTimeMillis()}" }
    }

    private fun sha1Short(input: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(input.toByteArray())
        return digest.take(6).joinToString("") { "%02x".format(it) }
    }
}
