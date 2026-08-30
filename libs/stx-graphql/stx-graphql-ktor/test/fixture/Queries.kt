package com.strange.graphql.ktor.fixture

import com.strange.graphql.schema.Query

class GreetingQueries {
    @Query
    fun hello(): String = "world"
}

class BoomQueries {
    @Query
    fun boom(): String = throw IllegalStateException("nope")
}
