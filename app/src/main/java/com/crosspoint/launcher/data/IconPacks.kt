package com.crosspoint.launcher.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.util.DisplayMetrics
import android.util.Xml
import androidx.compose.ui.graphics.ImageBitmap
import com.crosspoint.launcher.model.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser

/** An installed third-party icon pack (the ADW/Nova "appfilter" format most packs ship). */
data class IconPack(val packageName: String, val label: String)

class IconPackRepository(private val context: Context) {
    private val pm: PackageManager get() = context.packageManager
    private val iconSize = (context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager)
        .let { if (it.isLowRamDevice || it.memoryClass <= 128) 48 else 96 }

    suspend fun installedPacks(): List<IconPack> = withContext(Dispatchers.IO) {
        PACK_ACTIONS.flatMap { action ->
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(Intent(action), PackageManager.GET_META_DATA)
        }
            .map { it.activityInfo.packageName }
            .distinct()
            .mapNotNull { pkg ->
                runCatching { IconPack(pkg, pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()) }.getOrNull()
            }
            .sortedBy { it.label.lowercase() }
    }

    /** Icons from [packPackage] keyed by "package/activity"; apps the pack doesn't cover are absent. */
    suspend fun loadIcons(packPackage: String, apps: List<AppInfo>): Map<String, ImageBitmap> = withContext(Dispatchers.IO) {
        val res = runCatching { pm.getResourcesForApplication(packPackage) }.getOrNull() ?: return@withContext emptyMap()
        val filter = runCatching { readAppFilter(res, packPackage) }.getOrDefault(emptyMap())
        if (filter.isEmpty()) return@withContext emptyMap()
        apps.mapNotNull { app ->
            val drawableName = filter["${app.packageName}/${app.activityName}"] ?: return@mapNotNull null
            val bitmap = runCatching { loadDrawable(res, packPackage, drawableName)?.toImageBitmap(iconSize) }.getOrNull()
            bitmap?.let { "${app.packageName}/${app.activityName}" to it }
        }.toMap()
    }

    /** Every icon name [packPackage] maps an app to, for choosing an icon by hand. */
    suspend fun iconNames(packPackage: String): List<String> = withContext(Dispatchers.IO) {
        val res = runCatching { pm.getResourcesForApplication(packPackage) }.getOrNull() ?: return@withContext emptyList()
        runCatching { readAppFilter(res, packPackage) }.getOrDefault(emptyMap()).values.distinct().sorted()
    }

    /** One icon by [spec] ("pack:<package>:<drawable>"), cached; null if the pack or icon is gone. */
    fun loadSpec(spec: String): ImageBitmap? {
        PackIconCache.get(spec)?.let { return it }
        val (pkg, name) = parseSpec(spec) ?: return null
        val res = runCatching { pm.getResourcesForApplication(pkg) }.getOrNull() ?: return null
        return runCatching { loadDrawable(res, pkg, name)?.toImageBitmap(iconSize) }.getOrNull()
            ?.also { PackIconCache.put(spec, it) }
    }

    private fun loadDrawable(res: Resources, pkg: String, name: String): android.graphics.drawable.Drawable? {
        @Suppress("DiscouragedApi")
        val id = res.getIdentifier(name, "drawable", pkg).takeIf { it != 0 }
            ?: res.getIdentifier(name, "mipmap", pkg).takeIf { it != 0 }
            ?: return null
        return res.getDrawableForDensity(id, DisplayMetrics.DENSITY_XXHIGH, null)
    }

    /** Maps "package/activity" to the pack's drawable name, from res/xml or assets appfilter.xml. */
    private fun readAppFilter(res: Resources, pkg: String): Map<String, String> {
        @Suppress("DiscouragedApi")
        val xmlId = res.getIdentifier("appfilter", "xml", pkg)
        val parser: XmlPullParser = if (xmlId != 0) res.getXml(xmlId) else {
            Xml.newPullParser().apply { setInput(res.assets.open("appfilter.xml"), null) }
        }
        return parseAppFilter(parser)
    }

    companion object {
        /** An app icon override that points into an icon pack rather than at a Lucide icon. */
        fun spec(packPackage: String, drawable: String) = "pack:$packPackage:$drawable"
        fun isSpec(value: String) = value.startsWith("pack:")
        fun parseSpec(spec: String): Pair<String, String>? =
            spec.removePrefix("pack:").split(':', limit = 2).takeIf { isSpec(spec) && it.size == 2 }?.let { it[0] to it[1] }

        // Icons picked by hand from packs, shared by every screen that shows them.
        private val PackIconCache = android.util.LruCache<String, ImageBitmap>(256)
        fun peekSpec(spec: String): ImageBitmap? = PackIconCache.get(spec)

        private val PACK_ACTIONS = listOf(
            "org.adw.launcher.THEMES",
            "com.novalauncher.THEME",
            "com.teslacoilsw.launcher.THEME",
            "com.anddoes.launcher.THEME",
            "com.gau.go.launcherex.theme"
        )

        /** Parses `<item component="ComponentInfo{pkg/cls}" drawable="name"/>` entries. */
        fun parseAppFilter(parser: XmlPullParser): Map<String, String> {
            val map = HashMap<String, String>()
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType != XmlPullParser.START_TAG || parser.name != "item") continue
                val component = parser.getAttributeValue(null, "component") ?: continue
                val drawable = parser.getAttributeValue(null, "drawable")?.takeIf { it.isNotBlank() } ?: continue
                val inner = component.removePrefix("ComponentInfo{").removeSuffix("}")
                val pkg = inner.substringBefore('/', "")
                val cls = inner.substringAfter('/', "")
                if (pkg.isEmpty() || cls.isEmpty()) continue
                // Keep the first mapping, as launchers do when a pack lists an app twice.
                map.putIfAbsent("$pkg/${if (cls.startsWith(".")) pkg + cls else cls}", drawable)
            }
            return map
        }
    }
}
