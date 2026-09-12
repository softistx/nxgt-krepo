package com.softistx.spring.json

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * What `stx.json` configures.
 *
 * One switch, because the point is not to be configurable — it is that a service serializing
 * `@Serializable` types should not have to remember six `Json` settings, and that two services in
 * this repo should agree about them.
 */
@ConfigurationProperties(prefix = "stx.json")
data class JsonProperties(
    /**
     * Makes kotlinx-serialization the codec WebFlux reads and writes JSON with, in place of Jackson.
     * Off unless asked for, like every `stx.*` integration.
     */
    val enabled: Boolean = false,
    /**
     * Whether a null property is written out at all.
     *
     * Off, so `{"name":"x"}` rather than `{"name":"x","nickname":null,"note":null}`. A response type
     * with a dozen optional fields is mostly nulls otherwise, and a client cannot tell "absent"
     * from "explicitly null" through JSON anyway.
     */
    val explicitNulls: Boolean = false,
    /**
     * Whether a property equal to its default is written out.
     *
     * On, and the opposite of the kotlinx default. This is a response body, and a client that has
     * never seen a field cannot know the default the server would have used for it — so omitting it
     * moves a decision from the server to whoever wrote the client.
     */
    val encodeDefaults: Boolean = true,
)
