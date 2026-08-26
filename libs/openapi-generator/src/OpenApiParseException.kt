package com.strange.openapi

/**
 * Raised for a document this generator cannot faithfully represent.
 *
 * Shared by the parser and the emitters on purpose: whether the gap is found while reading the
 * document or while shaping output for a particular client, the answer is the same — fail, naming
 * what and where. Nothing is ever skipped silently, because a quietly missing endpoint is a bug
 * that surfaces far from its cause.
 */
public class OpenApiParseException(message: String) : IllegalArgumentException(message)
