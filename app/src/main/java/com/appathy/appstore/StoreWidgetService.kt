package com.appathy.appstore

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService

class StoreWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        StoreWidgetFactory(applicationContext)
}

class StoreWidgetFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {

    private var items: List<WidgetItem> = emptyList()

    override fun onCreate() {}

    override fun onDataSetChanged() {
        items = WidgetCache.load(context).filter { isInstalled(it.packageName) }
    }

    private fun isInstalled(pkg: String): Boolean {
        if (pkg.isBlank()) return false
        return runCatching { context.packageManager.getPackageInfo(pkg, 0) }.isSuccess
    }

    private fun iconOf(pkg: String): android.graphics.Bitmap? = runCatching {
        val d = context.packageManager.getApplicationIcon(pkg)
        val w = if (d.intrinsicWidth > 0) d.intrinsicWidth else 96
        val h = if (d.intrinsicHeight > 0) d.intrinsicHeight else 96
        val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        d.setBounds(0, 0, canvas.width, canvas.height)
        d.draw(canvas)
        bmp
    }.getOrNull()

    override fun onDestroy() {}

    override fun getCount(): Int = items.size

    override fun getViewAt(position: Int): RemoteViews {
        val item = items[position]
        val views = RemoteViews(context.packageName, R.layout.widget_item)
        views.setTextViewText(R.id.item_name, item.name)

        val icon = iconOf(item.packageName)
        if (icon != null) {
            views.setImageViewBitmap(R.id.item_icon, icon)
        } else {
            views.setImageViewResource(R.id.item_icon, R.mipmap.ic_launcher)
        }

        val fill = Intent()
            .putExtra(StoreWidget.EXTRA_APP_ID, item.id)
            .putExtra(StoreWidget.EXTRA_PKG, item.packageName)
            .putExtra(StoreWidget.EXTRA_KIND, "open")
        views.setOnClickFillInIntent(R.id.item_open, fill)
        views.setOnClickFillInIntent(R.id.item_name, fill)
        views.setOnClickFillInIntent(R.id.item_icon, fill)
        return views
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = true
}
