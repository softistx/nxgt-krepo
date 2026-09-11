# apollo

A Kotlin Toolchain plugin that generates Kotlin models from a GraphQL schema and operation
documents at build time, by calling Apollo Kotlin `apollo-compiler`. It is an adapter — the
Gradle plugin cannot run here — not a second generator.

Use it for a Compose / Android / KMP client, and on a server to generate typed documents for
HTTP tests (`OPERATION_DOCUMENT` is the string Graphix / `HttpGraphQlTester` POST). That is how
`nxgt-graphql` uses it.

The plugin is registered once, in `project.yaml`:

```yaml
plugins:
  - ./plugins/apollo
```

## Using it

```yaml
plugins:
  apollo:
    enabled: true
    packageName: com.example.apollo
    generateDataBuilders: true
    generateOptionalOperationVariables: false
    mapScalar:
      DateTime: kotlin.time.Instant
```

Empty `schemaPaths` scans `resources/graphql/` for `.graphqls` / `.gqls` / `.json`. Empty
`srcDir` scans `resources/graphql/documents/` for `.graphql` operations. A `.graphql` file
sitting next to the schema is an operation, not SDL.

| Setting | Default | Meaning |
| --- | --- | --- |
| `schemaPaths` | `resources/graphql/` | Schema files or directories |
| `srcDir` | `resources/graphql/documents/` | Operation / fragment `.graphql` files or directories |
| `packageName` | `generated.apollo` | Package for generated operations |
| `mapScalar` | `{}` | GraphQL scalar → Kotlin FQN |
| `mapScalarAdapters` | `{}` | GraphQL scalar → adapter expression compiled in |
| `generateDataBuilders` | `false` | Test builders for operation models |
| `generateFragmentImplementations` | `false` | Concrete fragment classes |
| `generateOptionalOperationVariables` | `true` | Variables as `Optional`; `false` is the nxgt-graphql shape |
| `useSemanticNaming` | `true` | Suffix class names with Query/Mutation/Subscription |

Settings are typed and KDoc'd in `src/ApolloSettings.kt`. An unknown key fails
`./kotlin show modules` with a line pointer.

## What the consuming module needs

Generated models compile against `$libs.apollo.api`. A runtime client adds `$libs.apollo.runtime`.
A scalar in `mapScalar` needs an adapter in `CustomScalarAdapters` at execute time, unless
`mapScalarAdapters` already baked one in.

The toolchain plugin subsystem is JVM-first. A `jvm/lib` or `jvm/app` consumer is the verified
path; a KMP `common` source set is not promised.

## Where the output goes

```
build/tasks/_<module>_generate@apollo/
```

The task deletes that directory before writing. Failures from the compiler (invalid document,
missing field) fail the build rather than emitting a partial result.

---

Apache-2.0 · [Contributing](../../CONTRIBUTING.md) · [All the libraries](../../README.md)
