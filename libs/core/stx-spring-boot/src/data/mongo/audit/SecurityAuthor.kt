package com.softistx.spring.data.mongo.audit

import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.core.userdetails.UserDetails
import reactor.core.publisher.Mono

/**
 * Reads the signed-in user's name from the reactive security context.
 *
 * Spring Security is an optional dependency, so every security type lives here and nowhere in
 * [AuditListener]: Spring reflects over a bean's class when it introspects it, and a method
 * signature or lambda that names a missing class fails that, before any `catch` could run.
 */
internal object SecurityAuthor {
    /** The signed-in user's name, or empty. */
    fun current(): Mono<String> =
        ReactiveSecurityContextHolder
            .getContext()
            .map { (it.authentication?.principal as? UserDetails)?.username.orEmpty() }
            .onErrorReturn("")
            .defaultIfEmpty("")
}
