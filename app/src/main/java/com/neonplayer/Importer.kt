package com.neonplayer

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

object Media {
    fun imagesDir(ctx: Context): File = File(ctx.filesDir, "images").apply { mkdirs() }

    /** Copie une image (redimensionnée) dans le stockage interne de l'appli et renvoie son chemin. */
    fun saveImageBytes(ctx: Context, bytes: ByteArray, maxSide: Int = 1024): String? = try {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        var sample = 1
        while (maxOf(o.outWidth, o.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        if (bmp == null) null else {
            val f = File(imagesDir(ctx), UUID.randomUUID().toString() + ".jpg")
            f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            f.absolutePath
        }
    } catch (e: Exception) { null }

    fun saveImageUri(ctx: Context, uri: Uri): String? = try {
        ctx.contentResolver.openInputStream(uri)?.use { saveImageBytes(ctx, it.readBytes()) }
    } catch (e: Exception) { null }
}

class Importer(private val ctx: Context, private val dao: LibraryDao, private val prefs: AppPrefs) {

    private val exts = setOf("mp3", "wav", "ogg", "oga", "opus", "flac", "m4a", "aac", "wma", "amr", "mid", "3gp", "mka", "weba")

    suspend fun importFiles(uris: List<Uri>): Int = withContext(Dispatchers.IO) {
        var n = 0
        for (u in uris) {
            try { ctx.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            if (addOne(u, null)) n++
        }
        n
    }

    suspend fun importTree(tree: Uri): Int {
        try { ctx.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
        prefs.addTree(tree.toString())
        return scanTree(tree)
    }

    /** Parcourt un dossier déjà autorisé et ajoute uniquement les nouveaux fichiers. */
    suspend fun scanTree(tree: Uri): Int = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(ctx, tree) ?: return@withContext 0
        val files = mutableListOf<DocumentFile>()
        walk(root, files)
        var n = 0
        for (f in files) if (addOne(f.uri, f.name)) n++
        n
    }

    private fun walk(dir: DocumentFile, out: MutableList<DocumentFile>) {
        for (f in dir.listFiles()) {
            if (f.isDirectory) walk(f, out) else if (isAudio(f)) out.add(f)
        }
    }

    private fun isAudio(f: DocumentFile): Boolean =
        f.type?.startsWith("audio/") == true ||
            f.type == "application/ogg" ||
            (f.name?.substringAfterLast('.', "")?.lowercase() in exts)

    private fun displayName(uri: Uri): String? = try {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    } catch (e: Exception) { null }

    private suspend fun addOne(uri: Uri, nameHint: String?): Boolean {
        val key = uri.toString()
        if (dao.countUri(key) > 0) return false
        var title: String? = null
        var artist: String? = null
        var dur = 0L
        var cover: String? = null
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(ctx, uri)
            title = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            artist = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?: r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
            dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            r.embeddedPicture?.let { cover = Media.saveImageBytes(ctx, it) }
        } catch (_: Exception) {
        } finally {
            try { r.release() } catch (_: Exception) {}
        }
        val fallback = (nameHint ?: displayName(uri) ?: "Titre").substringBeforeLast('.')
        val id = dao.insertTrack(
            Track(uri = key, title = title?.takeIf { it.isNotBlank() } ?: fallback, cover = cover, durationMs = dur)
        )
        if (id <= 0) return false
        if (prefs.autoArtists.value && !artist.isNullOrBlank()) {
            val refs = mutableListOf<TrackArtist>()
            for (raw in artist.split(';', '/')) {
                val n = raw.trim()
                if (n.isEmpty()) continue
                val aid = dao.artistByName(n)?.id ?: dao.insertArtist(Artist(name = n))
                refs.add(TrackArtist(id, aid))
            }
            dao.insertTrackArtists(refs)
        }
        return true
    }
}
