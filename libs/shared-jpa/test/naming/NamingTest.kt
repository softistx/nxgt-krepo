package com.strange.jpa.naming

import com.strange.jpa.Jpa
import com.strange.jpa.JpaConfig
import com.strange.jpa.JpaTestDatabase
import com.strange.jpa.SchemaMode
import com.strange.jpa.entity.Audited
import com.strange.jpa.entity.AuditedEvent
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.hibernate.boot.model.naming.Identifier
import org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl

/**
 * What a column ends up called, which is the one decision here that cannot be taken back.
 *
 * Hibernate does not do this on its own — Spring does, and the two are met together often enough that
 * everybody believes otherwise. Without a strategy `createdBy` is the column `createdby`.
 */
class NamingTest :
    FeatureSpec({

        feature("a name nobody wrote").config(enabled = JpaTestDatabase.available) {
            scenario("is snake case, and a name somebody wrote is left alone") {
                JpaTestDatabase.withJpa(Audited::class) { jpa ->
                    // `last_seen` is what a physical strategy would produce here. It stays `lastseen`
                    // because the entity said `@Column(name = "lastSeen")`, and an explicit name is a
                    // statement of intent rather than a starting point.
                    JpaTestDatabase.columns(jpa.config.schema!!, "audited") shouldContainExactly
                        listOf("created_by", "id", "lastseen", "orderurl")
                }
            }

            scenario("and a table Hibernate had to name follows the same rule") {
                JpaTestDatabase.withJpa(AuditedEvent::class) { jpa ->
                    JpaTestDatabase.columns(jpa.config.schema!!, "audited_event") shouldContainExactly
                        listOf("happened_at", "id")
                }
            }
        }

        feature("the schema an application already has").config(enabled = JpaTestDatabase.available) {
            scenario("is reachable with Naming.AS_WRITTEN, which is Hibernate's own behaviour") {
                JpaTestDatabase.withSchema { schema ->
                    Jpa
                        .connect(
                            JpaConfig(
                                uri = JpaTestDatabase.endpoint.uri,
                                username = JpaTestDatabase.endpoint.username,
                                password = JpaTestDatabase.endpoint.password,
                                schema = schema,
                                schemaMode = SchemaMode.CREATE_DROP,
                                naming = Naming.AS_WRITTEN,
                            ),
                            listOf(Audited::class),
                        ).use {
                            JpaTestDatabase.columns(schema, "audited") shouldContainExactly
                                listOf("createdby", "id", "lastseen", "orderurl")
                        }
                }
            }
        }

        feature("the rule itself") {
            scenario("is Hibernate's, so choosing its strategy later renames nothing") {
                // Asserted against the class rather than described in a comment: if a Hibernate
                // upgrade changes how it splits a name, this fails rather than the two drifting.
                val hibernate = PhysicalNamingStrategySnakeCaseImpl()
                listOf(
                    "createdBy",
                    "orderURL",
                    "line1Item",
                    "id",
                    "URL",
                    "aBcD",
                    "already_snake",
                    // Each of these is a name a capturing-group regex gets wrong, because the match
                    // eats the letter after the hump and the next hump is never looked at.
                    "aBcDeFg",
                    "lastSeenAtTime",
                    "aBaBa",
                    // And this one separates asking Character from matching [a-z0-9]: 'ı' is
                    // lower case and not ASCII, so the ASCII class would drop the underscore.
                    "ıMaç",
                ).forEach { name ->
                    snakeCase(name) shouldBe
                        hibernate.toPhysicalColumnName(Identifier.toIdentifier(name), null).text
                }
            }

            scenario("leaves an acronym glued together, the way Hibernate does") {
                snakeCase("orderURL") shouldBe "orderurl"
                snakeCase("createdBy") shouldBe "created_by"
                snakeCase("line1Item") shouldBe "line1_item"
                // A trailing capital has nothing after it, so the rule never fires for it.
                snakeCase("trailingX") shouldBe "trailingx"
                // Every hump, not every other one.
                snakeCase("lastSeenAtTime") shouldBe "last_seen_at_time"
            }
        }
    })
