package com.softistx.jpa

import com.softistx.jpa.entity.Car
import com.softistx.jpa.entity.Hammer
import com.softistx.jpa.entity.Tool
import com.softistx.jpa.entity.Truck
import com.softistx.jpa.entity.Vehicle
import com.softistx.jpa.query.query
import com.softistx.jpa.session.session
import com.softistx.jpa.session.transaction
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Entity inheritance, which nothing here exercised and no document mentioned.
 *
 * "It is standard JPA" is not an answer in this module: Hibernate Reactive is a different session
 * over the same mapping, the two Kotlin compiler plugins have to supply an open superclass and a
 * no-arg constructor for each subclass, and a polymorphic query is exactly the shape that reads a
 * discriminator or a join it may not have.
 */
class InheritanceTest :
    FeatureSpec({

        feature("single table").config(enabled = JpaTestDatabase.available) {
            scenario("stores both subclasses in one table and reads each back as itself") {
                JpaTestDatabase.withJpa(Vehicle::class, Car::class, Truck::class) { jpa ->
                    jpa.transaction { session ->
                        session.persist(Car(1, "ada", 5), Truck(2, "grace", 12))
                    }

                    jpa.session { session ->
                        session.get<Car>(1L).doors shouldBe 5
                        session.get<Truck>(2L).tonnes shouldBe 12
                    }
                }
            }

            scenario("a query on the base type is polymorphic, and each row keeps its own class") {
                JpaTestDatabase.withJpa(Vehicle::class, Car::class, Truck::class) { jpa ->
                    jpa.transaction { session -> session.persist(Car(1, "ada", 5), Truck(2, "grace", 12)) }

                    jpa.session { session ->
                        val all = session.query<Vehicle>("from Vehicle order by id").list()

                        all.map { it.label } shouldContainExactlyInAnyOrder listOf("ada", "grace")
                        all[0].shouldBeInstanceOf<Car>()
                        all[1].shouldBeInstanceOf<Truck>()
                    }
                }
            }

            scenario("the discriminator is a column of the one table") {
                JpaTestDatabase.withJpa(Vehicle::class, Car::class, Truck::class) { jpa ->
                    jpa.transaction { session -> session.persist(Car(1, "ada", 5)) }

                    JpaTestDatabase.columns(jpa.config.schema!!, "vehicles") shouldContainExactlyInAnyOrder
                        listOf("id", "kind", "label", "doors", "tonnes")
                }
            }
        }

        feature("joined").config(enabled = JpaTestDatabase.available) {
            scenario("gives the subclass its own table and still reads as the base type") {
                JpaTestDatabase.withJpa(Tool::class, Hammer::class) { jpa ->
                    jpa.transaction { session -> session.persist(Hammer(1, "claw", 700)) }

                    JpaTestDatabase.columns(jpa.config.schema!!, "hammers") shouldContainExactlyInAnyOrder
                        listOf("id", "weight_grams")

                    jpa.session { session ->
                        val tool = session.query<Tool>("from Tool").list().single()

                        tool.name shouldBe "claw"
                        tool.shouldBeInstanceOf<Hammer>().weightGrams shouldBe 700
                    }
                }
            }
        }
    })
