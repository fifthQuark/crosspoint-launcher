package com.crosspoint.launcher.ui.pages

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.crosspoint.launcher.data.LauncherSettings
import com.crosspoint.launcher.data.RssItem
import com.crosspoint.launcher.data.RssRepository
import com.crosspoint.launcher.model.AppInfo
import com.crosspoint.launcher.model.SwipePage
import com.crosspoint.launcher.model.SwipePageType
import com.crosspoint.launcher.notifications.NotificationEntry
import com.crosspoint.launcher.notifications.NotificationsService
import com.crosspoint.launcher.ui.components.AppIcon
import com.crosspoint.launcher.ui.components.BodyText
import com.crosspoint.launcher.ui.components.HeaderIconButton
import com.crosspoint.launcher.ui.components.LauncherAppIcon
import com.crosspoint.launcher.ui.components.Pressable
import com.crosspoint.launcher.ui.components.ScreenHeader
import com.crosspoint.launcher.ui.components.ScrollList
import com.crosspoint.launcher.ui.components.key
import com.crosspoint.launcher.ui.components.verticalScrollWithIndicator
import com.crosspoint.launcher.ui.home.CROSSPOINT_WIDGET_HOST_ID
import com.crosspoint.launcher.ui.icons.LucideIcon
import com.crosspoint.launcher.ui.theme.CrossPointTheme

/** Callbacks a swipe page needs from the launcher. */
class SwipePageActions(
    val onLaunchApp: (String, String) -> Unit,
    val onAppInfo: (String) -> Unit,
    val onAddWidget: (pageId: String) -> Unit,
    val onRemoveWidget: (pageId: String, widgetId: Int) -> Unit,
    val onOpenSettings: () -> Unit,
    val onBack: () -> Unit
)

/** The page at one position in the swipe-right sequence. */
@Composable
fun SwipePageScreen(
    page: SwipePage,
    position: String,
    apps: List<AppInfo>,
    settings: LauncherSettings,
    actions: SwipePageActions
) {
    when (page.type) {
        SwipePageType.STATS -> com.crosspoint.launcher.ui.stats.StatsScreen(onBack = actions.onBack)
        SwipePageType.APP -> AppLaunchPage(page, position, apps, actions)
        SwipePageType.RSS -> RssPage(page, position, actions)
        SwipePageType.NOTIFICATIONS -> NotificationsPage(position, apps, actions)
        SwipePageType.APP_GRID, SwipePageType.APP_GRID_WIDGETS -> AppGridPage(page, position, apps, settings, actions)
        SwipePageType.WIDGETS -> WidgetsPage(page, position, actions)
    }
}

@Composable
private fun PageScaffold(
    title: String,
    position: String,
    onBack: () -> Unit,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
    content: @Composable () -> Unit
) {
    val topPad = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Column(Modifier.fillMaxSize().padding(top = topPad)) {
        ScreenHeader(title, onBack) {
            BodyText(position, muted = true, size = 13, modifier = Modifier.padding(horizontal = 6.dp))
            actions()
        }
        Box(Modifier.fillMaxSize()) { content() }
    }
}

@Composable
private fun EmptyMessage(text: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        BodyText(text, muted = true, size = 16, maxLines = Int.MAX_VALUE, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            Pressable(onClick = onAction) {
                BodyText(action, weight = FontWeight.SemiBold, size = 16, modifier = Modifier.padding(12.dp))
            }
        }
    }
}

/* ----------------------------- Launch an app ----------------------------- */

@Composable
private fun AppLaunchPage(page: SwipePage, position: String, apps: List<AppInfo>, actions: SwipePageActions) {
    val app = apps.firstOrNull { it.key == page.app }
    PageScaffold(app?.label ?: "Launch an App", position, actions.onBack) {
        if (app == null) {
            EmptyMessage("Choose an app for this page in Settings > Swipe Pages.", "Open Settings", actions.onOpenSettings)
        } else {
            EmptyMessage("Swipe right past this page to continue.", "Open ${app.label}") {
                actions.onLaunchApp(app.packageName, app.activityName)
            }
        }
    }
}

/* ----------------------------- App grid ----------------------------- */

@Composable
private fun AppGridPage(page: SwipePage, position: String, apps: List<AppInfo>, settings: LauncherSettings, actions: SwipePageActions) {
    val withWidgets = page.type == SwipePageType.APP_GRID_WIDGETS
    val byKey = remember(apps) { apps.associateBy { it.key } }
    val chosen = page.apps.mapNotNull { byKey[it] }
    PageScaffold(page.type.label, position, actions.onBack, actions = {
        if (withWidgets) HeaderIconButton("plus", { actions.onAddWidget(page.id) }, iconSize = 22.dp)
    }) {
        val m = CrossPointTheme.metrics
        Column(Modifier.fillMaxSize().padding(horizontal = m.contentSidePadding, vertical = 8.dp)) {
            if (withWidgets) {
                page.widgetIds.forEach { id -> HostedWidget(id, onRemove = { actions.onRemoveWidget(page.id, id) }) }
                if (page.widgetIds.isEmpty()) {
                    Pressable(onClick = { actions.onAddWidget(page.id) }, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            LucideIcon("plus", Modifier.size(22.dp))
                            Spacer(Modifier.width(12.dp))
                            BodyText("Add a widget", weight = FontWeight.SemiBold, size = 16)
                        }
                    }
                }
            }
            if (chosen.isEmpty()) {
                EmptyMessage("Choose the apps for this page in Settings > Swipe Pages.", "Open Settings", actions.onOpenSettings)
            } else {
                AppPageGrid(chosen, page.columns, page.rows, settings, actions, Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
}

/** A page of hosted widgets only; it scrolls when they don't all fit. */
@Composable
private fun WidgetsPage(page: SwipePage, position: String, actions: SwipePageActions) {
    PageScaffold(page.type.label, position, actions.onBack, actions = {
        HeaderIconButton("plus", { actions.onAddWidget(page.id) }, iconSize = 22.dp)
    }) {
        if (page.widgetIds.isEmpty()) {
            EmptyMessage("No widgets on this page yet.", "Add a Widget") { actions.onAddWidget(page.id) }
        } else {
            Column(
                Modifier.fillMaxSize().verticalScrollWithIndicator(rememberScrollState())
                    .padding(horizontal = CrossPointTheme.metrics.contentSidePadding, vertical = 8.dp)
            ) {
                page.widgetIds.forEach { id -> HostedWidget(id, onRemove = { actions.onRemoveWidget(page.id, id) }) }
            }
        }
    }
}

/**
 * A fixed page of apps: [columns] across and [rows] down (0 = as many rows as the apps need),
 * sized to the space available so the page never scrolls.
 */
@Composable
private fun AppPageGrid(apps: List<AppInfo>, columns: Int, rows: Int, settings: LauncherSettings, actions: SwipePageActions, modifier: Modifier) {
    val cols = columns.coerceAtLeast(1)
    val rowCount = if (rows > 0) rows else ((apps.size + cols - 1) / cols).coerceAtLeast(1)
    val shown = apps.take(rowCount * cols)
    BoxWithConstraints(modifier) {
        // Fixed rows share the page; automatic rows keep a comfortable size and leave space below.
        val cellHeight = if (rows > 0) maxHeight / rowCount else minOf(maxHeight / rowCount, 120.dp)
        val iconSize = (cellHeight * 0.42f).coerceIn(20.dp, 56.dp)
        Column {
            shown.chunked(cols).forEach { rowApps ->
                Row(Modifier.fillMaxWidth().height(cellHeight)) {
                    rowApps.forEach { app ->
                        Pressable(
                            onClick = { actions.onLaunchApp(app.packageName, app.activityName) },
                            onLongClick = { actions.onAppInfo(app.packageName) },
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        ) {
                            Column(
                                Modifier.fillMaxSize().padding(4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                LauncherAppIcon(app, settings, size = iconSize)
                                Spacer(Modifier.height(4.dp))
                                BodyText(app.label, size = 12, maxLines = 2, textAlign = TextAlign.Center)
                            }
                        }
                    }
                    repeat(cols - rowApps.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** A hosted app widget at its provider's default height, with a remove control above it. */
@Composable
private fun HostedWidget(widgetId: Int, onRemove: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val host = remember(context) { AppWidgetHost(context.applicationContext, CROSSPOINT_WIDGET_HOST_ID) }
    val manager = remember(context) { AppWidgetManager.getInstance(context) }
    DisposableEffect(host) {
        host.startListening()
        onDispose { host.stopListening() }
    }
    val info = manager.getAppWidgetInfo(widgetId)
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BodyText(info?.loadLabel(context.packageManager) ?: "Widget unavailable", muted = true, size = 12, modifier = Modifier.weight(1f))
            HeaderIconButton("x", onRemove, iconSize = 16.dp)
        }
        if (info != null) {
            val height = with(density) { info.minHeight.toDp() }.coerceIn(72.dp, 420.dp)
            AndroidView(
                modifier = Modifier.fillMaxWidth().height(height),
                factory = { ctx -> host.createView(ctx, widgetId, info).apply { setAppWidget(widgetId, info) } },
                update = { view -> view.setAppWidget(widgetId, manager.getAppWidgetInfo(widgetId)) }
            )
        }
    }
}

/* ----------------------------- Notifications ----------------------------- */

@Composable
private fun NotificationsPage(position: String, apps: List<AppInfo>, actions: SwipePageActions) {
    val context = LocalContext.current
    var hasAccess by remember { mutableStateOf(NotificationsService.hasAccess(context)) }
    // Re-check after returning from the system notification-access screen.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) hasAccess = NotificationsService.hasAccess(context)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val notifications by NotificationsService.notifications.collectAsState()
    val labels = remember(apps) { apps.associate { it.packageName to it } }
    PageScaffold("Notifications", position, actions.onBack, actions = {
        if (hasAccess && notifications.any { it.clearable }) {
            Pressable(onClick = { NotificationsService.dismissAll() }) {
                BodyText("Clear All", weight = FontWeight.SemiBold, size = 14, modifier = Modifier.padding(horizontal = 6.dp, vertical = 12.dp))
            }
        }
    }) {
        when {
            !hasAccess -> EmptyMessage(
                "Allow notification access so this page can show your notifications.",
                "Allow Access"
            ) { NotificationsService.openAccessSettings(context) }
            notifications.isEmpty() -> EmptyMessage("No notifications")
            else -> ScrollList(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = CrossPointTheme.metrics.contentSidePadding, vertical = 8.dp)
            ) {
                items(notifications, key = { it.key }) { entry ->
                    NotificationRow(entry, labels[entry.packageName])
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(entry: NotificationEntry, app: AppInfo?) {
    val context = LocalContext.current
    Pressable(onClick = { NotificationsService.open(context, entry) }, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 4.dp), verticalAlignment = Alignment.Top) {
            AppIcon(app?.icon, app?.label ?: entry.packageName, size = 28.dp,
                colorFilter = if (CrossPointTheme.imageColorMode == com.crosspoint.launcher.model.ImageColorMode.COLOR) null
                    else androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(0f) }))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BodyText(app?.label ?: entry.packageName, muted = true, size = 12, modifier = Modifier.weight(1f))
                    BodyText(DateUtils.getRelativeTimeSpanString(entry.postTime).toString(), muted = true, size = 12)
                }
                if (entry.title.isNotBlank()) BodyText(entry.title, weight = FontWeight.SemiBold, size = 16, maxLines = 2)
                if (entry.text.isNotBlank()) BodyText(entry.text, size = 14, maxLines = 4)
            }
            if (entry.clearable) HeaderIconButton("x", { NotificationsService.dismiss(entry) }, iconSize = 18.dp)
        }
    }
}

/* ----------------------------- RSS reader ----------------------------- */

@Composable
private fun RssPage(page: SwipePage, position: String, actions: SwipePageActions) {
    val context = LocalContext.current
    val repo = remember { RssRepository(context.applicationContext) }
    var items by remember(page.feeds) { mutableStateOf<List<RssItem>>(emptyList()) }
    var failed by remember(page.feeds) { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember(page.feeds) { mutableStateOf(true) }
    var refresh by remember { mutableIntStateOf(0) }
    var reading by remember { mutableStateOf<RssItem?>(null) }
    LaunchedEffect(page.feeds, refresh) {
        loading = true
        // Show cached articles straight away, then fetch fresh ones.
        if (refresh == 0) repo.load(page.feeds, refresh = false).let { (cached, _) -> if (cached.isNotEmpty()) items = cached }
        val (fresh, errors) = repo.load(page.feeds, refresh = true)
        items = fresh
        failed = errors
        loading = false
    }

    reading?.let { article ->
        BackHandler { reading = null }
        RssArticle(article, position) { reading = null }
        return
    }
    PageScaffold("RSS Reader", position, actions.onBack, actions = {
        HeaderIconButton("rotate_cw", { if (!loading) refresh++ }, iconSize = 20.dp)
    }) {
        when {
            page.feeds.isEmpty() -> EmptyMessage("Add feeds for this page in Settings > Swipe Pages.", "Open Settings", actions.onOpenSettings)
            items.isEmpty() && loading -> EmptyMessage("Loading…")
            items.isEmpty() -> EmptyMessage(if (failed.isNotEmpty()) "Couldn't load your feeds." else "No articles yet.", "Try Again") { refresh++ }
            else -> ScrollList(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = CrossPointTheme.metrics.contentSidePadding, vertical = 8.dp)
            ) {
                if (failed.isNotEmpty()) item {
                    BodyText("Couldn't refresh: ${failed.joinToString()}", muted = true, size = 12, maxLines = 3, modifier = Modifier.padding(vertical = 6.dp))
                }
                items(items, key = { it.link.ifEmpty { it.title } + it.published }) { item ->
                    Pressable(onClick = { reading = item }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 4.dp)) {
                            BodyText(item.title, weight = FontWeight.SemiBold, size = 17, maxLines = 3)
                            Spacer(Modifier.height(2.dp))
                            BodyText(listOfNotNull(item.feedTitle.ifBlank { null }, item.published.takeIf { it > 0 }?.let {
                                DateUtils.getRelativeTimeSpanString(it).toString()
                            }).joinToString(" · "), muted = true, size = 12)
                            if (item.summary.isNotBlank()) BodyText(item.summary, size = 14, maxLines = 2, muted = true)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RssArticle(item: RssItem, position: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val m = CrossPointTheme.metrics
    PageScaffold(item.feedTitle.ifBlank { "Article" }, position, onBack, actions = {
        if (item.link.isNotBlank()) HeaderIconButton("globe", {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }, iconSize = 20.dp)
    }) {
        Column(
            Modifier.fillMaxSize().verticalScrollWithIndicator(rememberScrollState())
                .padding(horizontal = m.contentSidePadding, vertical = 16.dp)
        ) {
            BodyText(item.title, weight = FontWeight.Bold, size = 22, maxLines = Int.MAX_VALUE)
            if (item.published > 0) {
                Spacer(Modifier.height(6.dp))
                BodyText(DateUtils.formatDateTime(context, item.published,
                    DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_YEAR), muted = true, size = 13)
            }
            Spacer(Modifier.height(16.dp))
            BodyText(item.summary.ifBlank { "No preview. Open the article in your browser." }, size = 17, maxLines = Int.MAX_VALUE)
            if (item.link.isNotBlank()) {
                Spacer(Modifier.height(24.dp))
                Pressable(onClick = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }) {
                    Row(Modifier.padding(vertical = 10.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        LucideIcon("globe", Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        BodyText("Open in Browser", weight = FontWeight.SemiBold, size = 16)
                    }
                }
            }
        }
    }
}
