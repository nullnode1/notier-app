package app.notier

import android.net.Uri
import android.provider.DocumentsContract.Document
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.InterruptedIOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.util.UUID

internal class DriveRequestFailed(val status: Int) : IOException("Google Drive HTTP $status")
internal data class DriveItem(val id: String, val name: String, val parent: String?, val folder: Boolean, val flags: Int, val modified: Long) {
    val uri: Uri get() = DriveApi.uri(id)
    fun note() = NoteFile(uri, name, modified, flags, folder)
}

internal class DriveApi(internal val accessToken: String) {
    fun folder(id: String): DriveItem {
        val metadata = JSONObject(request("/drive/v3/files/${encode(id)}",
            query("fields" to "$FIELDS,trashed", "supportsAllDrives" to "true")))
        val item = json(metadata)
        if (!item.folder || metadata.optBoolean("trashed")) throw DriveRequestFailed(404)
        return item
    }

    fun children(id: String): List<NoteFile> {
        val items = mutableListOf<NoteFile>()
        listFiles("${parentFilter(id)} and trashed = false", LIST_FIELDS) { metadata ->
            if (metadata.optString("mimeType") == FOLDER || isMarkdown(metadata)) {
                items += json(metadata).note()
            }
        }
        return items.sortedWith(compareByDescending<NoteFile> { it.isFolder }.thenByDescending { it.modified }.thenBy { it.name })
    }

    fun childCounts(folderIds: List<String>): Map<String, Int> {
        val counts = folderIds.associateWith { 0 }.toMutableMap()
        if (counts.isEmpty()) return counts
        val parents = counts.keys.joinToString(" or ", "(", ")", transform = ::parentFilter)
        val excludedTypes = listOf(FOLDER, "application/vnd.google-apps.document",
            "application/vnd.google-apps.spreadsheet", "application/vnd.google-apps.presentation",
            "application/vnd.google-apps.shortcut")
        val mimeFilter = excludedTypes.joinToString(" and ") { "mimeType != '$it'" }
        listFiles("$parents and trashed = false and $mimeFilter", "name,mimeType,parents") { file ->
            if (!isMarkdown(file)) return@listFiles
            val parentIds = file.optJSONArray("parents") ?: return@listFiles
            for (index in 0 until parentIds.length()) {
                counts.computeIfPresent(parentIds.getString(index)) { _, count -> count + 1 }
            }
        }
        return counts
    }

    private fun isMarkdown(file: JSONObject) = file.optString("name").isMarkdownName() &&
        !file.optString("mimeType").startsWith("application/vnd.google-apps.")

    private fun parentFilter(id: String) = "'${id.replace("\\", "\\\\").replace("'", "\\'")}' in parents"

    private fun listFiles(filter: String, fields: String, consume: (JSONObject) -> Unit) {
        var page: String? = null
        do {
            if (Thread.currentThread().isInterrupted) throw InterruptedIOException()
            val parameters = mutableListOf(
                "q" to filter,
                "pageSize" to "1000", "fields" to "nextPageToken,incompleteSearch,files($fields)",
                "supportsAllDrives" to "true", "includeItemsFromAllDrives" to "true"
            )
            page?.let { parameters += "pageToken" to it }
            val response = JSONObject(request("/drive/v3/files", query(*parameters.toTypedArray())))
            if (response.optBoolean("incompleteSearch")) throw IOException("Incomplete Drive listing")
            val files = response.optJSONArray("files") ?: JSONArray()
            for (index in 0 until files.length()) consume(files.getJSONObject(index))
            page = response.optString("nextPageToken").takeIf(String::isNotEmpty)
        } while (page != null)
    }

    fun read(id: String): String = request("/drive/v3/files/${encode(id)}", query("alt" to "media", "supportsAllDrives" to "true"))

    fun accountEmail(): String? = JSONObject(request("/drive/v3/about", query("fields" to "user(emailAddress)")))
        .optJSONObject("user")?.optString("emailAddress")?.takeIf(String::isNotBlank)

    fun writable(id: String): Boolean = json(request("/drive/v3/files/${encode(id)}",
        query("fields" to FIELDS, "supportsAllDrives" to "true"))).flags and Document.FLAG_SUPPORTS_WRITE != 0

    fun create(parent: String, name: String, text: String): DriveItem {
        val metadata = JSONObject().put("name", name).put("parents", JSONArray().put(parent)).put("mimeType", "text/markdown")
        return upload(null, metadata, text)
    }

    fun createFolder(parent: String, name: String): DriveItem =
        patch(null, JSONObject().put("name", name).put("parents", JSONArray().put(parent)).put("mimeType", FOLDER))

    fun rename(id: String, name: String): DriveItem = patch(id, JSONObject().put("name", name))

    fun trash(id: String) {
        patch(id, JSONObject().put("trashed", true))
    }

    fun move(id: String, parent: String, destination: String): DriveItem {
        if (id == destination || parent == destination) throw DriveRequestFailed(400)
        return json(request("/drive/v3/files/${encode(id)}", query("supportsAllDrives" to "true",
            "fields" to FIELDS, "addParents" to destination, "removeParents" to parent),
            "PATCH", "application/json; charset=UTF-8", "{}".toByteArray()))
    }

    fun update(draft: Draft, name: String): Draft {
        val id = requireNotNull(draft.uri?.let(::id))
        if (read(id) != draft.original) throw NoteChanged()
        val saved = when {
            draft.text != draft.original -> {
                val metadata = JSONObject()
                if (name != draft.name) metadata.put("name", name)
                upload(id, metadata, draft.text)
            }
            name != draft.name -> rename(id, name)
            else -> return draft.copy(heading = null)
        }
        return draft.copy(uri = saved.uri, name = saved.name, original = draft.text, heading = null)
    }

    private fun patch(id: String?, metadata: JSONObject): DriveItem {
        val suffix = id?.let { "/${encode(it)}" }.orEmpty()
        val response = request("/drive/v3/files$suffix", query("supportsAllDrives" to "true", "fields" to FIELDS),
            if (id == null) "POST" else "PATCH", "application/json; charset=UTF-8", metadata.toString().toByteArray())
        return json(response)
    }

    private fun upload(id: String?, metadata: JSONObject, content: String): DriveItem {
        val boundary = "notier_${UUID.randomUUID()}"
        val body = "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$metadata\r\n" +
            "--$boundary\r\nContent-Type: text/markdown; charset=UTF-8\r\n\r\n$content\r\n--$boundary--\r\n"
        val suffix = id?.let { "/${encode(it)}" }.orEmpty()
        return json(request("/upload/drive/v3/files$suffix",
            query("uploadType" to "multipart", "supportsAllDrives" to "true", "fields" to FIELDS),
            if (id == null) "POST" else "PATCH", "multipart/related; boundary=$boundary", body.toByteArray()))
    }

    private fun request(path: String, query: String, method: String = "GET", contentType: String? = null, body: ByteArray? = null): String {
        val connection = URL("https://www.googleapis.com$path?$query").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 12_000
            connection.readTimeout = 25_000
            connection.instanceFollowRedirects = false
            connection.requestMethod = method
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", contentType)
                connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { it.write(body) }
            }
            val status = connection.responseCode
            if (status !in 200..299) throw DriveRequestFailed(status)
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun json(text: String) = json(JSONObject(text))

    private fun json(file: JSONObject): DriveItem {
        val caps = file.optJSONObject("capabilities") ?: JSONObject()
        var flags = 0
        if (caps.optBoolean("canEdit")) flags = flags or Document.FLAG_SUPPORTS_WRITE
        if (caps.optBoolean("canRename")) flags = flags or Document.FLAG_SUPPORTS_RENAME
        if (caps.optBoolean("canTrash")) flags = flags or Document.FLAG_SUPPORTS_DELETE
        if (caps.optBoolean("canAddChildren")) flags = flags or Document.FLAG_DIR_SUPPORTS_CREATE
        if (caps.optBoolean("canMoveItemWithinDrive")) flags = flags or CAN_MOVE
        return DriveItem(
            file.getString("id"), file.getString("name"), file.optJSONArray("parents")?.optString(0)?.takeIf(String::isNotEmpty),
            file.optString("mimeType") == FOLDER, flags,
            runCatching { Instant.parse(file.optString("modifiedTime")).toEpochMilli() }.getOrDefault(0)
        )
    }

    private fun query(vararg pairs: Pair<String, String>) = pairs.joinToString("&") { (key, value) -> "$key=${encode(value)}" }
    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    companion object {
        private const val FOLDER = "application/vnd.google-apps.folder"
        private const val LIST_FIELDS = "id,name,mimeType,modifiedTime,capabilities(canEdit,canRename,canTrash,canAddChildren,canMoveItemWithinDrive)"
        private const val FIELDS = "$LIST_FIELDS,parents"
        const val CAN_MOVE = 1 shl 28
        const val SCOPE = "https://www.googleapis.com/auth/drive"
        fun uri(id: String): Uri = Uri.Builder().scheme("notier-drive").authority("file").appendPath(id).build()
        fun id(uri: Uri): String? = if (uri.scheme == "notier-drive" && uri.authority == "file") uri.pathSegments.singleOrNull() else null
    }
}
