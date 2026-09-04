package com.softistx.r2jdbc

import com.softistx.r2jdbc.sql.transaction
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * `transaction { }`, and the one sharp edge it has.
 *
 * The mechanism is entirely the driver's — `DriverContractTest` measures it below this library — so
 * what is left to prove here is that the scope wires it up right: committing on return, rolling back
 * on a throw, and giving the connection back either way.
 *
 * The last scenario is the one worth the container. `stx-jpa` needs `Confinement.kt` and a custom
 * dispatcher because a Hibernate Reactive session belongs to the thread that opened it and says so
 * with `HR000069`; the Vert.x client asserts no such thing, and the difference is what lets every
 * scope in this module be eight lines.
 */
class TransactionTest :
    FeatureSpec({

        feature("postgres").config(enabled = TestPostgres.available) {
            scenario("commits what returns and undoes what throws") {
                TestPostgres.withR2jdbc { db -> db.transactionContract() }
            }

            scenario("survives suspending on something else in the middle") {
                TestPostgres.withR2jdbc { db -> db.roamingContract() }
            }
        }

        feature("mysql").config(enabled = TestMysql.available) {
            scenario("commits what returns and undoes what throws") {
                TestMysql.withR2jdbc { db -> db.transactionContract() }
            }

            scenario("survives suspending on something else in the middle") {
                TestMysql.withR2jdbc { db -> db.roamingContract() }
            }
        }
    })

private suspend fun R2jdbc.transactionContract() {
    sql.unprepared("create table accounts (id int primary key, balance int)")
    sql.execute("insert into accounts values (?, ?)", 1, 100)
    sql.execute("insert into accounts values (?, ?)", 2, 0)

    transaction { tx ->
        tx.execute("update accounts set balance = balance - ? where id = ?", 40, 1)
        tx.execute("update accounts set balance = balance + ? where id = ?", 40, 2)
    }
    balances() shouldBe listOf(60, 40)

    // A throw inside takes both statements with it, including the one that had already succeeded.
    shouldThrow<IllegalStateException> {
        transaction { tx ->
            tx.execute("update accounts set balance = balance - ? where id = ?", 60, 1)
            error("the second leg of the transfer failed")
        }
    }
    balances() shouldBe listOf(60, 40)

    // The connections both scopes borrowed went back to a pool of four, so a fifth transaction is
    // not waiting on anything.
    transaction { tx -> tx.queryOne("select balance from accounts where id = ?", 1)?.getInteger("balance") } shouldBe 60
}

/**
 * A transaction that suspends on a dispatcher of its own in the middle, and commits anyway.
 *
 * `withContext(Dispatchers.IO)` plus a delay is enough to resume the coroutine on a different thread
 * from the one that opened the transaction. This is the shape that fails against Hibernate Reactive.
 */
private suspend fun R2jdbc.roamingContract() {
    sql.unprepared("create table roamers (id int primary key, note varchar(40))")

    transaction { tx ->
        tx.execute("insert into roamers values (?, ?)", 1, "before")
        withContext(Dispatchers.IO) { delay(SUSPEND_MILLIS) }
        tx.execute("insert into roamers values (?, ?)", 2, "after")
    }

    sql.query("select id from roamers order by id").map { it.getInteger("id") } shouldBe listOf(1, 2)
}

private const val SUSPEND_MILLIS = 20L

private suspend fun R2jdbc.balances(): List<Int?> = sql.query("select balance from accounts order by id").map { it.getInteger("balance") }
