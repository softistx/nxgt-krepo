package com.strange.graphql.spring.fixture

import com.strange.graphql.schema.Query
import com.strange.graphql.spring.GraphQLController

@GraphQLController
class GreetingQueries {
    @Query
    fun hello(): String = "world"
}

@GraphQLController
class BoomQueries {
    @Query
    fun boom(): String = throw IllegalStateException("nope")
}
