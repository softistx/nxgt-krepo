# dgs-codegen

A Kotlin Toolchain plugin that generates Kotlin (or Java) types from a GraphQL schema at build
time, by calling Netflix DGS `graphql-dgs-codegen-core`. It is an adapter — the Gradle plugin
cannot run here — not a second generator.

The plugin is registered once, in `project.yaml`:

```yaml
plugins:
  - ./plugins/dgs-codegen
```

## Using it

```yaml
plugins:
  dgs-codegen:
    enabled: true
    packageName: com.example.codegen
    typeMapping:
      DateTime: kotlin.time.Instant
      Date: java.time.LocalDate
```

Empty `schemaPaths` scans `resources/graphql/` under the module, recursively, for `.graphqls`
and `.gqls` — the same default Graphix uses for SDL. Split documents merge. Operation
`.graphql` files belong to the Apollo plugin.

| Setting | Default | Meaning |
| --- | --- | --- |
| `schemaPaths` | `resources/graphql/` | Files or directories of schema documents |
| `packageName` | `generated.dgs` | Base package; types go in `<packageName>.types` |
| `language` | `Kotlin` | `Kotlin` or `Java` |
| `generateClient` | `false` | DGS type-safe query API. Apollo Kotlin owns client documents here |
| `generateDataTypes` | `true` | Input objects, types, enums |
| `generateInterfaces` | `false` | GraphQL interfaces as Kotlin/Java interfaces |
| `generateCustomAnnotations` | `false` | SDL `@annotation` → Kotlin/Java annotations |
| `typeMapping` | `{}` | GraphQL name → existing FQN; that type is not generated |
| `includeImports` | `{}` | Annotation short name → package, with `generateCustomAnnotations` |

Settings are typed and KDoc'd in `src/DgsCodegenSettings.kt`. An unknown key fails
`./kotlin show modules` with a line pointer.

## What the consuming module needs

Generated Kotlin input types carry `com.fasterxml.jackson.annotation.JsonProperty`. Add
`$libs.jackson.annotations` (or `$libs.jackson.module.kotlin`, which pulls it). A
`typeMapping` entry that names a type the module owns must resolve at compile time.

`generateClient: true` also needs the DGS client artifacts — this repo uses Apollo Kotlin for
that surface instead.

## Where the output goes

```
build/tasks/_<module>_generate@dgs-codegen/
```

The task deletes that directory before writing, so a renamed type does not leave a stale file
behind. Failures name the reason and fail the build rather than emitting a partial result.
