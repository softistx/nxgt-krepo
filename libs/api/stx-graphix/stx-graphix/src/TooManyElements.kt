package com.softistx.graphix

/**
 * A `Flow` field produced more elements than `maxListElements` allows.
 *
 * Deliberately **not** a [GraphixException]. That one means the operation could never be submitted
 * and is never offered to an exception handler; this is an ordinary field failure — the resolver ran
 * and answered with too much — so it travels the way any resolver throw does, becomes an entry in
 * `errors[]`, and an `errors { on<TooManyElements> { } }` handler can say something kinder about it.
 *
 * The bound exists because a `Flow` can be infinite where a `List` cannot. Without one such a field
 * does not fail, it simply never answers.
 */
class TooManyElements(
    /** The GraphQL field that produced them. */
    val field: String,
    /** The bound it passed. */
    val max: Int,
) : RuntimeException("field '$field' produced more than $max elements")
