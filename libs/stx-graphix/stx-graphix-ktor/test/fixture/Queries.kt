package com.strange.graphix.ktor.fixture

import com.strange.graphix.schema.Query

class GreetingQueries {
    @Query
    fun hello(): String = "world"
}

class BoomQueries {
    @Query
    fun boom(): String = throw IllegalStateException("nope")
}
