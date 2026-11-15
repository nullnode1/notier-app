package app.notier

import android.net.Uri
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException

internal data class Draft(
    val uri: Uri?,
    val name: String,
    val original: String,
    val text: String,
    val local: Boolean,
    val heading: String? = null,
    val driveParent: String? = null
) {
    val dirty get() = (if (uri == null) text.isNotBlank() else text != original) || heading != null
}

internal class DraftStore(directory: File) {
    private val file = AtomicFile(File(directory, "notier-draft.json"))

    fun read(): Draft? {
        val bytes = try {
            file.readFully()
        } catch (_: FileNotFoundException) {
            return null
        }
        val json = JSONObject(bytes.toString(Charsets.UTF_8))
        val uri = json.optString("uri").takeIf(String::isNotEmpty)?.let(Uri::parse)
        val heading = if (json.has("heading") && !json.isNull("heading")) json.getString("heading") else null
        val driveParent = json.optString("driveParent").takeIf(String::isNotEmpty)
        return Draft(uri, json.getString("name"), json.getString("original"), json.getString("text"), json.getBoolean("local"), heading, driveParent)
    }

    fun store(draft: Draft?, keepClean: Boolean = false) {
        if (draft == null || (!draft.dirty && !keepClean)) {
            file.delete()
            return
        }
        val bytes = JSONObject()
            .put("uri", draft.uri?.toString().orEmpty())
            .put("name", draft.name)
            .put("original", draft.original)
            .put("text", draft.text)
            .put("local", draft.local)
            .put("heading", draft.heading)
            .put("driveParent", draft.driveParent)
            .toString().toByteArray(Charsets.UTF_8)
        val output = file.startWrite()
        try {
            output.write(bytes)
            file.finishWrite(output)
        } catch (error: Exception) {
            file.failWrite(output)
            throw error
        }
    }
}
