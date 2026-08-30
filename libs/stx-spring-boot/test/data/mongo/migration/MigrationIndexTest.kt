package com.strange.spring.data.mongo.migration

import com.strange.spring.data.mongo.config.IndexInitializer
import com.strange.spring.data.mongo.template.SpringMongo
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import org.springframework.data.mongodb.core.ReactiveMongoTemplate

/**
 * `stx.data.mongo.create-indexes` and `stx.data.mongo.migration` create the same index, and have to
 * agree about its name.
 *
 * Both are on by default in nothing and on together in any application that wants either safely, so
 * this is not an exotic combination — it is what `examples/spring-orders` turns on, and it is how
 * this was found. `MigrationEntry.code` carries `@Indexed(unique = true)`, which `IndexInitializer`
 * creates under the name Spring Data derives from the property (`code`); `MigrationStore.prepare`
 * creates the same keys and, left unnamed, would ask for `code_1`. Mongo refuses the second with
 * `IndexOptionsConflict`, `MigrationRunner` catches it, and **no migration runs** — reported as one
 * warning on a log nobody is reading during a deploy.
 */
private suspend fun indexNames(
    template: ReactiveMongoTemplate,
    collection: String,
): List<String> =
    template
        .indexOps(collection)
        .indexInfo
        .asFlow()
        .toList()
        .map { it.name }

class MigrationIndexTest :
    FeatureSpec({

        feature("the migration index and the mapped one are the same index").config(enabled = SpringMongo.available) {
            scenario("preparing after the index initializer has run does not throw") {
                SpringMongo.withTemplate { template ->
                    // Puts MigrationEntry in the mapping context, which is what IndexInitializer walks.
                    MigrationStore(template).find("V1")
                    IndexInitializer(template).createIndexes()

                    MigrationStore(template).prepare()

                    indexNames(template, MigrationEntry.COLLECTION) shouldContain "code"
                    indexNames(template, MigrationEntry.COLLECTION) shouldHaveSize 2 // _id_ and code
                }
            }

            scenario("the index initializer running after a prepare does not throw either") {
                SpringMongo.withTemplate { template ->
                    val store = MigrationStore(template)
                    store.prepare()
                    store.find("V1")

                    IndexInitializer(template).createIndexes()

                    indexNames(template, MigrationEntry.COLLECTION) shouldHaveSize 2
                }
            }

            scenario("preparing twice is a no-op, so it is safe on every boot") {
                SpringMongo.withTemplate { template ->
                    val store = MigrationStore(template)
                    store.prepare()
                    store.prepare()

                    indexNames(template, MigrationEntry.COLLECTION) shouldHaveSize 2
                }
            }
        }
    })
