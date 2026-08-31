package com.strange.material.demo.stories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.strange.material.button.Button
import com.strange.material.datetime.DateField
import com.strange.material.form.TextField
import com.strange.material.navigation.Step
import com.strange.material.navigation.Stepper
import com.strange.material.text.Typography
import com.strange.material.text.TypographyVariant
import com.strange.material.theme.StrangeTheme
import kotlinx.datetime.LocalDate

/**
 * Checkout as a stepper. The one `remember` trio is the step, the date and the name — business
 * state a real checkout would own. No animation plumbing.
 */
@Composable
fun CheckoutScreen(modifier: Modifier = Modifier) {
    var step by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("Amara Diallo") }
    var date by remember { mutableStateOf<LocalDate?>(LocalDate(2026, 9, 1)) }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.md),
    ) {
        Typography(text = "Checkout", variant = TypographyVariant.HeadlineSmall)
        Stepper(
            steps =
                listOf(
                    Step("Details", "Who is this for"),
                    Step("When", "Delivery date"),
                    Step("Pay", "Confirm"),
                ),
            current = step,
            onStep = { step = it },
        )
        when (step) {
            0 -> {
                TextField(value = name, onValueChange = { name = it }, label = "Name")
            }

            1 -> {
                DateField(value = date, onValueChange = { date = it }, label = "Deliver on")
            }

            else -> {
                Typography(text = "$name · ${date ?: "no date"}")
                Button(text = "Place order", onClick = {})
            }
        }
    }
}
