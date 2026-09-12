package com.softistx.jpa

import com.softistx.jpa.criteria.fetch
import com.softistx.jpa.criteria.fetchEach
import com.softistx.jpa.entity.Account
import com.softistx.jpa.entity.Course
import com.softistx.jpa.entity.Hamper
import com.softistx.jpa.entity.HamperItem
import com.softistx.jpa.entity.Profile
import com.softistx.jpa.entity.Student
import com.softistx.jpa.query.query
import com.softistx.jpa.session.createQuery
import com.softistx.jpa.session.session
import com.softistx.jpa.session.transaction
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

/**
 * `@OneToOne`, `@ManyToMany`, and the write behaviour every application leans on: cascade and orphan
 * removal.
 *
 * `Shop.kt` and `FetchJoinTest` cover `@ManyToOne` and `@OneToMany` reads. Nothing covered these, and
 * cascade is the one where "it is standard JPA" is least reassuring — it is the persistence context
 * doing work at flush, and the reactive session is a different persistence context.
 */
class RelationshipTest :
    FeatureSpec({

        feature("@OneToOne").config(enabled = JpaTestDatabase.available) {
            scenario("is lazy like the rest, so unfetched it throws and fetched it does not") {
                JpaTestDatabase.withJpa(Account::class, Profile::class) { jpa ->
                    jpa.transaction { session ->
                        session.persist(Account(1, "ada@example.com", Profile(1, "Ada")))
                    }

                    shouldThrowAny {
                        jpa.session { session ->
                            session.query<Account>("from Account").list().map { it.profile?.displayName }
                        }
                    }

                    jpa.session { session ->
                        val criteria = session.createQuery<Account>()
                        criteria.from(Account::class.java).fetch(Account::profile)

                        session
                            .query(criteria)
                            .list()
                            .single()
                            .profile
                            ?.displayName
                    } shouldBe "Ada"
                }
            }

            scenario("cascades the insert, so persisting the owner persists what it holds") {
                JpaTestDatabase.withJpa(Account::class, Profile::class) { jpa ->
                    // Only the account is persisted; the profile rides along.
                    jpa.transaction { session -> session.persist(Account(1, "ada@example.com", Profile(1, "Ada"))) }

                    JpaTestDatabase.rows(jpa.config.schema!!, "profiles") shouldBe 1
                }
            }
        }

        feature("@ManyToMany").config(enabled = JpaTestDatabase.available) {
            scenario("keeps both sides in a join table of its own") {
                JpaTestDatabase.withJpa(Student::class, Course::class) { jpa ->
                    jpa.transaction { session ->
                        val maths = Course(1, "Maths")
                        val physics = Course(2, "Physics")
                        session.persist(maths, physics)
                        session.persist(Student(1, "ada").also { it.courses = mutableSetOf(maths, physics) })
                    }

                    JpaTestDatabase.rows(jpa.config.schema!!, "student_courses") shouldBe 2

                    jpa.session { session ->
                        val criteria = session.createQuery<Student>()
                        criteria.from(Student::class.java).fetchEach(Student::courses)

                        session
                            .query(criteria)
                            .list()
                            .single()
                            .courses
                            .map { it.title }
                    } shouldContainExactlyInAnyOrder listOf("Maths", "Physics")
                }
            }
        }

        feature("cascade and orphan removal").config(enabled = JpaTestDatabase.available) {
            scenario("a child dropped from the collection is deleted, not merely detached") {
                JpaTestDatabase.withJpa(Hamper::class, HamperItem::class) { jpa ->
                    jpa.transaction { session ->
                        val hamper = Hamper(1)
                        hamper.items += HamperItem(1, "mug", hamper)
                        hamper.items += HamperItem(2, "cup", hamper)
                        session.persist(hamper)
                    }
                    JpaTestDatabase.rows(jpa.config.schema!!, "hamper_items") shouldBe 2

                    jpa.transaction { session ->
                        val criteria = session.createQuery<Hamper>()
                        criteria.from(Hamper::class.java).fetchEach(Hamper::items)
                        val hamper = session.query(criteria).list().single()

                        hamper.items.removeIf { it.sku == "cup" }
                    }

                    JpaTestDatabase.rows(jpa.config.schema!!, "hamper_items") shouldBe 1
                }
            }

            scenario("removing the owner removes what it owned") {
                JpaTestDatabase.withJpa(Hamper::class, HamperItem::class) { jpa ->
                    jpa.transaction { session ->
                        val hamper = Hamper(1)
                        hamper.items += HamperItem(1, "mug", hamper)
                        session.persist(hamper)
                    }

                    jpa.transaction { session ->
                        val criteria = session.createQuery<Hamper>()
                        criteria.from(Hamper::class.java).fetchEach(Hamper::items)

                        session.remove(session.query(criteria).list().single())
                    }

                    JpaTestDatabase.rows(jpa.config.schema!!, "hamper_items") shouldBe 0
                }
            }
        }
    })
