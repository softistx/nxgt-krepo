package com.softistx.graphix.fixture

import com.softistx.graphix.schema.Argument
import com.softistx.graphix.schema.GraphQLDefault
import com.softistx.graphix.schema.GraphQLDeprecated
import com.softistx.graphix.schema.GraphQLOneOf
import com.softistx.graphix.schema.QueryMapping
import kotlinx.serialization.Serializable

/** `@oneOf`: exactly one field, and it may not be null. Every field is therefore nullable. */
@GraphQLOneOf
@Serializable
data class PickInput(
    val byId: String? = null,
    val byName: String? = null,
)

/** Not a legal oneOf input: a caller could never send only the other one. */
@GraphQLOneOf
@Serializable
data class BadPickInput(
    val byId: String,
    val byName: String?,
)

@Serializable
data class LegacyInput(
    val name: String,
    @GraphQLDeprecated("use name") val label: String? = null,
)

class SpecQueries {
    @QueryMapping
    fun pick(
        @Argument input: PickInput,
    ): String = input.byId ?: input.byName ?: "none"

    @QueryMapping
    @GraphQLDeprecated("use greet")
    fun hail(): String = "hail"

    @QueryMapping
    fun greet(
        @Argument name: String = "stranger",
        @GraphQLDeprecated("use name") @Argument who: String? = null,
    ): String = who ?: name

    @QueryMapping
    fun legacy(
        @Argument input: LegacyInput,
    ): String = input.label ?: input.name
}

class BadPickQueries {
    @QueryMapping
    fun pick(
        @Argument input: BadPickInput,
    ): String = input.byId
}

class RequiredDeprecatedQueries {
    @QueryMapping
    fun need(
        @GraphQLDeprecated("gone") @Argument id: String,
    ): String = id
}

/** Non-null but with a default, so the spec allows deprecating it: omitting it still works. */
class DefaultedDeprecatedQueries {
    @QueryMapping
    fun page(
        @GraphQLDeprecated("use cursor") @GraphQLDefault("10") @Argument limit: Int = 10,
    ): Int = limit
}

@Serializable
data class Ticketish(
    val title: String,
    val owner: String,
)

@Serializable
data class Plain(
    val title: String,
)

/** Roots for the SDL fixtures under `classpath:graphix-audit/`. */
class AuditQueries {
    @QueryMapping
    fun ticket(): Ticketish = Ticketish("dune", "frank")

    @QueryMapping
    fun plain(): Plain = Plain("dune")

    @QueryMapping
    fun loud(): String = "quiet"
}
