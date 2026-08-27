package com.strange.jpa.naming

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
 * `createdBy` to `created_by`, by Hibernate's own rule rather than one of ours.
 *
 * An underscore goes in where a lower-case letter or digit is followed by an upper-case letter that
 * is itself followed by a lower-case letter or digit. That last clause is why an acronym stays glued
 * together — `orderURL` is `orderurl`, not `order_u_r_l` and not `order_url` — and it is deliberately
 * copied from `PhysicalNamingStrategySnakeCaseImpl`, so that choosing the other strategy later gives
 * the same names for everything either of them touches. `NamingTest` pins that equivalence against
 * Hibernate's class instead of trusting this comment.
 */
internal fun snakeCase(name: String): String =
    buildString {
        val text = name.replace('.', '_')
        text.forEachIndexed { index, char ->
            val before = text.getOrNull(index - 1)
            val after = text.getOrNull(index + 1)
            val boundary =
                before != null && after != null &&
                    (before.isLowerCase() || before.isDigit()) &&
                    char.isUpperCase() &&
                    (after.isLowerCase() || after.isDigit())
            if (boundary) append('_')
            append(char)
        }
    }.lowercase()
