package app.notier

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract.Document

internal data class DriveListing(val files: List<NoteFile>, val unavailable: Int)
internal class DriveReadOnly : IllegalStateException()

internal class DriveDocuments(private val resolver: ContentResolver, private val notes: Notes) {
    fun list(): DriveListing {
        var unavailable = 0
        val files = resolver.persistedUriPermissions.mapNotNull { grant ->
            if (!grant.isReadPermission || !isDrive(grant.uri)) return@mapNotNull null
            val note = runCatching { notes.document(grant.uri) }.getOrNull()
            if (note == null) {
                unavailable++
                return@mapNotNull null
            }
            if (note.name.isMarkdownName()) note.copy(flags = if (grant.isWritePermission) note.flags else 0) else null
        }.sortedWith(compareByDescending<NoteFile> { it.modified }.thenBy { it.name })
        return DriveListing(files, unavailable)
    }

    fun writable(uri: Uri): Boolean {
        val grant = resolver.persistedUriPermissions.firstOrNull { it.uri == uri }
        val document = notes.document(uri) ?: return false
        return grant?.isWritePermission == true && document.supports(Document.FLAG_SUPPORTS_WRITE)
    }

    fun remember(uri: Uri, flags: Int) {
        if (!isDrive(uri)) return
        val granted = flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        if (granted and Intent.FLAG_GRANT_READ_URI_PERMISSION == 0) throw SecurityException("Missing document read grant")
        resolver.takePersistableUriPermission(uri, granted)
    }

    fun rememberRename(old: Uri, renamed: Uri) {
        if (!isDrive(renamed) || old == renamed) return
        val permissions = resolver.persistedUriPermissions
        val original = permissions.firstOrNull { it.uri == old }
        val updated = permissions.firstOrNull { it.uri == renamed }
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
            (if (original?.isWritePermission == true) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
        val alreadyHasGrant = updated?.isReadPermission == true &&
            (original?.isWritePermission != true || updated.isWritePermission)
        if (alreadyHasGrant || runCatching { remember(renamed, flags) }.isSuccess) {
            if (original?.isReadPermission == true) runCatching { resolver.releasePersistableUriPermission(old, flags) }
        }
    }

    companion object {
        fun isDrive(uri: Uri): Boolean = uri.scheme == ContentResolver.SCHEME_CONTENT &&
            isDriveAuthority(uri.authority?.substringAfter('@'))

        fun isDriveAuthority(authority: String?): Boolean = authority == "com.google.android.apps.docs.storage" ||
            authority == "com.google.android.apps.docs.storage.legacy"
    }
}

internal fun String.isMarkdownName(): Boolean =
    endsWith(".md", ignoreCase = true) || endsWith(".markdown", ignoreCase = true)
