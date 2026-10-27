package io.kotest.provided

import io.kotest.core.config.AbstractProjectConfig

/**
 * Kotest looks for this exact class in this exact package.
 *
 * It pins "reduce motion" off for the whole suite, so a spec that composes the default `StxTheme`
 * gives the same answer on a laptop with the OS setting on as on CI. A spec about the OS read sets
 * the property itself and restores it.
 */
object ProjectConfig : AbstractProjectConfig() {
    override suspend fun beforeProject() {
        System.setProperty("stx.material.reduceMotion", "false")
    }
}
