package com.crosspoint.launcher.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.crosspoint.launcher.data.SettingsRepository
import kotlinx.coroutines.flow.map

/** Full-screen black-then-white flash: the ebc driver redraws the whole panel, clearing e-ink ghosting. */
object ScreenRefresh {
    // Each colour must stay up long enough for the panel to finish a full update (the S4 driver reports ~570 ms).
    private const val FLASH_MS = 600L

    fun flash(activity: Activity) {
        val root = activity.window.decorView as ViewGroup
        val overlay = View(activity).apply { setBackgroundColor(Color.BLACK) }
        root.addView(overlay, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        overlay.postDelayed({ overlay.setBackgroundColor(Color.WHITE) }, FLASH_MS)
        overlay.postDelayed({ root.removeView(overlay) }, FLASH_MS * 2)
    }

    /**
     * Full black→[color] refresh, then run [action] (e.g. launch an app) onto the clean frame. White
     * is the default; a fully transparent [color] skips the flash and runs [action] right away.
     *
     * The overlay must cover home for the WHOLE launch — including a slow cold start. Removing it on a
     * timer re-exposes home before the new app paints, and the app then draws over that fresh home image
     * (the ghosting bug). So we keep white covering home until the launcher is actually shown again
     * (its next onResume after pausing for the launched app), never on a timer.
     */
    fun flashThen(activity: Activity, action: () -> Unit, color: Int = Color.WHITE) {
        if (Color.alpha(color) == 0) { action(); return }
        val root = activity.window.decorView as ViewGroup
        val overlay = View(activity).apply { setBackgroundColor(Color.BLACK) }
        root.addView(overlay, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val remove = { if (overlay.parent != null) root.removeView(overlay) }
        // Black settles a full GC, then the flash color clears it; launch after that update has finished.
        overlay.postDelayed({ overlay.setBackgroundColor(color) }, FLASH_MS)
        overlay.postDelayed({ action() }, FLASH_MS * 2)

        val lifecycle = (activity as? LifecycleOwner)?.lifecycle
        if (lifecycle != null) {
            lifecycle.addObserver(object : DefaultLifecycleObserver {
                private var paused = false // the immediate catch-up onResume (still foreground) must not remove it
                override fun onPause(owner: LifecycleOwner) { paused = true }
                override fun onResume(owner: LifecycleOwner) {
                    if (paused) { remove(); owner.lifecycle.removeObserver(this) }
                }
            })
        }
        // Backstop: if the launch never backgrounds the launcher (e.g. the app is missing), don't strand white.
        overlay.postDelayed(remove, FLASH_MS * 2 + 8000)
    }
}

/** Double-tap to run [ScreenRefresh.flash], when Settings > Launcher > Double-Tap Header to Refresh is on. */
fun Modifier.doubleTapToRefresh(): Modifier = composed {
    val context = LocalContext.current
    val enabled by remember { SettingsRepository(context).settings.map { it.doubleTapRefresh } }.collectAsState(false)
    if (!enabled) this
    else pointerInput(Unit) { detectTapGestures(onDoubleTap = { context.activity()?.let(ScreenRefresh::flash) }) }
}

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}
