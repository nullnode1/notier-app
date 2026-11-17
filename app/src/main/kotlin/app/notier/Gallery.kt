package app.notier

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Point
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import java.io.FileNotFoundException
import java.util.Locale

internal data class GalleryImage(
    val uri: Uri, val name: String, val mime: String, val modified: Long,
    val hasThumbnail: Boolean = false, val driveThumbnail: String? = null
)

internal data class GalleryFolder(val name: String, val images: List<GalleryImage>)

internal class Gallery(private val resolver: ContentResolver) {
    fun list(tree: Uri): GalleryFolder {
        val id = DocumentsContract.getTreeDocumentId(tree)
        val root = DocumentsContract.buildDocumentUriUsingTree(tree, id)
        val name = resolver.query(root, arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null } ?: throw FileNotFoundException()
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, id)
        val columns = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE, Document.COLUMN_LAST_MODIFIED, Document.COLUMN_FLAGS)
        val images = resolver.query(children, columns, null, null, null)?.use { rows ->
            buildList<GalleryImage> {
                while (rows.moveToNext()) {
                    val filename = rows.getString(1)?.takeIf { validName(it) } ?: continue
                    val mime = imageMime(filename, rows.getString(2)) ?: continue
                    val uri = DocumentsContract.buildDocumentUriUsingTree(tree, rows.getString(0))
                    val thumbnail = rows.getInt(4) and Document.FLAG_SUPPORTS_THUMBNAIL != 0
                    add(GalleryImage(uri, filename, mime, rows.getLong(3), thumbnail))
                }
            }
        } ?: throw FileNotFoundException()
        val ordered = images.sortedWith(compareByDescending<GalleryImage> { it.modified }
            .thenBy { it.name.lowercase(Locale.ROOT) }.thenBy { it.uri.toString() })
        return GalleryFolder(name, ordered.distinctBy { it.name })
    }

    fun thumbnail(image: GalleryImage, edge: Int): Bitmap? {
        if (image.hasThumbnail) {
            runCatching { DocumentsContract.getDocumentThumbnail(resolver, image.uri, Point(edge, edge), null) }
                .getOrNull()?.let { return it }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(image.uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > edge * 2) sample *= 2
        return resolver.openInputStream(image.uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }

    companion object {
        private val imageTypes = mapOf(
            "png" to "image/png", "jpg" to "image/jpeg", "jpeg" to "image/jpeg",
            "webp" to "image/webp", "gif" to "image/gif", "avif" to "image/avif",
            "svg" to "image/svg+xml", "bmp" to "image/bmp", "ico" to "image/x-icon"
        )
        private val mimeTypes = imageTypes.values.toSet() + "image/vnd.microsoft.icon"

        private fun validName(name: String) = name.isNotBlank() && name != "." && name != ".." &&
            name.none { it == '/' || it == '\\' || it.isISOControl() }

        fun imageMime(name: String, provider: String?): String? {
            if (provider == Document.MIME_TYPE_DIR || provider.orEmpty().startsWith("application/vnd.google-apps.")) return null
            return provider?.lowercase(Locale.ROOT)?.takeIf { it in mimeTypes }
                ?: imageTypes[name.substringAfterLast('.', "").lowercase(Locale.ROOT)]
        }

    }
}
