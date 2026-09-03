package com.softistx.graphix.error

import com.softistx.graphix.GraphixError

/**
 * What kind of failure an error is, in the vocabulary a client acts on.
 *
 * graphql-java has an `ErrorType` of its own and it answers a different question: `ValidationError`,
 * `DataFetchingException`, `OperationNotSupported` name the *phase* the operation died in. Nothing
 * in that list says "not found" or "forbidden", which is what a client needs to decide between
 * showing a 404 page and sending the user to log in. These five are Spring GraphQL's, for the same
 * reason it had to add them.
 *
 * [GraphixError.errorType] stays a `String?` rather than this enum, so graphql-java's own
 * classifications keep passing through untouched. This is the vocabulary a *handler* reaches for;
 * `withErrorType(String)` is there for one it does not cover.
 */
enum class GraphixErrorType {
    BAD_REQUEST,
    UNAUTHORIZED,
    FORBIDDEN,
    NOT_FOUND,
    INTERNAL_ERROR,
}
