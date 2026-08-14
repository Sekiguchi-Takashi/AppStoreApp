package com.appathy.appstore

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

object AppIcons {
    private val cache = mutableMapOf<String, ImageBitmap?>()

    fun of(context: Context, app: StoreApp): ImageBitmap? {
        cache[app.id]?.let { return it }
        if (cache.containsKey(app.id)) return null
        val info = Catalog.installedPackage(context, app)
        val bmp = if (info == null) null else runCatching {
            toBitmap(context.packageManager.getApplicationIcon(info.packageName))
        }.getOrNull()?.asImageBitmap()
        cache[app.id] = bmp
        return bmp
    }

    private fun toBitmap(d: Drawable): Bitmap {
        if (d is BitmapDrawable && d.bitmap != null) return d.bitmap
        val w = if (d.intrinsicWidth > 0) d.intrinsicWidth else 96
        val h = if (d.intrinsicHeight > 0) d.intrinsicHeight else 96
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        d.setBounds(0, 0, canvas.width, canvas.height)
        d.draw(canvas)
        return bmp
    }
}
