package com.strange.spring.data.mongo.filter

/**
 * The comparisons a `?filter=` parameter may ask for.
 *
 * The set is deliberately closed. A filter grammar that passed operators through to the database
 * would let a caller write `$where`, which is JavaScript the server runs — so what a client may say
 * is this list, and everything else is not a filter.
 */
enum class Operator(
    val token: String,
) {
    EQ("eq"),
    NE("ne"),
    LT("lt"),
    GT("gt"),
    LTE("lte"),
    GTE("gte"),
    BEFORE("before"),
    AFTER("after"),
    FROM("from"),
    TO("to"),
    LIKE("like"),
    NOT_LIKE("!like"),
    LIKE_IGNORE_CASE("ilike"),
    NOT_LIKE_IGNORE_CASE("!ilike"),
    IN("in"),
    NOT_IN("!in"),
    EXISTS("exists"),
    SIZE("size"),
    NEAR("near"),
    WITHIN("within"),
    ;

    companion object {
        private val byToken = entries.associateBy { it.token }

        /** The operator [token] names, or null when it names none. */
        fun of(token: String): Operator? = byToken[token]
    }
}
