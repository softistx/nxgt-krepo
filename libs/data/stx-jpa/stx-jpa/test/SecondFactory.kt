package com.softistx.jpa

import kotlin.reflect.KClass

/**
 * A second [Jpa] onto the schema this one is using, so a spec can have two sessions that are
 * genuinely apart.
 *
 * **Nesting does not give you that**, which is the whole reason this exists: `jpa.transaction { }`
 * inside `jpa.transaction { }` hands back *the same session*, measured and pinned in
 * `NestedTransactionTest`. A spec about two transactions racing therefore needs two factories, and
 * finding that out cost one wrong measurement — `SpreadTest`'s `@DynamicUpdate` scenarios failed by
 * passing, because the two mappings agreed when there had only ever been one transaction.
 *
 * [SchemaMode.NONE] on the copy is not optional: the first factory made these tables with
 * `create-drop`, and a second one built from its config unchanged would drop them on the way in.
 */
internal suspend fun <T> Jpa.withSecondFactory(
    vararg entities: KClass<*>,
    block: suspend (Jpa) -> T,
): T = Jpa.connect(config.copy(schemaMode = SchemaMode.NONE), entities.toList()).use { block(it) }
