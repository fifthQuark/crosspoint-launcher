package com.crosspoint.launcher.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import com.crosspoint.launcher.ui.theme.CrossPointTheme
import com.crosspoint.launcher.util.HomeBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Draws the user's Home background behind this element, rendered at its exact pixel size so
 * dithered images stay sharp. [version] changes whenever a new image is chosen; 0 means none.
 */
@Composable
fun Modifier.homeBackground(version: Long): Modifier {
    if (version <= 0L) return this
    val context = LocalContext.current
    val mode = CrossPointTheme.imageColorMode
    var size by remember { mutableStateOf(IntSize.Zero) }
    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(version, mode, size) {
        if (size.width > 0 && size.height > 0) {
            image = withContext(Dispatchers.Default) {
                HomeBackground.render(context, size.width, size.height, mode)?.asImageBitmap()
            }
        }
    }
    return this
        .onSizeChanged { size = it }
        .drawBehind { image?.let { drawImage(it) } }
}
