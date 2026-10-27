package com.softistx.material.school.session

import com.softistx.material.components.feedback.UiState

/**
 * Which face of the data the whole app shows. A real app gets this from its repository; the
 * showcase lets the reader (or a headless render) pick it, so every screen's loading, empty and
 * error state can be looked at without a network to fail.
 */
enum class DataState(
    val label: String,
) {
    Loaded("Loaded"),
    Loading("Loading"),
    Empty("Empty"),
    Failed("Error"),
}

/**
 * The [UiState] a screen sees, given the app-wide [DataState]: [full] when loaded, [empty] when the
 * showcase says there is nothing, and the loading and error faces otherwise.
 */
fun <T> DataState.of(
    full: T,
    empty: T,
): UiState<T> =
    when (this) {
        DataState.Loaded -> UiState.Data(full)
        DataState.Loading -> UiState.Loading
        DataState.Empty -> UiState.Data(empty)
        DataState.Failed -> UiState.Error("The school server did not answer. Check the connection and retry.")
    }

/** [of] for a list, whose empty face is the empty list. */
fun <T> DataState.ofList(items: List<T>): UiState<List<T>> = of(items, emptyList())
