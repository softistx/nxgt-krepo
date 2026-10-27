package com.softistx.material.foundation.icon

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.softistx.material.NodeBounds
import com.softistx.material.probed
import com.softistx.material.renderProbe
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThan
import java.nio.file.Files
import java.nio.file.Path

private val icons: Map<String, ImageVector> =
    mapOf(
        "bell" to StxIcons.Bell,
        "printer" to StxIcons.Printer,
        "phone" to StxIcons.Phone,
        "edit-note" to StxIcons.EditNote,
    )

/** Each icon is drawn at 24 dp, covers a real part of its box and is not a filled square. */
class NewIconsTest :
    FeatureSpec({
        feature("the icons added for the School showcase") {
            icons.forEach { (name, vector) ->
                scenario("$name paints ink, and leaves room around it") {
                    val bounds = NodeBounds()
                    renderProbe(
                        width = 24,
                        height = 24,
                        content = {
                            Box(Modifier.probed(bounds)) {
                                Icon(icon = vector, description = null, size = IconSize.Large, tint = Color.Black)
                            }
                        },
                    ) {
                        val frame = settle()
                        frame.ink(bounds) shouldBeGreaterThan 100
                        frame.ink(bounds) shouldBeLessThan 24 * 24 * 3 / 4
                        System.getenv("ICON_PNG_DIR")?.let { dir ->
                            Files.createDirectories(Path.of(dir))
                            savePng(Path.of(dir, "$name.png"))
                        }
                    }
                }
            }
        }
    })
