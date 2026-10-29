package com.softistx.material.foundation.i18n

import androidx.compose.runtime.Immutable

/**
 * Every user-visible string the library owns, in one locale.
 *
 * Each key is a required constructor property with no default, so a locale that misses one does not
 * compile. A string with an argument or a plural is a typed lambda, so the call site cannot pass the
 * wrong thing and each language owns its own plural rule. There are no String keys anywhere.
 *
 * Override one string with [copy]: `StxStrings.En.copy(retry = "Try again")`. Equality compares the
 * lambdas by reference, which is what a theme needs to skip recomposition when nothing changed.
 */
@Immutable
data class StxStrings(
    /** Confirms a picker. */
    val ok: String,
    /** Dismisses a dialog or picker without applying it. */
    val cancel: String,
    /** Default confirm action of a dialog. */
    val confirm: String,
    /** Label of a two-step confirm button once armed. */
    val confirmQuestion: String,
    /** Closes a lightbox or a panel. */
    val close: String,
    /** Saves an inline edit. */
    val save: String,
    /** Starts an inline edit. */
    val edit: String,
    /** Retries a failed load. */
    val retry: String,
    /** Announced while content loads. */
    val loading: String,
    /** Title of the error shown when a load fails. */
    val loadFailed: String,
    /** Goes to the previous page or item. */
    val previous: String,
    /** Goes to the next page or item. */
    val next: String,
    /** Forward action of a step footer. */
    val continueStep: String,
    /** Backward action of a step footer. */
    val back: String,
    /** Placeholder of a search field. */
    val search: String,
    /** Opens an overflow menu. */
    val more: String,
    /** Opens the extra actions of a split button. */
    val moreActions: String,
    /** Opens a help tip. */
    val moreInformation: String,
    /** Dismisses an announcement. */
    val dismiss: String,
    /** Drops the message being replied to. */
    val dismissReply: String,
    /** Unpins a pinned message. */
    val unpin: String,
    /** Sends a composed message. */
    val send: String,
    /** Attaches a file to a message. */
    val attachFile: String,
    /** Placeholder of the message composer. */
    val writeMessage: String,
    /** Decrements a quantity. */
    val decrease: String,
    /** Increments a quantity. */
    val increase: String,
    /** Reveals a hidden field value. */
    val showValue: String,
    /** Hides a revealed field value. */
    val hideValue: String,
    /** Placeholder of a select field. */
    val chooseOne: String,
    /** Placeholder of an empty inline edit. */
    val addTitle: String,
    /** Placeholder of the tag field. */
    val addTag: String,
    /** Prompt of the upload field. */
    val uploadPrompt: String,
    /** Supporting text of the upload field. */
    val uploadHint: String,
    /** Marks a form field as optional. */
    val optional: String,
    /** Title of the danger zone section. */
    val dangerZone: String,
    /** Weakest password grade. */
    val passwordWeak: String,
    /** Second password grade. */
    val passwordFair: String,
    /** Third password grade. */
    val passwordGood: String,
    /** Strongest password grade. */
    val passwordStrong: String,
    /** Label of the date field. */
    val date: String,
    /** Opens the date picker. */
    val pickDate: String,
    /** Label of the date range field. */
    val dates: String,
    /** Opens the date range picker. */
    val pickDates: String,
    /** Label of the time field. */
    val time: String,
    /** Opens the time picker. */
    val pickTime: String,
    /** Loads the next batch of a list. */
    val loadMore: String,
    /** Scrolls a list back to its start. */
    val backToTop: String,
    /** Placeholder of an empty detail pane. */
    val selectAnItem: String,
    /** Empties the current selection. */
    val clearSelection: String,
    /** Relative time under a minute. */
    val justNow: String,
    /** Relative time of the previous day. */
    val yesterday: String,
    /** Opens a floating action menu. */
    val openActions: String,
    /** Closes a floating action menu. */
    val closeActions: String,
    /** Copies a value. */
    val copy: String,
    /** Confirms a value was copied. */
    val copied: String,
    /** Label of the sort control. */
    val sortBy: String,
    /** Ascending sort direction. */
    val ascending: String,
    /** Descending sort direction. */
    val descending: String,
    /** Title of the command palette. */
    val command: String,
    /** Placeholder of the command palette. */
    val typeACommand: String,
    /** Empty result of the command palette. */
    val noMatchingCommands: String,
    /** Message state before anyone saw it. */
    val sent: String,
    /** Shown when an image fails to load. */
    val imageLoadFailed: String,
    /** Announced by the typing indicator. */
    val someoneIsTyping: String,
    /** Light colour mode. */
    val themeLight: String,
    /** Dark colour mode. */
    val themeDark: String,
    /** Colour mode that follows the system. */
    val themeSystem: String,
    /** Expands a clamped text. */
    val readMore: String,
    /** Collapses an expanded text. */
    val showLess: String,
    /** Size of the current selection. */
    val selected: (count: Int) -> String,
    /** A future time in minutes. */
    val inMinutes: (count: Int) -> String,
    /** A future time in hours. */
    val inHours: (count: Int) -> String,
    /** A past time in minutes. */
    val minutesAgo: (count: Int) -> String,
    /** A past time in hours. */
    val hoursAgo: (count: Int) -> String,
    /** A past time in days. */
    val daysAgo: (count: Int) -> String,
    /** Copies a promo code. */
    val copyCode: (code: String) -> String,
    /** Sorts a table by a column. */
    val sortByColumn: (column: String) -> String,
    /** Readers of a message. */
    val seenBy: (count: Int) -> String,
    /** Removes a tag, a mention or a file. */
    val remove: (item: String) -> String,
    /** Accessible name of one rating star. */
    val ratingOf: (star: Int, max: Int) -> String,
) {
    companion object
}
