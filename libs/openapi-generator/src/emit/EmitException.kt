package com.strange.openapi.emit

/**
 * Raised when a document is readable but a particular client cannot express part of it.
 *
 * Distinct from `OpenApiParseException`, which means the document itself could not be read: the
 * same operation may emit fine for one client and be impossible for another, so the failure
 * belongs to the emitter, not to the parser. Both fail the build rather than dropping the
 * operation, because a quietly missing endpoint surfaces far from its cause.
 */
public class EmitException(
    message: String,
) : IllegalArgumentException(message)
