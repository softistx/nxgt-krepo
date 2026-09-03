package com.softistx.graphix.scalar

import com.softistx.graphix.message.GraphixMessages
import com.softistx.graphix.message.MessageKeys
import graphql.GraphQLContext
import graphql.language.ArrayValue
import graphql.language.BooleanValue
import graphql.language.EnumValue
import graphql.language.FloatValue
import graphql.language.IntValue
import graphql.language.NullValue
import graphql.language.ObjectValue
import graphql.language.StringValue
import graphql.language.Value
import graphql.schema.CoercingParseLiteralException
import graphql.schema.CoercingParseValueException
import graphql.schema.CoercingSerializeException
import java.util.Locale

/**
 * One coercion in progress: which scalar, the operation's [GraphQLContext], the operation's
 * locale. Every built-in scalar raises its failures through this, so the wording of a coercion
 * error is decided in one place and translated in one place.
 *
 * `Coercing` hands all three to every method, which is why the message source is read off the
 * context rather than held in a field: the same scalar instance serves every operation, and the
 * catalogue is per operation.
 */
internal class Coercion(
    val scalar: String,
    private val context: GraphQLContext,
    private val locale: Locale,
) {
    /** The text for [key], with [args] and `scalar` interpolated. */
    fun text(
        key: String,
        args: Map<String, Any> = emptyMap(),
    ): String = messages().message(locale, key, args + ("scalar" to scalar))

    /** A resolver returned something this scalar cannot put on the wire. */
    fun cannotSerialize(value: Any): Nothing =
        throw CoercingSerializeException(text(MessageKeys.SERIALIZE, mapOf("actual" to typeName(value))))

    /** A variable's value is of a type this scalar cannot read. */
    fun cannotRead(input: Any): Nothing =
        throw CoercingParseValueException(text(MessageKeys.PARSE_VALUE, mapOf("actual" to typeName(input))))

    /** A literal of the wrong kind. [expected] is one of `MessageKeys.LITERAL_*`. */
    fun cannotAccept(
        input: Value<*>,
        expected: String,
    ): Nothing =
        throw CoercingParseLiteralException(
            text(
                MessageKeys.PARSE_LITERAL,
                mapOf("expected" to text(expected), "actual" to text(literalKey(input))),
            ),
        )

    /** The right kind of input, content this scalar's parser refused. */
    fun unreadable(
        value: Any,
        reason: String? = null,
    ): Nothing = throw CoercingParseValueException(parsed(value, reason))

    /** [unreadable] from the literal side of the same parser. */
    fun unparseable(
        value: Any,
        reason: String? = null,
    ): Nothing = throw CoercingParseLiteralException(parsed(value, reason))

    /**
     * A number outside the scalar's range. [constraint] is one of `MessageKeys.RANGE_*`; [phase]
     * decides which of graphql-java's three exceptions carries it, because a range is the one
     * failure that can happen on the way out as well as on the way in.
     */
    fun outOfRange(
        value: Any,
        constraint: String,
        constraintArgs: Map<String, Any> = emptyMap(),
        phase: CoercionPhase = CoercionPhase.Value,
    ): Nothing {
        val message =
            text(
                MessageKeys.RANGE,
                mapOf("constraint" to text(constraint, constraintArgs), "value" to value.toString()),
            )
        throw when (phase) {
            CoercionPhase.Serialize -> CoercingSerializeException(message)
            CoercionPhase.Value -> CoercingParseValueException(message)
            CoercionPhase.Literal -> CoercingParseLiteralException(message)
        }
    }

    private fun parsed(
        value: Any,
        reason: String?,
    ): String =
        when (reason) {
            null -> text(MessageKeys.PARSE, mapOf("value" to value.toString()))
            else -> text(MessageKeys.PARSE_REASON, mapOf("value" to value.toString(), "reason" to reason))
        }

    private fun messages(): GraphixMessages = context.get<GraphixMessages>(GraphixMessages::class) ?: GraphixMessages.Bundled
}

/** Which `Coercing` method is failing. Each has its own exception, and graphql-java tells them apart. */
internal enum class CoercionPhase {
    /** A resolver's value on its way out. */
    Serialize,

    /** A variable's value on its way in. */
    Value,

    /** A literal in the document on its way in. */
    Literal,
}

/** A GraphQL literal's kind, as a `MessageKeys.LITERAL_*` key. Unknown kinds print their class. */
private fun literalKey(value: Value<*>): String =
    when (value) {
        is StringValue -> MessageKeys.LITERAL_STRING
        is IntValue -> MessageKeys.LITERAL_INT
        is FloatValue -> MessageKeys.LITERAL_FLOAT
        is BooleanValue -> MessageKeys.LITERAL_BOOLEAN
        is EnumValue -> MessageKeys.LITERAL_ENUM
        is ObjectValue -> MessageKeys.LITERAL_OBJECT
        is ArrayValue -> MessageKeys.LITERAL_LIST
        is NullValue -> MessageKeys.LITERAL_NULL
        else -> value::class.simpleName ?: "?"
    }

/** What a value's type is called in an error: the Kotlin name, which is what the writer sees. */
private fun typeName(value: Any): String = value::class.qualifiedName ?: value::class.java.name
