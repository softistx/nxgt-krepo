package com.softistx.graphix.scalar

import graphql.schema.GraphQLScalarType
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** `kotlin.uuid.Uuid` as the canonical hyphenated string. */
@OptIn(ExperimentalUuidApi::class)
internal val UuidScalar: GraphQLScalarType =
    scalarType(
        name = "Uuid",
        description = "A UUID, canonical string form.",
        specifiedBy = "https://www.rfc-editor.org/rfc/rfc4122",
        coercing = StringCoercing("Uuid", Uuid::class, { it.toString() }, { Uuid.parse(it) }),
    )
