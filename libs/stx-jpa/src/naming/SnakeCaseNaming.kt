package com.softistx.jpa.naming

import org.hibernate.boot.model.naming.Identifier
import org.hibernate.boot.model.naming.ImplicitNamingStrategyJpaCompliantImpl
import org.hibernate.boot.spi.MetadataBuildingContext

/**
 * Names the things nobody named, in the case SQL is written in.
 *
 * Hibernate on its own keeps the property name, and Postgres folds an unquoted identifier to lower
 * case — so `createdBy` becomes the column `createdby`, which is not a name anyone would have chosen
 * and is the one everybody assumes Hibernate would not produce. It is Spring that installs a
 * snake-case strategy, not Hibernate, which is why this surprises people who have only met the two
 * together.
 *
 * **An implicit strategy rather than a physical one, and that is the whole design.** Hibernate ships
 * `PhysicalNamingStrategySnakeCaseImpl`, which rewrites *every* identifier — including the ones an
 * entity spells out in `@Table(name = …)` and `@Column(name = …)` — and leaves quoting as the only way
 * to opt out. This runs one step earlier, where Hibernate is deciding a name it was not given, so a
 * name written by hand is used exactly as written. The two produce identical schemas for an entity
 * that names nothing; they differ only where somebody said what they wanted, and there this does what
 * they said. It is also what makes mapping an existing camelCase schema possible without quoting
 * every identifier in it.
 *
 * All seventeen of `ImplicitNamingStrategyJpaCompliantImpl`'s `determine…` methods funnel through
 * [toIdentifier], so overriding it covers tables, columns, join columns, collection tables, foreign
 * keys and the rest.
 */
internal class SnakeCaseNaming : ImplicitNamingStrategyJpaCompliantImpl() {
    override fun toIdentifier(
        stringForm: String,
        buildingContext: MetadataBuildingContext,
    ): Identifier = super.toIdentifier(snakeCase(stringForm), buildingContext)
}

/**
 * A lower-case letter or a digit, as `Character.isLowerCase` and `Character.isDigit` decide it.
 *
 * `\p{javaLowerCase}` and `\p{javaDigit}` call those two methods, where `[a-z0-9]` would answer for
 * ASCII alone. Hibernate asks `Character`, so this asks `Character`: with the ASCII classes `ıMaç`
 * comes out `ımaç` instead of `ı_maç`, and the two strategies would disagree on any name that leaves
 * the ASCII range.
 */
private const val LOWER_OR_DIGIT = "[\\p{javaLowerCase}\\p{javaDigit}]"

/**
 * The seam between a lower-case run and the next capitalised word.
 *
 * **Both halves are zero-width on purpose, and this is the one thing to get right.** Written with
 * capturing groups instead — `([a-z0-9])([A-Z][a-z0-9])` replaced by `$1_$2` — the match *consumes*
 * the letter after the hump, so scanning resumes past it and the following hump is missed:
 * `aBcDeFg` comes out `a_bcde_fg` rather than `a_bc_de_fg`, and `lastSeenAtTime` becomes
 * `last_seen_attime`. Lookarounds match between characters and consume nothing, so every hump in a
 * name is found. `NamingTest` pins both of those names against Hibernate.
 */
private val CAMEL_HUMP = Regex("(?<=$LOWER_OR_DIGIT)(?=\\p{javaUpperCase}$LOWER_OR_DIGIT)")

/**
 * `createdBy` to `created_by`, by Hibernate's own rule rather than one of ours.
 *
 * An underscore goes in where a lower-case letter or digit is followed by an upper-case letter that
 * is itself followed by a lower-case letter or digit. That last clause is why an acronym stays glued
 * together — `orderURL` is `orderurl`, not `order_u_r_l` and not `order_url` — and why a trailing
 * capital gets nothing, `trailingX` being `trailingx`. It is deliberately copied from
 * `PhysicalNamingStrategySnakeCaseImpl`, so that choosing the other strategy later gives the same
 * names for everything either of them touches. `NamingTest` pins that equivalence against Hibernate's
 * class instead of trusting this comment.
 */
internal fun snakeCase(name: String): String = CAMEL_HUMP.replace(name.replace('.', '_'), "_").lowercase()
