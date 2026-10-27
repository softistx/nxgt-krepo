package com.softistx.material.components.data

import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.scale
import com.softistx.material.foundation.style.compactPress
import com.softistx.material.foundation.style.rowPress

/**
 * A clickable row of a [DataTable] wide enough to be a table: a press scale of 0.99, as a card or a
 * list row has. A row is never disabled, so there is no fade; the collapsed cards are `Card`s and
 * carry the card style.
 */
internal val tableRowStyle: Style = rowPress

/**
 * A sortable column header of a [DataTable]: a press scale of 0.97, as a chip has, since a header
 * is a compact control and not a full-width row.
 */
internal val sortHeaderStyle: Style = compactPress
