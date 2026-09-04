package com.softistx.r2jdbc

import com.softistx.r2jdbc.sql.Sql
import com.softistx.r2jdbc.sql.connection
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/**
 * The four things a caller can run, written once and measured against both servers.
 *
 * The statements here are spelled the way a caller would spell them — `?` on both, unqualified
 * table names on both — which is the claim this module is making. Everything that makes that true
 * is in `Backend` and `Placeholders`, and nothing in this file mentions either.
 */
class SqlTest :
    FeatureSpec({

        feature("postgres").config(enabled = TestPostgres.available) {
            scenario("runs the same statements as MySQL, spelled the same way") {
                TestPostgres.withR2jdbc { db -> db.statementContract() }
            }

            scenario("refuses to call a two-row result one row") {
                TestPostgres.withR2jdbc { db -> db.queryOneContract() }
            }
        }

        feature("mysql").config(enabled = TestMysql.available) {
            scenario("runs the same statements as Postgres, spelled the same way") {
                TestMysql.withR2jdbc { db -> db.statementContract() }
            }

            scenario("refuses to call a two-row result one row") {
                TestMysql.withR2jdbc { db -> db.queryOneContract() }
            }
        }
    })

private suspend fun R2jdbc.statementContract() {
    // DDL goes through `unprepared`: the extended protocol a prepared statement uses carries one
    // statement, and this is the door `stx-jpa` opens as `jpa.connection { }`.
    sql.unprepared("create table books (id int primary key, title varchar(80), pages int)")

    sql.execute("insert into books values (?, ?, ?)", 1, "Dune", 412) shouldBe 1
    sql.execute("insert into books values (?, ?, ?)", 2, "Emma", 474) shouldBe 1

    sql.query("select id from books order by id").map { it.getInteger("id") } shouldBe listOf(1, 2)
    sql.queryOne("select title from books where id = ?", 1)?.getString("title") shouldBe "Dune"
    sql.queryOne("select title from books where id = ?", 99) shouldBe null

    // A value is bound and not interpolated, so a quote in it is a quote.
    sql.execute("insert into books values (?, ?, ?)", 3, "it's ? fine", 1) shouldBe 1
    sql.queryOne("select title from books where id = ?", 3)?.getString("title") shouldBe "it's ? fine"

    // `execute` counts what the `where` matched — see `DriverContractTest` for why that is the same
    // answer on both servers.
    sql.execute("update books set pages = ? where id = ?", 412, 1) shouldBe 1
    sql.execute("update books set pages = ? where id = ?", 1, 99) shouldBe 0
    sql.execute("delete from books where id = ?", 2) shouldBe 1

    // And a connection pinned for several statements is the same `Sql`, with the same rules.
    connection { pinned: Sql ->
        pinned.execute("insert into books values (?, ?, ?)", 4, "Ada", 300) shouldBe 1
        pinned.queryOne("select title from books where id = ?", 4)?.getString("title") shouldBe "Ada"
    }
}

private suspend fun R2jdbc.queryOneContract() {
    sql.unprepared("create table twins (id int primary key, kind varchar(20))")
    sql.execute("insert into twins values (?, ?)", 1, "same")
    sql.execute("insert into twins values (?, ?)", 2, "same")

    // Not the first row, and not a silent truncation: a `where` that matched twice has found a
    // broken key, and returning row one is how it stays broken.
    val failure = shouldThrow<R2jdbcException> { sql.queryOne("select id from twins where kind = ?", "same") }
    failure.message shouldContain "matched 2 rows"
}
