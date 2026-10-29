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
    /**
     * Every string with a sample argument, by key. A test walks this, so a key added above and
     * missed here is the one thing left to forget: keep the two lists in one edit.
     */
    internal fun entries(): Map<String, String> =
        mapOf(
            "ok" to ok,
            "cancel" to cancel,
            "confirm" to confirm,
            "confirmQuestion" to confirmQuestion,
            "close" to close,
            "save" to save,
            "edit" to edit,
            "retry" to retry,
            "loading" to loading,
            "loadFailed" to loadFailed,
            "previous" to previous,
            "next" to next,
            "continueStep" to continueStep,
            "back" to back,
            "search" to search,
            "more" to more,
            "moreActions" to moreActions,
            "moreInformation" to moreInformation,
            "dismiss" to dismiss,
            "dismissReply" to dismissReply,
            "unpin" to unpin,
            "send" to send,
            "attachFile" to attachFile,
            "writeMessage" to writeMessage,
            "decrease" to decrease,
            "increase" to increase,
            "showValue" to showValue,
            "hideValue" to hideValue,
            "chooseOne" to chooseOne,
            "addTitle" to addTitle,
            "addTag" to addTag,
            "uploadPrompt" to uploadPrompt,
            "uploadHint" to uploadHint,
            "optional" to optional,
            "dangerZone" to dangerZone,
            "passwordWeak" to passwordWeak,
            "passwordFair" to passwordFair,
            "passwordGood" to passwordGood,
            "passwordStrong" to passwordStrong,
            "date" to date,
            "pickDate" to pickDate,
            "dates" to dates,
            "pickDates" to pickDates,
            "time" to time,
            "pickTime" to pickTime,
            "loadMore" to loadMore,
            "backToTop" to backToTop,
            "selectAnItem" to selectAnItem,
            "clearSelection" to clearSelection,
            "justNow" to justNow,
            "yesterday" to yesterday,
            "openActions" to openActions,
            "closeActions" to closeActions,
            "copy" to copy,
            "copied" to copied,
            "sortBy" to sortBy,
            "ascending" to ascending,
            "descending" to descending,
            "command" to command,
            "typeACommand" to typeACommand,
            "noMatchingCommands" to noMatchingCommands,
            "sent" to sent,
            "imageLoadFailed" to imageLoadFailed,
            "someoneIsTyping" to someoneIsTyping,
            "themeLight" to themeLight,
            "themeDark" to themeDark,
            "themeSystem" to themeSystem,
            "readMore" to readMore,
            "showLess" to showLess,
            "selected(0)" to selected(0),
            "selected(1)" to selected(1),
            "selected(2)" to selected(2),
            "inMinutes(0)" to inMinutes(0),
            "inMinutes(1)" to inMinutes(1),
            "inMinutes(2)" to inMinutes(2),
            "inHours(0)" to inHours(0),
            "inHours(1)" to inHours(1),
            "inHours(2)" to inHours(2),
            "minutesAgo(0)" to minutesAgo(0),
            "minutesAgo(1)" to minutesAgo(1),
            "minutesAgo(2)" to minutesAgo(2),
            "hoursAgo(0)" to hoursAgo(0),
            "hoursAgo(1)" to hoursAgo(1),
            "hoursAgo(2)" to hoursAgo(2),
            "daysAgo(0)" to daysAgo(0),
            "daysAgo(1)" to daysAgo(1),
            "daysAgo(2)" to daysAgo(2),
            "copyCode" to copyCode("SAVE10"),
            "sortByColumn" to sortByColumn("Name"),
            "seenBy(0)" to seenBy(0),
            "seenBy(1)" to seenBy(1),
            "seenBy(2)" to seenBy(2),
            "remove" to remove("Ada"),
            "ratingOf" to ratingOf(3, 5),
        )

    companion object {
        val En: StxStrings get() = EnCatalogue
        val Fr: StxStrings get() = FrCatalogue
    }
}
