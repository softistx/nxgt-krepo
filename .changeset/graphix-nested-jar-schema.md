---
"stx-graphix": patch
---

Schema documents under a `classpath:` location are now found inside a jar the class loader names
and `java.class.path` does not — the `BOOT-INF/classes/` of a Spring Boot executable jar, which is
what `./kotlin package -f executable-jar` writes. Such an application used to start with no
documents at all and fall back to building the schema from its types, which fails as soon as a
document declared something types cannot say, such as a `union`.
