package com.softistx.material.form

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.softistx.material.display.Card
import com.softistx.material.display.CardVariant
import com.softistx.material.text.Emphasis
import com.softistx.material.text.Typography
import com.softistx.material.text.TypographyVariant

/**
 * The destructive corner of a settings screen.
 *
 * An outlined [Card] with the error colour on the title, so "Delete account" is not just another
 * [FormSection]. [action] is the control — usually a [com.softistx.material.button.ConfirmButton]
 * — kept as a slot because the library does not own the consequence.
 */
@Composable
fun DangerZone(
    text: String,
    modifier: Modifier = Modifier,
    title: String = "Danger zone",
    action: @Composable () -> Unit,
) {
    Card(modifier = modifier, variant = CardVariant.Outlined) {
        Typography(
            text = title,
            variant = TypographyVariant.TitleSmall,
            color = MaterialTheme.colorScheme.error,
        )
        Typography(text = text, emphasis = Emphasis.Medium)
        action()
    }
}
