package app.notier

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import java.io.FileNotFoundException
import java.util.Locale

internal data class NoteFile(val uri: Uri, val name: String, val modified: Long, val flags: Int = 0, val isFolder: Boolean = false) {
    fun supports(capability: Int) = flags and capability != 0
}
internal data class Folder(val name: String, val notes: List<NoteFile>, val hasSubfolders: Boolean)
internal class NoteChanged : IllegalStateException("Dosya başka bir uygulamada değiştirildi.")
internal class NoteRenameFailed(val draft: Draft, val duplicate: Boolean = false, cause: Exception? = null) : Exception(cause)

internal class Notes(private val resolver: ContentResolver) {
    fun list(tree: Uri): Folder {
        val id = DocumentsContract.getTreeDocumentId(tree)
        val root = DocumentsContract.buildDocumentUriUsingTree(tree, id)
        val name = displayName(root).orEmpty()
        val columns = arrayOf(
            Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE, Document.COLUMN_LAST_MODIFIED
        )
        val files = mutableListOf<NoteFile>()
        var hasSubfolders = false
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, id)
        val cursor = resolver.query(children, columns, null, null, null)
            ?: throw FileNotFoundException("Klasör açılamadı.")
        cursor.use {
            while (it.moveToNext()) {
                val filename = it.getString(1) ?: continue
                if (it.getString(2) == Document.MIME_TYPE_DIR) {
                    hasSubfolders = true
                    continue
                }
                if (!filename.isMarkdownName()) continue
                val uri = DocumentsContract.buildDocumentUriUsingTree(tree, it.getString(0))
                files += NoteFile(uri, filename, if (it.isNull(3)) 0 else it.getLong(3))
            }
        }
        return Folder(name, files.sortedWith(
            compareByDescending<NoteFile> { it.modified }
                .thenBy { it.name.lowercase(Locale.ROOT) }.thenBy { it.uri.toString() }
        ), hasSubfolders)
    }

    fun displayName(uri: Uri): String? =
        resolver.query(uri, arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }

    fun document(uri: Uri): NoteFile? {
        val columns = arrayOf(Document.COLUMN_DISPLAY_NAME, Document.COLUMN_LAST_MODIFIED, Document.COLUMN_FLAGS)
        return resolver.query(uri, columns, null, null, null)?.use { row ->
            if (!row.moveToFirst()) return@use null
            val name = row.getString(0) ?: return@use null
            NoteFile(uri, name, if (row.isNull(1)) 0 else row.getLong(1), if (row.isNull(2)) 0 else row.getInt(2))
        }
    }

    fun read(uri: Uri): String {
        val stream = resolver.openInputStream(uri) ?: throw FileNotFoundException("Dosya açılamadı.")
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    fun update(draft: Draft, name: String, tree: Uri?): Draft {
        val uri = requireNotNull(draft.uri)
        if (read(uri) != draft.original) throw NoteChanged()
        if (name != draft.name && tree != null && draft.local && nameTaken(tree, uri, name))
            throw NoteRenameFailed(draft, duplicate = true)
        if (draft.text != draft.original) write(uri, draft.text)
        val saved = draft.copy(original = draft.text)
        if (name == draft.name) return saved.copy(heading = null)
        val renamed = try {
            renameDocument(NoteFile(uri, draft.name, 0), name)
        } catch (error: Exception) {
            throw NoteRenameFailed(saved, cause = error)
        }
        return saved.copy(uri = renamed.uri, name = renamed.name, heading = null)
    }

    fun create(tree: Uri, name: String, markdown: String): Uri {
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val uri = DocumentsContract.createDocument(resolver, parent, MIME, name)
            ?: throw FileNotFoundException("Bu klasöre yeni dosya eklenemedi.")
        try {
            write(uri, markdown)
        } catch (error: Exception) {
            runCatching { DocumentsContract.deleteDocument(resolver, uri) }
            throw error
        }
        return uri
    }

    fun write(uri: Uri, markdown: String) {
        val stream = resolver.openOutputStream(uri, "wt") ?: throw FileNotFoundException("Dosya yazılamadı.")
        stream.use { it.write(markdown.toByteArray(Charsets.UTF_8)) }
    }

    fun delete(uri: Uri) {
        check(DocumentsContract.deleteDocument(resolver, uri))
    }

    fun rename(file: NoteFile, name: String, tree: Uri?): NoteFile? {
        if (file.name == name) return file
        if (tree != null && nameTaken(tree, file.uri, name)) return null
        return renameDocument(file, name)
    }

    private fun nameTaken(tree: Uri, uri: Uri, name: String): Boolean {
        val id = DocumentsContract.getDocumentId(uri)
        return list(tree).notes.any {
            it.name.equals(name, ignoreCase = true) && DocumentsContract.getDocumentId(it.uri) != id
        }
    }

    private fun renameDocument(file: NoteFile, name: String): NoteFile {
        val uri = DocumentsContract.renameDocument(resolver, file.uri, name)
            ?: throw FileNotFoundException("Rename failed.")
        return file.copy(uri = uri, name = runCatching { displayName(uri) }.getOrNull() ?: name)
    }

    fun image(tree: Uri, path: List<String>): Uri? {
        var parentId = DocumentsContract.getTreeDocumentId(tree)
        for ((index, segment) in path.withIndex()) {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
            val columns = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE)
            val child = resolver.query(children, columns, null, null, null)?.use { rows ->
                while (rows.moveToNext()) {
                    if (rows.getString(1) == segment) return@use rows.getString(0) to rows.getString(2)
                }
                null
            } ?: return null
            val (id, mime) = child
            if (index == path.lastIndex) {
                return if (mime.startsWith("image/") || segment.substringAfterLast('.').lowercase(Locale.ROOT) in Markdown.imageTypes)
                    DocumentsContract.buildDocumentUriUsingTree(tree, id) else null
            }
            if (mime != Document.MIME_TYPE_DIR) return null
            parentId = id
        }
        return null
    }

    companion object {
        // Generic binary MIME preserves the exact .md suffix across Android's system picker.
        const val MIME = "application/octet-stream"
    }
}
