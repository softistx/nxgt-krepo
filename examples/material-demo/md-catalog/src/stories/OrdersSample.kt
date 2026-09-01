package com.softistx.material.demo.stories

import com.softistx.material.theme.Tone

/** The rows the full-screen story renders. Data only — kept out of the screen so the screen reads. */
data class Order(
    val customer: String,
    val reference: String,
    val status: String,
    val tone: Tone,
)

val SampleOrders =
    listOf(
        Order("Amara Diallo", "#4821 · 3 items · €142.00", "settled", Tone.Success),
        Order("Jonas Weber", "#4822 · 1 item · €28.50", "pending", Tone.Info),
        Order("Priya Raman", "#4823 · 7 items · €310.90", "on hold", Tone.Warning),
        Order("Elif Yilmaz", "#4824 · 2 items · €64.00", "settled", Tone.Success),
        Order("Tomas Novak", "#4825 · 4 items · €98.20", "refunded", Tone.Error),
    )

val SampleFilters = listOf("All", "Settled", "Pending", "On hold")
