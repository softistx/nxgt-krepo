package com.softistx.spring

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.event.EventListener
import kotlin.time.Duration.Companion.seconds

/**
 * How Spring treats a `suspend fun` annotated `@EventListener`: it runs it, and does not wait for it.
 *
 * Load-bearing and easy to guess wrong in both directions. `IndexInitializer` is a suspending
 * listener on `ApplicationReadyEvent`, so if Spring did not invoke them the symptom would be a
 * startup task that silently never ran. And the second half is why such a listener may not be
 * treated as a startup gate: `publishEvent` returns while the listener is still suspended, so the
 * application is serving requests before the indexes exist. It handles its own failures for the same
 * reason — an exception out of one goes to a reactive error handler nobody is reading, not to
 * whoever published the event.
 *
 * **This is the spec `stx-migrations-spring` is built around.** The migration runner that used to
 * live in this module was a suspending listener too, so the port opened while migrations were still
 * being applied and `examples/spring-orders` polled the ledger from its own specs to work around it.
 * `MigrationGate` is an `InitializingBean` instead: it runs during the refresh, and a throw out of it
 * means no web server at all.
 *
 * The first version of this spec asserted the result straight after `publishEvent` and passed,
 * because a `delay(1)` happened to finish first. It failed on the next run.
 */
class SuspendingListenerTest :
    StringSpec({
        "Spring invokes a suspending @EventListener" {
            val (context, listener) = listenerContext()

            context.publishEvent("go")
            listener.gate.complete("!")

            withTimeout(5.seconds) { listener.done.await() } shouldBe "go!"
            context.close()
        }

        "publishEvent does not wait for it" {
            val (context, listener) = listenerContext()

            // The listener suspends on `gate`, which nothing has completed. If publishing blocked
            // until the listener returned, this line would not be reached.
            context.publishEvent("go")

            listener.done.isCompleted shouldBe false
            listener.gate.complete("!")
            withTimeout(5.seconds) { listener.done.await() }
            context.close()
        }
    })

private fun listenerContext(): Pair<AnnotationConfigApplicationContext, Listener> {
    val context = AnnotationConfigApplicationContext(ListenerConfiguration::class.java)
    return context to context.getBean(Listener::class.java)
}

@Configuration
private open class ListenerConfiguration {
    @Bean
    open fun listener(): Listener = Listener()
}

private open class Listener {
    /** Held open until a spec lets the listener finish, which is how the second one is deterministic. */
    val gate = CompletableDeferred<String>()
    val done = CompletableDeferred<String>()

    @EventListener
    open suspend fun onString(event: String) {
        done.complete(event + gate.await())
    }
}
