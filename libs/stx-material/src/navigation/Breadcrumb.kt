package com.softistx.material.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.rememberUpdatedStyleState
import androidx.compose.foundation.style.styleable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.softistx.material.icon.Icon
import com.softistx.material.icon.IconSize
import com.softistx.material.icon.StxIcons
import com.softistx.material.text.Emphasis
import com.softistx.material.text.Typography
import com.softistx.material.text.TypographyVariant
import com.softistx.material.theme.StxTheme

/**
 * One crumb. The last item of a [Breadcrumb] is the current page: its [onClick] is ignored so a
 * caller can build the trail from one list without a special last case.
 */
@Immutable
data class BreadcrumbItem(
    val label: String,
    val onClick: (() -> Unit)? = null,
)

/**
 * A trail of ancestors, for screens that have a place in a hierarchy.
 *
 * Material 3 has no breadcrumb. Four crumbs or fewer draw in full; more than that collapse the
 * middle behind a menu so a deep path still fits a compact pane. The current page is never a
 * control — it is where the reader already is.
 */
@Composable
fun Breadcrumb(
    items: List<BreadcrumbItem>,
    modifier: Modifier = Modifier,
    style: Style = Style,
) {
    if (items.isEmpty()) return
    val collapsed = items.size > 4
    val visible: List<Crumb> =
        if (!collapsed) {
            items.mapIndexed { index, item -> Crumb(item, current = index == items.lastIndex) }
        } else {
            val middle = items.subList(1, items.lastIndex)
            listOf(
                Crumb(items.first(), current = false),
                Crumb(
                    BreadcrumbItem("…"),
                    current = false,
                    overflow = middle,
                ),
                Crumb(items.last(), current = true),
            )
        }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(StxTheme.spacing.xxs),
    ) {
        visible.forEachIndexed { index, crumb ->
            if (index > 0) {
                Icon(
                    icon = StxIcons.ChevronRight,
                    description = null,
                    size = IconSize.Small,
                )
            }
            CrumbLabel(crumb = crumb, style = style)
        }
    }
}

@Immutable
private data class Crumb(
    val item: BreadcrumbItem,
    val current: Boolean,
    val overflow: List<BreadcrumbItem> = emptyList(),
)

@Composable
private fun CrumbLabel(
    crumb: Crumb,
    style: Style,
) {
    if (crumb.overflow.isNotEmpty()) {
        OverflowCrumb(items = crumb.overflow, style = style)
        return
    }
    val current = crumb.current || crumb.item.onClick == null
    val interactionSource = remember { MutableInteractionSource() }
    val styleState = rememberUpdatedStyleState(interactionSource) { it.isEnabled = !current }
    Typography(
        text = crumb.item.label,
        variant = TypographyVariant.LabelLarge,
        emphasis = if (current) Emphasis.Full else Emphasis.Medium,
        modifier =
            Modifier
                .styleable(styleState, breadcrumbStyle, style)
                .then(
                    if (current) {
                        Modifier
                    } else {
                        Modifier.clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = { crumb.item.onClick.invoke() },
                        )
                    },
                ),
    )
}

@Composable
private fun OverflowCrumb(
    items: List<BreadcrumbItem>,
    style: Style,
) {
    var open by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val styleState = rememberUpdatedStyleState(interactionSource) { it.isEnabled = true }
    Typography(
        text = "…",
        variant = TypographyVariant.LabelLarge,
        emphasis = Emphasis.Medium,
        modifier =
            Modifier
                .styleable(styleState, breadcrumbStyle, style)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = { open = true },
                ),
    )
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        items.forEach { item ->
            DropdownMenuItem(
                text = { Typography(text = item.label) },
                onClick = {
                    open = false
                    item.onClick?.invoke()
                },
            )
        }
    }
}
