package com.strange.openapi.parser

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.media.Schema

/** What a schema becomes in the model package, or nothing when it becomes no declaration at all. */
internal enum class SchemaKind { Object, Enum, Union, ValueClass }

/**
 * The one answer to "does this schema become a generated declaration, and which kind?".
 *
 * There used to be two copies of this question — one in [typeOf] deciding whether a `$ref` names a
 * class, one in `parseModels` deciding what to emit — kept in step by hand. They cannot be: an enum
 * and a union both have no `properties`, so under the old test a `$ref` to either named a class
 * nobody generated.
 */
internal fun OpenAPI.schemaKindOf(schema: Schema<*>): SchemaKind? =
    when {
        // A type the consumer owns becomes nothing here: that is the whole request.
        schema.extensions?.containsKey(Ext.TYPE) == true -> null

        schema.extensions.isValueClass("schema") -> SchemaKind.ValueClass

        schema.hasGeneratableEnum() -> SchemaKind.Enum

        unionMembersOf(schema) != null -> SchemaKind.Union

        !schema.properties.isNullOrEmpty() -> SchemaKind.Object

        // `allOf` composes an object out of other schemas even with no local properties of its own.
        !schema.allOf.isNullOrEmpty() -> SchemaKind.Object

        else -> null
    }

/** A schema saying only "null" — 3.1's way of adding nullability to a branch of a `oneOf`. */
internal fun Schema<*>.isNullOnly(): Boolean = properties.isNullOrEmpty() && (type == "null" || types?.singleOrNull() == "null")

/**
 * The `oneOf`/`anyOf` branches that carry a type, with 3.1's `{type: "null"}` branch dropped.
 *
 * That branch is nullability, not a member: `oneOf: [Cat, {type: "null"}]` is a nullable `Cat`, and
 * treating it as a two-member union would generate a hierarchy over a type that does not exist.
 */
internal fun Schema<*>.typedBranches(): List<Schema<*>> = (oneOf ?: anyOf).orEmpty().filterNot { it.isNullOnly() }

/**
 * The single branch a `oneOf`/`anyOf` really means, when it names only one.
 *
 * `oneOf: [Cat, {type: "null"}]` and `anyOf: [Cat]` both resolve to `Cat`; the nullability is read
 * separately by [isNullable].
 */
internal fun Schema<*>.soleBranch(): Schema<*>? = typedBranches().singleOrNull()?.takeIf { !(oneOf ?: anyOf).isNullOrEmpty() }
