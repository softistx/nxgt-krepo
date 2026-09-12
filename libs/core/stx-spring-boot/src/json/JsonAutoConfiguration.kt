package com.softistx.spring.json

import com.softistx.common.serialization.lenientJson
import kotlinx.serialization.json.Json
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.http.codec.CodecCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.http.codec.json.KotlinSerializationJsonDecoder
import org.springframework.http.codec.json.KotlinSerializationJsonEncoder

/**
 * Makes kotlinx-serialization the codec WebFlux reads and writes JSON with.
 *
 * **Why this is opt-in rather than the default.** Jackson is what WebFlux uses when nobody says
 * otherwise, and it serializes anything; kotlinx serializes what carries `@Serializable` and throws
 * on the rest. Switching a running application over is a decision with a blast radius, not a
 * detail — so it is a property, and `ErrorResponse` is built to come out the same either way.
 *
 * Contributed as a [CodecCustomizer] rather than by implementing `WebFluxConfigurer`. A configurer
 * is a whole extension point with a dozen methods, and an application that has its own would find
 * two of them competing; a customizer is additive by construction and composes with whatever else
 * is registered.
 */
@AutoConfiguration
@EnableConfigurationProperties(JsonProperties::class)
@ConditionalOnProperty(prefix = "stx.json", name = ["enabled"], havingValue = "true")
class JsonAutoConfiguration {
    /**
     * The `Json` the codecs use, built from `stx-common`'s [lenientJson] rather than beside it.
     *
     * Unknown keys are ignored for the reason that module gives — a reader that throws on a field a
     * newer writer added stops during every rolling deploy. The two settings added here are the ones
     * that are about a *response* specifically, which a `Json` meant for a cache or a topic has no
     * opinion on.
     */
    @Bean
    @ConditionalOnMissingBean(name = ["stxWebJson"])
    fun stxWebJson(properties: JsonProperties): Json =
        Json(from = lenientJson) {
            explicitNulls = properties.explicitNulls
            encodeDefaults = properties.encodeDefaults
        }

    @Bean
    fun stxJsonCodecCustomizer(stxWebJson: Json) =
        CodecCustomizer { configurer ->
            configurer.defaultCodecs().apply {
                kotlinSerializationJsonDecoder(KotlinSerializationJsonDecoder(stxWebJson))
                kotlinSerializationJsonEncoder(KotlinSerializationJsonEncoder(stxWebJson))
            }
        }
}
