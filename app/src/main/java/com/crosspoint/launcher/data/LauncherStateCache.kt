package com.crosspoint.launcher.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.crosspoint.launcher.model.AppInfo
import com.crosspoint.launcher.model.Book
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

/**
 * The launcher's last known apps, icons and books, so Home can draw its previous state on the
 * first frame and only change what differs once fresh data has loaded. Everything here is a
 * cache: a missing or unreadable file just means starting empty, as before.
 */
class LauncherStateCache(context: Context) {
    private val dir = File(context.cacheDir, "launcher_state")
    private val appsFile = File(dir, "apps.bin")
    private val booksFile = File(dir, "books.json")
    private val packIconsFile = File(dir, "pack_icons.bin")

    fun readApps(): List<AppInfo> = read(appsFile) { input ->
        List(input.readInt()) {
            val packageName = input.readUTF()
            val activityName = input.readUTF()
            val label = input.readUTF()
            val category = input.readInt()
            AppInfo(packageName, activityName, label, readIcon(input), category)
        }
    } ?: emptyList()

    fun writeApps(apps: List<AppInfo>) = write(appsFile) { out ->
        out.writeInt(apps.size)
        apps.forEach { app ->
            out.writeUTF(app.packageName)
            out.writeUTF(app.activityName)
            out.writeUTF(app.label)
            out.writeInt(app.category)
            writeIcon(out, app.icon)
        }
    }

    /** Pack icons for the given packs (main first, then fallback); empty if they were for other packs. */
    fun readPackIcons(packs: List<String>): Map<String, ImageBitmap> = read(packIconsFile) { input ->
        if (input.readUTF() != packs.joinToString("|")) return@read emptyMap()
        (0 until input.readInt()).mapNotNull {
            val key = input.readUTF()
            readIcon(input)?.let { key to it }
        }.toMap()
    } ?: emptyMap()

    fun writePackIcons(packs: List<String>, icons: Map<String, ImageBitmap>) = write(packIconsFile) { out ->
        out.writeUTF(packs.joinToString("|"))
        out.writeInt(icons.size)
        icons.forEach { (key, icon) ->
            out.writeUTF(key)
            writeIcon(out, icon)
        }
    }

    fun readBooks(): List<Book> = runCatching {
        if (!booksFile.isFile) return emptyList()
        val array = JSONArray(booksFile.readText())
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            Book(
                filePath = o.getString("path"),
                title = o.getString("title"),
                author = o.optString("author"),
                coverPath = o.optString("cover").takeIf { it.isNotEmpty() && File(it).isFile },
                lastModified = o.optLong("modified")
            )
        }
    }.getOrDefault(emptyList())

    fun writeBooks(books: List<Book>) = runCatching {
        dir.mkdirs()
        val json = JSONArray(books.map { book ->
            JSONObject().put("path", book.filePath).put("title", book.title).put("author", book.author)
                .put("cover", book.coverPath ?: "").put("modified", book.lastModified)
        }).toString()
        val temp = File(booksFile.path + ".tmp")
        temp.writeText(json)
        temp.renameTo(booksFile)
    }

    private fun <T> read(file: File, block: (DataInputStream) -> T): T? = runCatching {
        if (!file.isFile) return null
        DataInputStream(file.inputStream().buffered()).use { input ->
            if (input.readInt() != VERSION) return null
            block(input)
        }
    }.getOrNull()

    private fun write(file: File, block: (DataOutputStream) -> Unit) {
        runCatching {
            dir.mkdirs()
            val temp = File(file.path + ".tmp")
            DataOutputStream(temp.outputStream().buffered()).use { out ->
                out.writeInt(VERSION)
                block(out)
            }
            temp.renameTo(file)
        }
    }

    private fun readIcon(input: DataInputStream): ImageBitmap? {
        val size = input.readInt()
        if (size <= 0) return null
        val bytes = ByteArray(size).also { input.readFully(it) }
        return BitmapFactory.decodeByteArray(bytes, 0, size)?.asImageBitmap()
    }

    private fun writeIcon(out: DataOutputStream, icon: ImageBitmap?) {
        val bytes = icon?.let {
            ByteArrayOutputStream().also { buffer -> it.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, buffer) }.toByteArray()
        }
        out.writeInt(bytes?.size ?: 0)
        bytes?.let(out::write)
    }

    companion object {
        private const val VERSION = 1

        /** True when two app lists would draw the same: same apps, labels and icon pixels. */
        fun sameApps(a: List<AppInfo>, b: List<AppInfo>): Boolean = a.size == b.size && a.indices.all { i ->
            val x = a[i]
            val y = b[i]
            x.packageName == y.packageName && x.activityName == y.activityName && x.label == y.label &&
                x.category == y.category && sameIcon(x.icon, y.icon)
        }

        fun sameIcons(a: Map<String, ImageBitmap>, b: Map<String, ImageBitmap>): Boolean =
            a.keys == b.keys && a.all { (key, icon) -> sameIcon(icon, b[key]) }

        private fun sameIcon(a: ImageBitmap?, b: ImageBitmap?): Boolean = when {
            a === b -> true
            a == null || b == null -> false
            else -> a.asAndroidBitmap().sameAs(b.asAndroidBitmap())
        }
    }
}
