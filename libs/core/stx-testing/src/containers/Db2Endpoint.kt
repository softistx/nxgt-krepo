package com.softistx.testing.containers

/**
 * Where a DB2 is, if anyone said — and null if nobody did.
 *
 * **The one backend here with no container fallback.** `icr.io/db2_community/db2` needs a privileged
 * container, several gigabytes and minutes to become ready, and this repo's rule is that a test run
 * does not put the machine under that kind of load unasked. So DB2 is reused or skipped, never
 * started: `stx-jpa` ships the driver, and the specs that would exercise it run only where a
 * server is named.
 */
data class Db2Endpoint(
    val uri: String,
    val username: String,
    val password: String,
    val database: String,
)

/** The DB2 named by `DB2_TEST_URI`, `DB2_TEST_USER` and `DB2_TEST_PASSWORD`, or null. */
fun db2Endpoint(): Db2Endpoint? = endpointFromEnvironment("DB2", ::Db2Endpoint)
