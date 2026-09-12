package com.softistx.graphix.fixture

import com.softistx.graphix.schema.Argument
import com.softistx.graphix.schema.GraphQLDefault
import com.softistx.graphix.schema.GraphQLId
import com.softistx.graphix.schema.QueryMapping
import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Serializable
data class Ticket(
    @GraphQLId val id: String,
    @GraphQLId val batch: Uuid,
    @GraphQLId val tags: List<String>,
    val title: String,
)

@Serializable
data class PageInput(
    @GraphQLDefault("10") val limit: Int = 10,
    @GraphQLDefault("\"name\"") val sort: String = "name",
)

class TicketQueries {
    @OptIn(ExperimentalUuidApi::class)
    @QueryMapping
    fun ticket(
        @GraphQLId @Argument id: String,
    ): Ticket = Ticket(id, Uuid.parse("00000000-0000-4000-8000-000000000000"), listOf("t1"), "Dune")

    @QueryMapping
    @GraphQLId
    fun currentId(): String = "me"

    @QueryMapping
    fun page(
        @GraphQLDefault("10") @Argument limit: Int = 10,
    ): Int = limit

    @QueryMapping
    fun paged(
        @Argument input: PageInput,
    ): String = "${input.sort}:${input.limit}"
}

class BadIdQueries {
    @QueryMapping
    fun count(
        @GraphQLId @Argument size: Boolean,
    ): Int = if (size) 1 else 0
}

class BadDefaultQueries {
    @QueryMapping
    fun broken(
        @GraphQLDefault("{ not a literal") @Argument limit: Int = 1,
    ): Int = limit
}
