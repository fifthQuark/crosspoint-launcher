package com.crosspoint.launcher.model

import androidx.compose.ui.graphics.ImageBitmap

/** Visual layout themes, mirroring the CrossPoint reader "Lyra" family plus two new ones. */
enum class ThemeType(val id: String, val label: String, val description: String) {
    LYRA("lyra", "Lyra", "One featured cover with a two-column icon menu"),
    LYRA_3_COVERS("lyra3", "Lyra Covers 3", "Three recent covers in a row"),
    CAROUSEL("carousel", "Carousel", "Swipeable cover carousel, centered focus"),
    MINIMAL("minimal", "Minimal", "No covers — a Niagara-style pinned list");

    companion object {
        fun from(id: String?): ThemeType = entries.firstOrNull { it.id == id } ?: LYRA
    }
}

/** Light/dark selection for the black & white e-ink palette. */
enum class AppearanceMode(val id: String, val label: String) {
    LIGHT("light", "Light"),
    DARK("dark", "Dark"),
    SYSTEM("system", "Follow System");

    companion object {
        fun from(id: String?): AppearanceMode = entries.firstOrNull { it.id == id } ?: LIGHT
    }
}

enum class LauncherIconStyle(val id: String, val label: String) {
    ICONS("icons", "Icons"),
    DOTS("dots", "Dots"),
    SQUARES("squares", "Squares"),
    CIRCLES("circles", "Circles"),
    /** The app's own icon, or its icon from an installed icon pack. */
    ICON_PACK("iconpack", "Icon Pack");

    companion object {
        fun from(id: String?): LauncherIconStyle = entries.firstOrNull { it.id == id } ?: ICONS
    }
}

enum class CoverSlotMode(val id: String, val label: String) {
    BOOKS("books", "Books"),
    WIDGET("widget", "Widget");

    companion object {
        fun from(id: String?): CoverSlotMode = entries.firstOrNull { it.id == id } ?: BOOKS
    }
}

/**
 * How book covers and other pictures are drawn. Dithering suits black & white e-ink panels;
 * grayscale and color are for panels (or phones) that can show them.
 */
enum class ImageColorMode(val id: String, val label: String, val description: String) {
    DITHERED("dithered", "Black & White, Dithered", "Sharp dot patterns for e-ink"),
    GRAYSCALE("grayscale", "Grayscale", "Smooth shades of gray"),
    COLOR("color", "Color", "Original colors");

    companion object {
        fun from(id: String?): ImageColorMode = entries.firstOrNull { it.id == id } ?: DITHERED
    }
}

/** What the e-ink sleep screen shows. BOOK_COVER tracks the most recent book automatically. */
enum class SleepScreenMode(val id: String, val label: String) {
    OFF("off", "Off"),
    BOOK_COVER("book", "Book Cover"),
    CUSTOM("custom", "Custom");

    companion object {
        fun from(id: String?): SleepScreenMode = entries.firstOrNull { it.id == id } ?: OFF
    }
}

/** A parsed local EPUB book. [coverPath] points to a cached cover image file, if extracted. */
data class Book(
    val filePath: String,
    val title: String,
    val author: String,
    val coverPath: String?,
    val lastModified: Long
)

/** An installed, launchable Android application. */
data class AppInfo(
    val packageName: String,
    val activityName: String,
    val label: String,
    val icon: ImageBitmap?,
    val category: Int = -1
)

data class ExternalReadingState(
    val source: String,
    val packageName: String,
    val title: String,
    val author: String = "",
    val coverPath: String? = null,
    val shortcutId: String? = null,
    val launchUri: String? = null,
    val updatedAt: Long = 0L
)

/** Which vertical swipe gesture (if any) opens the app-search overlay on the home screen. */
enum class SearchGesture(val id: String, val label: String) {
    OFF("off", "Off"),
    SWIPE_UP("up", "Swipe Up"),
    SWIPE_DOWN("down", "Swipe Down"),
    BOTH("both", "Swipe Up or Down");

    companion object {
        fun from(id: String?): SearchGesture = entries.firstOrNull { it.id == id } ?: SWIPE_UP
    }
}

/**
 * Home-screen menu entries, mirroring the CrossPoint reader's Lyra home menu. OPDS_BROWSER is
 * shown automatically when at least one OPDS server is configured (as the reader does); APPS is
 * an extra launcher-specific entry the user can enable.
 */
enum class HomeMenuItem(val id: String, val label: String, val icon: String) {
    BROWSE_BOOKS("browse", "Browse Books", "folder"),
    RECENT_BOOKS("recent", "Recent Books", "book"),
    OPDS_BROWSER("opds", "OPDS Browser", "library"),
    FILE_TRANSFER("transfer", "File Transfer", "send_horizontal"),
    APPS("apps", "All Apps", "layout_grid"),
    SETTINGS("settings", "Settings", "settings");

    companion object {
        fun from(id: String): HomeMenuItem? = entries.firstOrNull { it.id == id }
    }
}

/** What a page reached by swiping right from Home shows. */
enum class SwipePageType(val id: String, val label: String, val description: String, val icon: String) {
    STATS("stats", "Reading Stats", "CrossPoint Sync reading activity", "calendar"),
    APP("app", "Launch an App", "Swiping here opens the chosen app", "app_window"),
    RSS("rss", "RSS Reader", "Latest articles from your feeds", "rss"),
    NOTIFICATIONS("notifications", "Notifications", "Your current notifications", "bell"),
    APP_GRID("apps", "App Grid", "A page of apps you choose", "layout_grid"),
    APP_GRID_WIDGETS("apps_widgets", "App Grid with Widgets", "Widgets above apps you choose", "panels_top_left"),
    WIDGETS("widgets", "Widgets", "A page of widgets", "boxes");

    companion object {
        fun from(id: String?): SwipePageType? = entries.firstOrNull { it.id == id }
    }
}

/**
 * One page beside Home, reached by swiping right, or by swiping left when [left] is set. Only the
 * fields for its [type] are used: [app] is a "package/activity" key, [feeds] are RSS/Atom URLs,
 * [apps] are the "package/activity" keys an app grid shows in [columns] × [rows] (0 rows = fit them
 * all), and [widgetIds] are hosted app widgets.
 */
data class SwipePage(
    val id: String,
    val type: SwipePageType,
    val left: Boolean = false,
    val app: String? = null,
    val feeds: List<String> = emptyList(),
    val columns: Int = 4,
    val rows: Int = 0,
    val apps: List<String> = emptyList(),
    val widgetIds: List<Int> = emptyList()
)

/** A configured OPDS catalog server (matches the reader's OpdsServer struct). */
data class OpdsServer(
    val id: String,
    val name: String,
    val url: String,
    val username: String = "",
    val password: String = ""
)

/** A catalog link, including acquisition and lending information. */
data class OpdsLink(
    val href: String,
    val mediaType: String = "",
    val rels: List<String> = emptyList(),
    val title: String = "",
    val properties: org.json.JSONObject = org.json.JSONObject()
) {
    fun hasRel(rel: String) = rel in rels
    val acquisition: String? get() = rels.firstNotNullOfOrNull { rel ->
        when (rel) {
            "http://opds-spec.org/acquisition" -> "acquisition"
            "http://opds-spec.org/acquisition/open-access", "open-access" -> "download"
            "http://opds-spec.org/acquisition/sample", "sample" -> "preview"
            else -> rel.removePrefix("http://opds-spec.org/acquisition/")
                .takeIf { it in listOf("download", "acquisition", "borrow", "buy", "subscribe", "preview") }
        }
    }
}

/** A publication or navigation row shared by OPDS 1.x and 2.0. */
data class OpdsEntry(
    val type: Type,
    val title: String,
    val author: String,
    val href: String,
    val thumbnailHref: String? = null,
    val id: String = "",
    val heading: String = "",
    val description: String = "",
    val metadata: String = "",
    val links: List<OpdsLink> = emptyList(),
    val selfHref: String? = null,
    val detail: String = ""
) {
    enum class Type { NAVIGATION, BOOK }
}

/** A parsed OPDS feed page: entries plus pagination + search metadata. */
data class OpdsFeed(
    val title: String,
    val entries: List<OpdsEntry>,
    val nextUrl: String? = null,
    val prevUrl: String? = null,
    val searchTemplate: String? = null,
    val firstUrl: String? = null,
    val lastUrl: String? = null,
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val url: String = ""
)
