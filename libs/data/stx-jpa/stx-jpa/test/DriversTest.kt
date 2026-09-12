package com.softistx.jpa

import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldNotBe

/**
 * That all three drivers actually reach an application's runtime classpath.
 *
 * They are declared `runtime-only`, which is the scope that says "needed to run, not to compile" —
 * and a scope is a claim about a classpath that nothing else here would notice being wrong. Loading
 * the class each driver registers itself through is the cheapest way to hold that claim: no server,
 * no connection, and it fails the moment somebody removes a line from the manifest.
 *
 * Named by string rather than imported on purpose. An import would put the class on the *compile*
 * classpath, which is what this spec is asserting nobody needs.
 */
class DriversTest :
    FeatureSpec({

        feature("the drivers Hibernate Reactive can choose from") {
            listOf(
                "postgres" to "io.vertx.pgclient.PgBuilder",
                "mysql" to "io.vertx.mysqlclient.MySQLBuilder",
                "db2" to "io.vertx.db2client.DB2Builder",
            ).forEach { (database, driver) ->
                scenario("$database, through $driver") {
                    Class.forName(driver) shouldNotBe null
                }
            }
        }
    })
