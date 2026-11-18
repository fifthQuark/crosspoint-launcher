package com.crosspoint.launcher.ui.settings

import android.appwidget.AppWidgetManager
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crosspoint.launcher.LauncherViewModel
import com.crosspoint.launcher.data.LauncherSettings
import com.crosspoint.launcher.model.AppInfo
import com.crosspoint.launcher.model.SwipePage
import com.crosspoint.launcher.model.SwipePageType
import com.crosspoint.launcher.ui.components.BodyText
import com.crosspoint.launcher.ui.components.HeaderIconButton
import com.crosspoint.launcher.ui.components.Pressable
import com.crosspoint.launcher.ui.components.key
import com.crosspoint.launcher.ui.icons.LucideIcon
import com.crosspoint.launcher.ui.theme.CrossPointTheme

/**
 * Settings > Swipe Pages: the ordered pages on each side of Home, reached by swiping right or left.
 * [onChooseApp] opens an app picker for a "Launch an App" page.
 */
internal fun LazyListScope.swipePagesSettings(
    settings: LauncherSettings,
    apps: List<AppInfo>,
    vm: LauncherViewModel,
    onChooseApp: (pageId: String) -> Unit,
    onAddWidget: (pageId: String) -> Unit
) {
    item {
        BodyText("Swipe on Home to go through each side's pages in order, and the other way to come back.",
            muted = true, size = 14, maxLines = Int.MAX_VALUE, modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp))
    }
    for (left in listOf(false, true)) {
        val side = settings.swipePages.filter { it.left == left }
        val name = if (left) "Swipe Left" else "Swipe Right"
        item(key = "side:$left") {
            BodyText(name, weight = FontWeight.Bold, size = 16, modifier = Modifier.padding(top = 16.dp, start = 6.dp))
        }
        if (side.isEmpty()) item(key = "empty:$left") {
            BodyText("No pages. Swiping ${if (left) "left" else "right"} on Home does nothing.", muted = true, size = 14,
                maxLines = Int.MAX_VALUE, modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp))
        }
        items(side, key = { "page:${it.id}" }) { page ->
            SwipePageCard(page, side.indexOf(page), side.size, apps, vm, onChooseApp, onAddWidget)
        }
        item(key = "add-title:$left") {
            BodyText("Add a Page to $name", weight = FontWeight.Bold, size = 14, muted = true, modifier = Modifier.padding(top = 8.dp, start = 6.dp))
        }
        items(SwipePageType.entries, key = { "add:$left:${it.id}" }) { type ->
            ActionRow(type.label, type.description, type.icon) { vm.addSwipePage(type, left) }
        }
    }
}

@Composable
private fun SwipePageCard(
    page: SwipePage,
    index: Int,
    count: Int,
    apps: List<AppInfo>,
    vm: LauncherViewModel,
    onChooseApp: (String) -> Unit,
    onAddWidget: (String) -> Unit
) {
    val colors = CrossPointTheme.colors
    Column(
        Modifier.fillMaxWidth().padding(vertical = 6.dp)
            .border(1.dp, colors.divider, RoundedCornerShape(CrossPointTheme.metrics.cornerRadius))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LucideIcon(page.type.icon, Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            BodyText("${index + 1}. ${page.type.label}", weight = FontWeight.SemiBold, size = 16, modifier = Modifier.weight(1f))
            if (index > 0) HeaderIconButton("chevron_up", { vm.moveSwipePage(page.id, -1) }, iconSize = 20.dp)
            if (index < count - 1) HeaderIconButton("chevron_down", { vm.moveSwipePage(page.id, 1) }, iconSize = 20.dp)
            // Send the page to the other side of Home.
            HeaderIconButton(if (page.left) "chevron_right" else "chevron_left", { vm.setSwipePageSide(page.id, !page.left) }, iconSize = 20.dp)
            HeaderIconButton("trash_2", { vm.removeSwipePage(page.id) }, iconSize = 20.dp)
        }
        when (page.type) {
            SwipePageType.APP -> {
                val app = apps.firstOrNull { it.key == page.app }
                ActionRow(app?.label ?: "Choose App", if (app == null) "Nothing opens until you choose one" else "Tap to change", "app_window") { onChooseApp(page.id) }
            }
            SwipePageType.RSS -> RssFeedsEditor(page, vm)
            SwipePageType.APP_GRID, SwipePageType.APP_GRID_WIDGETS -> {
                val fits = if (page.rows > 0) page.rows * page.columns else Int.MAX_VALUE
                ActionRow(
                    "Choose Apps",
                    when {
                        page.apps.isEmpty() -> "No apps chosen yet"
                        page.apps.size > fits -> "${page.apps.size} chosen; only $fits fit"
                        else -> "${page.apps.size} chosen"
                    },
                    "layout_grid"
                ) { onChooseApp(page.id) }
                StepperRow("Columns", "${page.columns}",
                    canDecrease = page.columns > 1, canIncrease = page.columns < LauncherSettings.MAX_HOME_COLUMNS,
                    onDecrease = { vm.setSwipePageColumns(page.id, page.columns - 1) },
                    onIncrease = { vm.setSwipePageColumns(page.id, page.columns + 1) })
                StepperRow("Rows", if (page.rows == 0) "Auto" else "${page.rows}",
                    canDecrease = page.rows > 0, canIncrease = page.rows < LauncherSettings.MAX_HOME_ROWS,
                    onDecrease = { vm.setSwipePageRows(page.id, page.rows - 1) },
                    onIncrease = { vm.setSwipePageRows(page.id, page.rows + 1) })
                if (page.type == SwipePageType.APP_GRID_WIDGETS) PageWidgetsEditor(page, vm, onAddWidget)
            }
            SwipePageType.WIDGETS -> PageWidgetsEditor(page, vm, onAddWidget)
            SwipePageType.STATS, SwipePageType.NOTIFICATIONS -> {}
        }
    }
}

/** The widgets hosted on a page, each removable, plus Add Widget. */
@Composable
private fun PageWidgetsEditor(page: SwipePage, vm: LauncherViewModel, onAddWidget: (String) -> Unit) {
    val context = LocalContext.current
    val manager = remember(context) { AppWidgetManager.getInstance(context) }
    page.widgetIds.forEach { id ->
        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            BodyText(manager.getAppWidgetInfo(id)?.loadLabel(context.packageManager) ?: "Widget unavailable",
                size = 15, modifier = Modifier.weight(1f))
            HeaderIconButton("x", { vm.removeSwipePageWidget(page.id, id) }, iconSize = 18.dp)
        }
    }
    ActionRow("Add Widget", "", "plus") { onAddWidget(page.id) }
}

@Composable
private fun RssFeedsEditor(page: SwipePage, vm: LauncherViewModel) {
    val colors = CrossPointTheme.colors
    var url by remember(page.id) { mutableStateOf("") }
    fun add() {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return
        vm.addSwipePageFeed(page.id, if ("://" in trimmed) trimmed else "https://$trimmed")
        url = ""
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        page.feeds.forEach { feed ->
            Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                BodyText(feed, size = 14, maxLines = 2, modifier = Modifier.weight(1f))
                HeaderIconButton("x", { vm.removeSwipePageFeed(page.id, feed) }, iconSize = 18.dp)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = url,
                onValueChange = { url = it },
                singleLine = true,
                textStyle = TextStyle(color = colors.onBackground, fontSize = 15.sp),
                cursorBrush = SolidColor(colors.onBackground),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add() }),
                modifier = Modifier.weight(1f).border(1.dp, colors.divider, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 8.dp),
                decorationBox = { inner ->
                    if (url.isEmpty()) BodyText("Feed URL", muted = true, size = 15)
                    inner()
                }
            )
            Pressable(onClick = ::add) {
                BodyText("Add", weight = FontWeight.SemiBold, size = 15, modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp))
            }
        }
    }
}

/**
 * Chooses the apps on an app grid page: chosen apps first, in page order with move controls,
 * then every other app. Tapping toggles an app.
 */
internal fun LazyListScope.swipePageAppsPicker(page: SwipePage, apps: List<AppInfo>, vm: LauncherViewModel) {
    val byKey = apps.associateBy { it.key }
    val chosen = page.apps.mapNotNull { byKey[it] }
    item { BodyText("On This Page", weight = FontWeight.Bold, size = 14, muted = true, modifier = Modifier.padding(top = 8.dp, start = 6.dp)) }
    if (chosen.isEmpty()) item {
        BodyText("Tap apps below to add them.", muted = true, size = 14, modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp))
    }
    items(chosen, key = { "chosen:${it.key}" }) { app ->
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Pressable(onClick = { vm.toggleSwipePageApp(page.id, app.key) }, modifier = Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    CheckBox(true)
                    Spacer(Modifier.width(14.dp))
                    BodyText(app.label, weight = FontWeight.Medium, size = 16)
                }
            }
            val index = page.apps.indexOf(app.key)
            if (index > 0) HeaderIconButton("chevron_up", { vm.moveSwipePageApp(page.id, app.key, -1) }, iconSize = 18.dp)
            if (index < page.apps.lastIndex) HeaderIconButton("chevron_down", { vm.moveSwipePageApp(page.id, app.key, 1) }, iconSize = 18.dp)
        }
    }
    item { BodyText("Apps", weight = FontWeight.Bold, size = 14, muted = true, modifier = Modifier.padding(top = 12.dp, start = 6.dp)) }
    items(apps.filter { it.key !in page.apps }, key = { "other:${it.key}" }) { app ->
        Pressable(onClick = { vm.toggleSwipePageApp(page.id, app.key) }, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 10.dp, horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                CheckBox(false)
                Spacer(Modifier.width(14.dp))
                BodyText(app.label, weight = FontWeight.Medium, size = 16)
            }
        }
    }
}

/** Lists launchable apps for a "Launch an App" page. */
internal fun LazyListScope.swipePageAppPicker(apps: List<AppInfo>, selected: String?, onPick: (AppInfo) -> Unit) {
    items(apps, key = { "pick:${it.key}" }) { app ->
        SelectRow(app.label, app.packageName, app.key == selected) { onPick(app) }
    }
}
