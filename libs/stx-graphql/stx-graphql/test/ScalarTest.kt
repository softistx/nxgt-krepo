package com.strange.graphql

import com.strange.graphql.fixture.ScalarQueries
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
class ScalarTest :
    FeatureSpec({
        feature("scalars") {
            scenario("Instant, Uuid and Long coerce through their GraphQL scalars") {
                val graphql = GraphQl { query(ScalarQueries()) }
                val result =
                    graphql.execute(
                        GraphQlRequest("{ epoch id big(n: 41) }"),
                    )
                result.isOk shouldBe true
                result.data shouldBe
                    mapOf(
                        "epoch" to "1970-01-01T00:00:00Z",
                        "id" to "00112233-4455-6677-8899-aabbccddeeff",
                        "big" to 42L,
                    )
            }
        }
    })
