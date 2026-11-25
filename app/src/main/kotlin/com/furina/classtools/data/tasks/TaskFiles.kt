package com.furina.classtools.data.tasks

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID
import kotlinx.serialization.json.*

internal object TaskFiles {
    private fun file(context: Context, id: String): File {
        require(UUID.fromString(id).toString() == id)
        return File(File(context.filesDir, "task_attachments"), id)
    }

    fun copy(context: Context, uri: Uri): JsonObject {
        val resolver = context.contentResolver
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }.orEmpty().ifBlank { "附件" }
        val id = UUID.randomUUID().toString()
        val output = file(context, id)
        output.parentFile?.mkdirs()
        try {
            val type = resolver.getType(uri) ?: "application/octet-stream"
            requireNotNull(resolver.openInputStream(uri)).use { input -> output.outputStream().use { input.copyTo(it) } }
            return buildJsonObject { put("id", id); put("n", name.take(120)); put("t", type); put("s", output.length()) }
        } catch (e: Exception) {
            output.delete()
            throw e
        }
    }

    fun open(context: Context, attachment: JsonObject) {
        val output = file(context, attachment.text("id"))
        check(output.isFile) { "附件文件不存在" }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", output, attachment.text("n"))
        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, attachment.text("t"))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }

    /** Web and WeChat backups embed small files. Larger task documents stay on the originating device. */
    fun backup(context: Context, snapshot: JsonObject): JsonObject {
        val portable = taskBackup(snapshot)
        return portable.withFields {
            put("items", JsonArray(snapshot.objects("items").map { task ->
                val attachments = task.objects("localFiles").map { attachment ->
                    val source = file(context, attachment.text("id"))
                    attachment.withFields {
                        if (source.length() <= 480L * 1024 && source.isFile)
                            put("d", Base64.encodeToString(source.readBytes(), Base64.NO_WRAP))
                    }
                }
                JsonObject(task - "localFiles").withFields { put("attachments", if (attachments.isEmpty()) "" else JsonArray(attachments).toString()) }
            }))
        }
    }

    fun restore(context: Context, repository: TaskRepository, backup: JsonObject): Int {
        val staged = mutableListOf<JsonObject>()
        var unavailable = 0
        try {
            repository.restore(backup) { task ->
                val encoded = when (val att = task["attachments"]) {
                    is JsonArray -> att
                    is JsonPrimitive -> runCatching { Json.parseToJsonElement(att.content) as? JsonArray }.getOrNull()
                    else -> null
                }
                encoded.orEmpty().filterIsInstance<JsonObject>().take(2).mapNotNull { att ->
                    val data = att.text("d")
                    if (data.isBlank()) { unavailable++; return@mapNotNull null }
                    require(data.length <= 1_400_000) { "备份附件数据异常" }
                    val bytes = Base64.decode(data, Base64.DEFAULT)
                    val id = UUID.randomUUID().toString()
                    val output = file(context, id)
                    output.parentFile?.mkdirs()
                    val metadata = buildJsonObject {
                        put("id", id); put("n", att.text("n").take(120).ifBlank { "附件" })
                        put("t", att.text("t").ifBlank { "application/octet-stream" }); put("s", bytes.size)
                    }
                    staged += metadata
                    output.writeBytes(bytes)
                    metadata
                }
            }
        } catch (e: Exception) {
            discard(context, staged)
            throw e
        }
        return unavailable
    }

    fun discard(context: Context, files: List<JsonObject>) {
        files.forEach { runCatching { file(context, it.text("id")).delete() } }
    }

    fun prune(context: Context, before: JsonObject, after: JsonObject) {
        val kept = after.objects("items").flatMap { it.objects("localFiles") }.map { it.text("id") }.toSet()
        discard(context, before.objects("items").flatMap { it.objects("localFiles") }.filter { it.text("id") !in kept })
    }
}
