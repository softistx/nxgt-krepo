package com.softistx.material.components.form

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.softistx.material.components.content.Card
import com.softistx.material.components.content.CardVariant
import com.softistx.material.foundation.text.Emphasis
import com.softistx.material.foundation.text.Typography
import com.softistx.material.foundation.text.TypographyVariant
import com.softistx.material.foundation.theme.LocalStxStrings

/**
 * The destructive corner of a settings screen.
 *
 * An outlined [Card] with the error colour on the title, so "Delete account" is not just another
 * [FormSection]. [action] is the control — usually a [com.softistx.material.components.button.ConfirmButton]
 * — kept as a slot because the library does not own the consequence.
 */
@Composable
fun DangerZone(
    text: String,
    modifier: Modifier = Modifier,
    title: String = LocalStxStrings.current.dangerZone,
    action: @Composable () -> Unit,
) {
    Card(modifier = modifier, variant = CardVariant.Outlined) {
        Typography(
            text = title,
            variant = TypographyVariant.TitleSmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics { heading() },
        )
        Typography(text = text, emphasis = Emphasis.Medium)
        action()
    }
}
