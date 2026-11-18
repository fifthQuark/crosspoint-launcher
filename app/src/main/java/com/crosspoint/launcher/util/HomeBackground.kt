package com.crosspoint.launcher.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.crosspoint.launcher.model.ImageColorMode
import java.io.File

/**
 * The optional picture behind the Home screen. The chosen image is copied into app storage at
 * screen resolution, then cropped to the Home area and drawn in the current color mode.
 */
object HomeBackground {
    private fun file(context: Context) = File(context.filesDir, "home_background.jpg")

    /** Copies the picked image, bounded to the screen's longer edge. */
    fun save(context: Context, uri: Uri): Boolean {
        val m = context.resources.displayMetrics
        val maxSide = maxOf(m.widthPixels, m.heightPixels)
        val open = { context.contentResolver.openInputStream(uri) ?: error("Image unavailable") }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open().use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val bitmap = open().use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
            ?: return false
        val target = file(context)
        val temp = File(target.path + ".tmp")
        try {
            temp.outputStream().use { if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)) return false }
            return temp.renameTo(target)
        } finally {
            bitmap.recycle()
            temp.delete()
        }
    }

    fun clear(context: Context) {
        file(context).delete()
    }

    /** The background cropped to fill [width]×[height] and converted for [mode], or null if unset. */
    fun render(context: Context, width: Int, height: Int, mode: ImageColorMode): Bitmap? {
        if (width <= 0 || height <= 0) return null
        val source = BitmapFactory.decodeFile(file(context).path) ?: return null
        val cropped = SleepScreen.fillCrop(source, width, height)
        source.recycle()
        return try { Dither.render(cropped, mode) } finally { cropped.recycle() }
    }
}
