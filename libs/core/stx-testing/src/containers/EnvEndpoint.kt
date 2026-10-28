package com.softistx.testing.containers

/**
 * Reads `<PREFIX>_TEST_URI`, `<PREFIX>_TEST_USER` and `<PREFIX>_TEST_PASSWORD` and builds the
 * endpoint from them with the database name the URI ends in, or returns null unless all three are
 * set and not blank — a URI on its own would send a run at somebody's real server with no way in.
 */
internal fun <T> endpointFromEnvironment(
    prefix: String,
    build: (uri: String, user: String, password: String, database: String) -> T,
): T? {
    val uri = System.getenv("${prefix}_TEST_URI")
    val user = System.getenv("${prefix}_TEST_USER")
    val password = System.getenv("${prefix}_TEST_PASSWORD")
    return if (uri.isNullOrBlank() || user.isNullOrBlank() || password.isNullOrBlank()) {
        null
    } else {
        build(uri, user, password, uri.substringAfterLast('/').substringBefore('?'))
    }
}
