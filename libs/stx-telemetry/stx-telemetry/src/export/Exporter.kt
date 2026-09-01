package com.strange.telemetry.export

import com.strange.telemetry.model.Signal

/**
 * Where signals go once they leave this process's memory.
 *
 * One method, taking a **batch**, because every destination worth having is cheaper per signal the
 * more of them arrive together: one HTTP request instead of a hundred, one write instead of a
 * hundred. An exporter that would rather have them one at a time can loop; an exporter over a
 * network cannot un-split what it was handed separately.
 *
 * It suspends, and it is called from a single coroutine that owns the queue — so an implementation
 * needs no synchronisation of its own, and may take as long as it needs without blocking anybody who
 * writes a log. What it must not do is throw: [com.strange.telemetry.Telemetry] catches and reports,
 * because **a destination being down is not a reason for the application to fail**, but an exporter
 * that treats a failure as fatal to its own state will simply stop working after the first one.
 */
interface Exporter : AutoCloseable {
    suspend fun export(batch: List<Signal>)

    /** Releases whatever this holds. Nothing by default, because most exporters hold nothing. */
    override fun close() {}
}
