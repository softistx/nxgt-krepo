package com.softistx.material.school

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import com.softistx.material.foundation.theme.ColorMode
import com.softistx.material.school.session.SchoolScreen
import com.softistx.material.school.session.SchoolStart
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File

/** A window size the specs, and the visual bench after them, render at. */
enum class Viewport(
    val width: Int,
    val height: Int,
) {
    Phone(412, 915),
    Desktop(1440, 900),
}

/**
 * Renders one screen of the School app headlessly: no window, no clicks. This is the entry point
 * the visual work builds on — a screen, a size, a theme, a data state, and the frame comes back.
 *
 * Set `SCHOOL_SHOTS_DIR` and every render is also written there as a PNG.
 */
fun renderSchool(
    screen: SchoolScreen,
    viewport: Viewport,
    dark: Boolean,
    start: SchoolStart = SchoolStart(),
): Image {
    val scene = ImageComposeScene(width = viewport.width, height = viewport.height)
    try {
        val mode = if (dark) ColorMode.Dark else ColorMode.Light
        scene.setContent {
            SchoolApp(initialScreen = screen, start = start.copy(colorMode = mode, animated = false))
        }
        var time = 0L
        repeat(8) {
            time += 100_000_000L
            scene.render(time)
        }
        val image = scene.render(time)
        System.getenv("SCHOOL_SHOTS_DIR")?.let { dir -> save(image, dir, screen, viewport, dark, start) }
        return image
    } finally {
        scene.close()
    }
}

private fun save(
    image: Image,
    dir: String,
    screen: SchoolScreen,
    viewport: Viewport,
    dark: Boolean,
    start: SchoolStart,
) {
    val detail = listOfNotNull(start.studentId?.let { "student$it" }, start.conversationId?.let { "thread$it" })
    val name = (listOf(screen.name, viewport.name, if (dark) "dark" else "light", start.data.name) + detail).joinToString("-")
    val folder = File(dir).also { it.mkdirs() }
    File(folder, "$name.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
}

/** How many distinct colours the frame holds: a blank or failed render holds one or two. */
fun distinctColours(image: Image): Int {
    val map = image.toComposeImageBitmap().toPixelMap()
    val seen = HashSet<Int>()
    for (y in 0 until map.height step 3) {
        for (x in 0 until map.width step 3) {
            seen += map.buffer[y * map.width + x]
        }
    }
    return seen.size
}
