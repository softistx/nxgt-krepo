package com.strange.spring.testing

import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldStartWith
import kotlinx.coroutines.reactor.awaitSingle
import org.bson.Document
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.mongodb.autoconfigure.MongoConnectionDetails
import org.springframework.boot.mongodb.autoconfigure.MongoProperties
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Query

/** `spring.mongodb.database` in `testResources/application-test.yaml`, and nothing else names it. */
private const val PREFIX = "stx_spring_boot_test"

/** What the prefix becomes: `testDatabase` puts this run's suffix on it. */
private val DATABASE = testDatabase(PREFIX)

private const val COLLECTION = "spec_documents"

/**
 * What [MongoSpec] is supposed to arrange, asserted rather than assumed.
 *
 * Two of these claims are the load-bearing ones. **The test bean wins over the auto-configuration** —
 * `MongoReactiveAutoConfiguration` declares its `PropertiesMongoConnectionDetails` under
 * `@ConditionalOnMissingBean(MongoConnectionDetails::class)`, and if that ever stopped being true the
 * design would fail silently by pointing every suite at the default `mongodb://localhost/test`. And
 * **the database an application named is the one it gets**, which is the isolation these specs claim
 * and the thing a shared server makes expensive to be wrong about.
 *
 * `RANDOM_PORT` here rather than the inherited `DEFINED_PORT`, for two reasons: it proves that
 * re-annotating a subclass overrides the base spec, and it means a full `./kotlin test` can run this
 * module and `examples/spring-orders` at once without them fighting over 8088.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MongoSpecTest(
    template: ReactiveMongoTemplate,
    connectionDetails: MongoConnectionDetails,
) : MongoSpec({

        feature("the test bean answers where MongoDB is") {
            scenario("the connection string names the database the test profile asked for") {
                // The auto-configuration's own details would answer `test`, out of the default URI.
                connectionDetails.connectionString.database shouldBe DATABASE
            }

            scenario("and that database is this run's, not one shared with whatever else is running") {
                // The prefix is the application's; the suffix is what keeps two suites against the
                // same `MONGO_TEST_URI` from emptying each other's collections.
                DATABASE shouldStartWith "${PREFIX}_"
                DATABASE shouldNotBe PREFIX
            }

            scenario("and it reads that name from the prefix Boot 4 actually binds") {
                // Pinned because getting it wrong is silent: a `spring.data.mongodb.*` left over from
                // Boot 3 binds to nothing, the driver falls back to `mongodb://localhost/test`, and on
                // a developer machine that is a real server which answers. The suite then passes
                // against the wrong database and says nothing — which is what `examples/spring-orders`
                // did until this class replaced its `@DynamicPropertySource`.
                MongoProperties::class.java.getAnnotation(ConfigurationProperties::class.java).value shouldBe "spring.mongodb"
            }

            scenario("the template opens that database, and not the one the endpoint happened to end in") {
                template.mongoDatabase.awaitSingle().name shouldBe DATABASE
            }
        }

        feature("the base spec starts a server and says where it is") {
            scenario("the recorded port is the one the server took, not the default it started from") {
                TestServer.port shouldBeGreaterThan 0
                TestServer.port shouldNotBe TestServer.STX_TEST_PORT
            }

            scenario("a client built from it reaches the server, with nobody spelling a URL") {
                // Reaching the wrong port fails to connect; any status at all means this one is right.
                webTestClient().get().uri("/nothing-is-here").exchange()
            }
        }

        feature("a real server is behind it").config(enabled = mongoAvailable) {
            scenario("a document written is a document read, and `clear` takes it away again") {
                template.insert(Document("_id", "one"), COLLECTION).awaitSingle()
                template.count(Query(), COLLECTION).awaitSingle() shouldBe 1L

                template.clear(COLLECTION)

                template.count(Query(), COLLECTION).awaitSingle() shouldBe 0L
            }
        }
    })
