# artifacts-spring

The Spring Boot half of the coordinate check.
[`artifacts-ktor`](../artifacts-ktor/README.md) explains why both exist and what each proves.

## What this half adds

`stx-amqp-spring`, `stx-kafka-spring` and `stx-storage-spring` each register through
`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`. Spring reads
those files from the classpath at context startup, so **booting a context is the only way to find
out** that the file survived publication, that the class it names is in the jar, and that loading it
does not throw.

The application is one annotated class with no code in it. All three auto-configurations sit behind
`@ConditionalOnProperty(stx.<name>.enabled)` and none is turned on — a context that starts with them
present and inert is exactly the claim.

## Two things that bit while writing it

- **No `slf4j-nop`.** `artifacts-ktor` has one, because Ktor logs through SLF4J and a no-op keeps the
  output readable. Here `spring-boot-starter` brings Logback, and a second binding makes the context
  refuse to start: *"LoggerFactory is not a Logback LoggerContext but Logback is on the classpath."*
- **No dependency on `stx-spring-boot`.** It ships a `SpringProjectConfig` that would have saved the
  one line in `test/ProjectConfig.kt`. Taking it would have put a fourth library's POM between the
  check and the three it checks, which is the opposite of the point.
