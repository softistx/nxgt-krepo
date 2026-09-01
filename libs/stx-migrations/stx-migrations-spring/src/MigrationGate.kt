package com.softistx.migrations.spring

import com.softistx.migrations.MigrationRecord
import com.softistx.migrations.MigrationRunner
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.InitializingBean
import org.springframework.beans.factory.ObjectProvider

/**
 * Runs every `MigrationRunner` bean, and stops the context refreshing if any of them fails.
 *
 * **An [InitializingBean] and not an `@EventListener`, which is the point of this class.**
 * `SuspendingListenerTest` in `stx-spring-boot` pins that Spring **does not wait for a suspending
 * listener**: `publishEvent` returns while the listener is still suspended. The runner this library
 * replaces was a suspending `ApplicationReadyEvent` listener, so the port opened while migrations
 * were still running — and `examples/spring-orders` worked around it by polling the ledger from its
 * own specs. A throw out of [afterPropertiesSet] aborts the refresh instead: no web server, no
 * `ApplicationReadyEvent`, no requests.
 *
 * `runBlocking` for the reason every other bootstrap in these libraries uses it: `run()` suspends and
 * a bean's lifecycle callback cannot. It runs once, on the thread that is already blocked waiting for
 * the context.
 *
 * The runners are taken as an [ObjectProvider] rather than a `List`, so an application that declares
 * none still starts — `stx.migrations.enabled=true` with no store named and no runner bean is an
 * empty gate rather than a failure to build one.
 */
class MigrationGate(
    private val runners: ObjectProvider<MigrationRunner<*>>,
) : InitializingBean {
    /** Every ledger, as the gate read it — for a health endpoint, or for a spec. */
    lateinit var ledger: List<MigrationRecord>
        private set

    override fun afterPropertiesSet() {
        ledger = runBlocking { runners.orderedStream().toList().flatMap { it.run() } }
    }
}
