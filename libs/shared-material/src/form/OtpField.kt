package com.strange.material.form

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.strange.material.text.Typography
import com.strange.material.text.TypographyVariant
import com.strange.material.theme.StrangeTheme

/**
 * The code from the text message, as one box per digit.
 *
 * Material 3 has nothing like it, and the obvious build — one text field per digit, each moving
 * focus to the next — is the one to avoid: it fights the keyboard, loses a pasted code, and gives
 * a screen reader six unlabelled inputs. This is **one** field wearing several boxes. Paste works,
 * backspace works, autofill lands in one place, and the cells are decoration.
 */
@Composable
fun OtpField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    length: Int = 6,
    label: String? = null,
    helper: String? = null,
    isError: Boolean = false,
    supportingText: String? = null,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.small

    FieldScaffold(
        modifier = modifier,
        label = label,
        helper = helper,
        error = supportingText,
    ) {
        BasicTextField(
            value = value,
            onValueChange = { next -> onValueChange(next.filter(Char::isDigit).take(length)) },
            enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            cursorBrush = SolidColor(scheme.primary),
            decorationBox = {
                Row(horizontalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.xs)) {
                    repeat(length) { index ->
                        val digit = value.getOrNull(index)
                        val filled = digit != null
                        Box(
                            modifier =
                                Modifier
                                    .size(CellWidth, CellHeight)
                                    .clip(shape)
                                    .background(scheme.surfaceContainerHighest)
                                    .border(
                                        width = if (filled) 2.dp else 1.dp,
                                        color =
                                            when {
                                                isError -> scheme.error
                                                filled -> scheme.primary
                                                else -> scheme.outlineVariant
                                            },
                                        shape = shape,
                                    ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Typography(
                                text = digit?.toString().orEmpty(),
                                variant = TypographyVariant.Metric,
                            )
                        }
                    }
                }
            },
        )
    }
}

private val CellWidth = 44.dp
private val CellHeight = 56.dp
