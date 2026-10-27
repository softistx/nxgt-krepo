package com.softistx.openapi.emit

import com.softistx.openapi.SecurityScheme
import com.squareup.kotlinpoet.CodeBlock

/**
 * The statements both client styles share for choosing which alternative of an operation's
 * `security` to send, written once so the rule cannot drift between Ktorfit and Spring.
 *
 * The rule, in the order it is applied:
 *
 * 1. Walk the alternatives in the document's order and take the **first one whose every scheme has a
 *    credential** — a slot that is set and returns a value. Its credentials, and only its, go on
 *    the request, so `[{Bearer}, {ApiKey}]` never sends both.
 * 2. If none is complete and the anonymous alternative `{}` (the empty string in the annotation) is
 *    among them, send nothing: the document says the call is allowed without a credential.
 * 3. Otherwise throw an [IllegalStateException] naming the operation and the alternatives it
 *    expected, **before** anything is sent. Without this a client that lost its token sends a bare
 *    request and the caller reads a 401 from the server, which does not say which slot to fill.
 *
 * A slot is read lazily and stops at the first scheme of an alternative that has no credential, so a
 * token that costs a refresh is only fetched when an alternative actually reaches it. Attaching is
 * deferred (`pending`) until an alternative is known to be complete, because half of one is
 * exactly the leak this exists to prevent.
 *
 * The caller provides what differs: [alternatives], the expression holding the annotation's strings;
 * [operationId], the expression naming the operation in the error; and [attach], the statements that
 * put one scheme's credential — held in a local called `value` — on the request.
 */
internal fun chooseAlternative(
    schemes: List<SecurityScheme>,
    alternatives: String,
    operationId: String,
    attach: (SecurityScheme) -> CodeBlock,
): CodeBlock {
    val builder =
        CodeBlock
            .builder()
            .addStatement("val pending = mutableListOf<() -> Unit>()")
            .addStatement("var satisfied = false")
            .addStatement("var anonymous = false")
            .beginControlFlow("for (alternative in %L)", alternatives)
            .beginControlFlow("if (alternative.isEmpty())")
            .addStatement("anonymous = true")
            .addStatement("continue")
            .endControlFlow()
            .addStatement("pending.clear()")
            .addStatement("var complete = true")
            .beginControlFlow("for (scheme in alternative.split('%L'))", ALTERNATIVE_SEPARATOR)
            .beginControlFlow("when (scheme)")
    schemes.forEach { scheme ->
        builder
            .beginControlFlow("%S ->", scheme.name)
            .addStatement("val value = credentials.%N?.invoke()", scheme.propertyName)
            .beginControlFlow("if (value == null)")
            .addStatement("complete = false")
            .nextControlFlow("else")
            .beginControlFlow("pending.add")
            .add(attach(scheme))
            .endControlFlow()
            .endControlFlow()
            .endControlFlow()
    }
    // Unreachable: the parser rejects a scheme the document does not declare and the emitter drops
    // every alternative that names one without a slot. `when` over a String is not exhaustive.
    return builder
        .addStatement("else -> complete = false")
        .endControlFlow()
        .addStatement("if (!complete) break")
        .endControlFlow()
        .beginControlFlow("if (complete)")
        .addStatement("pending.forEach { it() }")
        .addStatement("satisfied = true")
        .addStatement("break")
        .endControlFlow()
        .endControlFlow()
        .beginControlFlow("if (!satisfied && !anonymous)")
        .addStatement(
            "val expected = %L.joinToString(%S) { %S + it.replace(%S, %S) + %S }",
            alternatives,
            " or ",
            "[",
            ",",
            " + ",
            "]",
        ).addStatement(
            "throw IllegalStateException(%P)",
            "\${$operationId} needs a credential for one of \$expected, but none is configured",
        ).endControlFlow()
        .build()
}
