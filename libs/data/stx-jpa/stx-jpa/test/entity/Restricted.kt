package com.softistx.jpa.entity

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Filter
import org.hibernate.annotations.FilterDef
import org.hibernate.annotations.Immutable
import org.hibernate.annotations.NaturalId
import org.hibernate.annotations.ParamDef

/*
 * Three ways of narrowing what a mapping will show or let you change, and the reason they are one
 * file: each is a claim about the *session*, not about a column, and a reactive session is where a
 * claim about the session goes to be tested.
 *
 * `@Filter` is the one with real stakes — it is how multi-tenancy is usually done — and it was the
 * open question `docs/jpa-mapping.md` named. Reading `Stage.java` answers half of it before a
 * container starts: `enableFilter`, `disableFilter` and `getEnabledFilter` are all on
 * `Stage.Session` (lines 1492-1508). Whether an enabled filter actually reaches a reactive query is
 * the half a spec has to ask.
 */

/**
 * A row visible only to the tenant that owns it, once the filter is switched on.
 *
 * The filter is **off by default**, which is the trap worth naming rather than the feature: a query
 * on a session where nobody called `enableFilter` returns every tenant's rows, and nothing warns.
 * `@FilterDef(autoEnabled = true)` is the other half of the answer, and `RestrictionTest` measures
 * both.
 */
@Entity
@Table(name = "leases")
@FilterDef(name = "byTenant", parameters = [ParamDef(name = "tenant", type = String::class)])
@Filter(name = "byTenant", condition = "tenant = :tenant")
class Lease(
    @Id var id: Long = 0,
    var tenant: String = "",
    var label: String = "",
)

/** The same mapping with the filter enabled for every session that loads the entity. */
@Entity
@Table(name = "quotas")
@FilterDef(name = "byOwner", parameters = [ParamDef(name = "owner", type = String::class)], autoEnabled = true)
@Filter(name = "byOwner", condition = "owner = :owner")
class Quota(
    @Id var id: Long = 0,
    var owner: String = "",
    var amount: Int = 0,
)

/**
 * A row Hibernate will read and never write back.
 *
 * `@Immutable` is not a database constraint — it removes the entity from dirty checking, so a change
 * made to a loaded instance is *dropped silently*. That is the whole measurement: not that the write
 * is refused, but that it is not refused.
 */
@Entity
@Table(name = "postmarks")
@Immutable
class Postmark(
    @Id var id: Long = 0,
    var stamp: String = "",
)

/**
 * A business key beside the surrogate one — and the annotation with the least to offer here.
 *
 * `@NaturalId` earns its keep in blocking Hibernate through `session.byNaturalId(…)`, a first-class
 * lookup with its own cache. **`Stage.Session` has no such method**: `NaturalId` does not appear
 * anywhere in `Stage.java`. So what survives the reactive port is the unique constraint on the
 * column, which a plain `@Column(unique = true)` also gives, and `RestrictionTest` shows the
 * ordinary query that replaces the lookup.
 */
@Entity
@Table(name = "isbns")
class Isbn(
    @Id var id: Long = 0,
    @NaturalId var code: String = "",
    var title: String = "",
)
