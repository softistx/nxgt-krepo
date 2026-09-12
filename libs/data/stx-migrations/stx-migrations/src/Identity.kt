package com.softistx.migrations

import java.net.InetAddress
import java.util.UUID

/**
 * Who this run says it is, in the ledger's `appliedBy` and in the lock's owner.
 *
 * `host/pid/abcd1234`, and every part of it earns its place. The host and the pid are what an
 * operator looking at a stuck `RUNNING` record actually needs — a bare UUID identifies the run and
 * tells them nothing about where to go looking. The random suffix is what keeps two runners inside
 * one JVM distinct, which is not a hypothetical: it is every spec in this library that asks whether
 * a second process is turned away.
 *
 * A fresh value per call. Two ledgers built in one process are two owners, so one cannot release the
 * other's lock.
 */
fun migrationIdentity(): String {
    val host = runCatching { InetAddress.getLocalHost().hostName }.getOrNull()?.takeIf { it.isNotBlank() } ?: "unknown"
    val pid = runCatching { ProcessHandle.current().pid() }.getOrNull() ?: 0L
    return "$host/$pid/${UUID.randomUUID().toString().take(8)}"
}
