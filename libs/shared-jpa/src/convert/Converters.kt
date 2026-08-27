package com.strange.jpa.convert

import jakarta.persistence.AttributeConverter
import kotlin.reflect.KClass

/**
 * The converters `Jpa.connect` registers for every factory it builds.
 *
 * They are `autoApply`, so this list is the only place they are named: an entity writes
 * `kotlin.time.Instant` or `kotlin.uuid.Uuid` and gets the right column with nothing on it. Adding
 * one here changes the mapping of every application that upgrades, so a new entry belongs with the
 * spec that pins the column type it produces.
 */
val kotlinConverters: List<KClass<out AttributeConverter<*, *>>> =
    listOf(InstantConverter::class, UuidConverter::class)
