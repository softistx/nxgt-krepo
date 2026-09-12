package com.softistx.material.button

/**
 * How much visual weight a button carries.
 *
 * The five are ordered by loudness. Exactly one [Filled] button belongs on a screen — it is the
 * thing to do — and the rest step down from there. Naming them means a review can ask "why are
 * there two filled buttons here?", which is a question a `Button` versus `OutlinedButton` split
 * never quite prompts.
 */
enum class ButtonVariant {
    /** The primary action. Solid fill. */
    Filled,

    /** A secondary action of real importance. Filled with the container tone. */
    Tonal,

    /** An alternative action. Border only. */
    Outlined,

    /** A quiet action, usually in a row of them. No fill, no border. */
    Ghost,

    /** An action that reads as a link: inline, underlined, no padding to speak of. */
    Link,
}

/**
 * What a button *means*, independent of how loud it is.
 *
 * This is the second axis, and having it is what stops a destructive action being spelled as
 * "the red variant" in one place and "error colour" in another. [Neutral] is the escape for a
 * button that should not claim a semantic at all.
 */
enum class ButtonColor {
    Primary,
    Secondary,
    Success,
    Info,
    Warning,
    Danger,
    Neutral,
}
