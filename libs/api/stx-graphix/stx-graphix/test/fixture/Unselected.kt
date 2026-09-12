package com.softistx.graphix.fixture

import com.softistx.graphix.schema.GraphQLIgnore
import com.softistx.graphix.schema.QueryMapping
import kotlinx.serialization.Serializable
import java.util.concurrent.atomic.AtomicInteger

/*
 * Types carrying a property that **throws when read**, so a spec can prove what is never read.
 *
 * It stands in for a lazy JPA association, and it is the better instrument: an unfetched association
 * throws only inside a session, where this throws the moment anything touches it — no database, no
 * container, microseconds. What the specs are pinning is that a property is read when its field is
 * selected and at no other time, which is the whole reason a DataLoader can key on a foreign key
 * column instead of on the association it belongs to.
 */

@Serializable
class Shelf(
    val title: String,
) {
    /**
     * A field of the schema, because nothing excluded it — and still never read unless asked for.
     *
     * `var` with an initialiser rather than a getter-only `val`: kotlinx serializes a property with a
     * backing field, and one without is not in the descriptor at all, which would prove the wrong
     * thing. A lazy association has a backing field too.
     */
    var contents: String = ""
        get() = error("read a property the client did not select")
}

/** The same trap, kept out of the schema entirely — which is what [GraphQLIgnore] is for. */
@Serializable
class GuardedShelf(
    val title: String,
) {
    @GraphQLIgnore
    var contents: String = ""
        get() = error("read a property the client did not select")
}

/**
 * [calls] counts what actually ran, which is the only way to tell a validation failure from an
 * execution one here: null-bubbling makes both of them answer `data: null` when the field that
 * failed is non-nullable, so the response shape says nothing.
 */
class ShelfQueries(
    val calls: AtomicInteger = AtomicInteger(),
) {
    @QueryMapping
    fun shelf(): Shelf {
        calls.incrementAndGet()
        return Shelf("Fiction")
    }

    @QueryMapping
    fun guarded(): GuardedShelf {
        calls.incrementAndGet()
        return GuardedShelf("Fiction")
    }
}
