package com.strange.i18n

/**
 * How strict a catalog is about what it cannot answer.
 *
 * The two callers want opposite things, which is why this is a choice rather than a default buried
 * in the lookup: a running server should not fail a request over a translation, and a test suite
 * should not pass over one.
 */
enum class MissingKey {
    /**
     * Return the key itself, and a badly-argued message as its own pattern.
     *
     * What production wants. `checkout.button` on a page is ugly and diagnosable; a 500 in its
     * place is neither.
     */
    ReturnKey,

    /**
     * Throw [MissingMessageException], or [MalformedMessageException] for arguments that do not fit.
     *
     * What a test wants. A key typed wrongly is a failing spec here and a support ticket otherwise,
     * and the difference between the two is which of these was configured.
     */
    Fail,
}
