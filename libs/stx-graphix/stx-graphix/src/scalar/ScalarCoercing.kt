package com.softistx.graphix.scalar

import graphql.GraphQLContext
import graphql.execution.CoercedVariables
import graphql.language.IntValue
import graphql.language.StringValue
import graphql.language.Value
import graphql.schema.Coercing
import graphql.schema.CoercingParseLiteralException
import graphql.schema.CoercingParseValueException
import graphql.schema.CoercingSerializeException
import java.util.Locale
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Serializes as an integer. A float literal is refused; an `IntValue` larger than Long throws. */
internal object LongCoercing : Coercing<Long, Long> {
    override fun serialize(
        dataFetcherResult: Any,
        graphQLContext: GraphQLContext,
        locale: Locale,
    ): Long = toLong(dataFetcherResult) { CoercingSerializeException(it) }

    override fun parseValue(
        input: Any,
        graphQLContext: GraphQLContext,
        locale: Locale,
    ): Long = toLong(input) { CoercingParseValueException(it) }

    override fun parseLiteral(
        input: Value<*>,
        variables: CoercedVariables,
        graphQLContext: GraphQLContext,
        locale: Locale,
    ): Long {
        val value =
            input as? IntValue ?: throw CoercingParseLiteralException("Long expected an integer literal, got ${input::class.simpleName}")
        return value.value.longValueExact()
    }
}

/** `kotlin.time.Instant`, not `java.time`. Wire format is ISO-8601. */
internal object InstantCoercing : Coercing<Instant, String> {
    override fun serialize(
        dataFetcherResult: Any,
        graphQLContext: GraphQLContext,
        locale: Locale,
    ): String =
        when (dataFetcherResult) {
            is Instant -> dataFetcherResult.toString()
            is String -> dataFetcherResult
            else -> throw CoercingSerializeException("Instant expected kotlin.time.Instant, got ${dataFetcherResult::class.qualifiedName}")
        }

    override fun parseValue(
        input: Any,
        graphQLContext: GraphQLContext,
        locale: Locale,
    ): Instant = parseInstant(input) { CoercingParseValueException(it) }

    override fun parseLiteral(
        input: Value<*>,
        variables: CoercedVariables,
        graphQLContext: GraphQLContext,
        locale: Locale,
    ): Instant {
        val value = input as? StringValue ?: throw CoercingParseLiteralException("Instant expected a string literal")
        return Instant.parse(requireNotNull(value.value) { "Instant string literal was null" })
    }
}

/** `kotlin.uuid.Uuid`. Wire format is the canonical hyphenated string. */
@OptIn(ExperimentalUuidApi::class)
internal object UuidCoercing : Coercing<Uuid, String> {
    override fun serialize(
        dataFetcherResult: Any,
        graphQLContext: GraphQLContext,
        locale: Locale,
    ): String =
        when (dataFetcherResult) {
            is Uuid -> dataFetcherResult.toString()
            is String -> dataFetcherResult
            else -> throw CoercingSerializeException("Uuid expected kotlin.uuid.Uuid, got ${dataFetcherResult::class.qualifiedName}")
        }

    override fun parseValue(
        input: Any,
        graphQLContext: GraphQLContext,
        locale: Locale,
    ): Uuid = parseUuid(input) { CoercingParseValueException(it) }

    override fun parseLiteral(
        input: Value<*>,
        variables: CoercedVariables,
        graphQLContext: GraphQLContext,
        locale: Locale,
    ): Uuid {
        val value = input as? StringValue ?: throw CoercingParseLiteralException("Uuid expected a string literal")
        return Uuid.parse(requireNotNull(value.value) { "Uuid string literal was null" })
    }
}

private inline fun toLong(
    value: Any,
    error: (String) -> RuntimeException,
): Long =
    when (value) {
        is Long -> value
        is Int -> value.toLong()
        is Short -> value.toLong()
        is Byte -> value.toLong()
        is String -> value.toLongOrNull() ?: throw error("Long cannot parse '$value'")
        else -> throw error("Long expected a number, got ${value::class.qualifiedName}")
    }

private inline fun parseInstant(
    input: Any,
    error: (String) -> RuntimeException,
): Instant =
    when (input) {
        is Instant -> {
            input
        }

        is String -> {
            try {
                Instant.parse(input)
            } catch (failure: Exception) {
                throw error("Instant cannot parse '$input': ${failure.message}")
            }
        }

        else -> {
            throw error("Instant expected a string, got ${input::class.qualifiedName}")
        }
    }

@OptIn(ExperimentalUuidApi::class)
private inline fun parseUuid(
    input: Any,
    error: (String) -> RuntimeException,
): Uuid =
    when (input) {
        is Uuid -> {
            input
        }

        is String -> {
            try {
                Uuid.parse(input)
            } catch (failure: Exception) {
                throw error("Uuid cannot parse '$input': ${failure.message}")
            }
        }

        else -> {
            throw error("Uuid expected a string, got ${input::class.qualifiedName}")
        }
    }
