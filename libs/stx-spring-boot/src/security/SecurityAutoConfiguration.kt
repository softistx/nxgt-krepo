package com.softistx.spring.security

import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.security.core.annotation.AnnotationTemplateExpressionDefaults
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder

/**
 * The two Spring Security beans every application here would otherwise write identically.
 *
 * **Not the filter chain.** Which paths are open, how a user authenticates and what the session
 * looks like are the application's decisions; a library that answered them would either lock a
 * service out of its own health check or open something that should not be open.
 *
 * `@EnableReactiveMethodSecurity` is also the application's to add. Turning method security on
 * changes how every bean in the context is proxied, which is not a thing a dependency should do to
 * someone quietly — and the annotations in this package do nothing until it is on.
 */
@AutoConfiguration
@EnableConfigurationProperties(SecurityProperties::class)
@ConditionalOnClass(PasswordEncoder::class)
@ConditionalOnProperty(prefix = "stx.security", name = ["enabled"], havingValue = "true")
class SecurityAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    fun passwordEncoder(properties: SecurityProperties): PasswordEncoder = BCryptPasswordEncoder(properties.bcryptStrength)

    /**
     * What makes `@RequireRole("ADMIN")` mean `hasRole('ADMIN')`.
     *
     * Spring Security resolves `{value}` in a meta-annotation's expression only when this bean is
     * present. Without it the expression stays the literal string `hasRole('{value}')`, which
     * matches nobody — so the annotations in this package would silently deny every call rather than
     * failing in a way anyone could debug.
     */
    @Bean
    @ConditionalOnMissingBean
    fun annotationTemplateExpressionDefaults() = AnnotationTemplateExpressionDefaults()
}
