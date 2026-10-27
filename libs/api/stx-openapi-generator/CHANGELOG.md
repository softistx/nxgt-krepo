# stx-openapi-generator

## 0.3.0

### Minor Changes

- [#321](https://github.com/softistx/nxgt-krepo/pull/321) [`18abc6f`](https://github.com/softistx/nxgt-krepo/commit/18abc6ff525567c44b66049f578785f0cc675da4) Thanks [@SteveGT96](https://github.com/SteveGT96)! - BREAKING: `security` is modelled as alternatives (OR), each an AND of schemes, instead of being flattened. `Operation.security`
  is now `List<List<SecurityRequirement>>` and `@ApiOperation(security = [...])` carries one string per alternative,
  its schemes joined by `,`, with `""` for the anonymous alternative `{}`.
  
  The generated clients (Ktorfit `ApiAuth`, Spring `apiAuthFilter`) send the first alternative whose credentials are all
  configured and only those, send nothing when none is configured and `{}` is allowed, and otherwise throw an
  `IllegalStateException` naming the expected alternatives before sending. A client that holds no credential for an
  operation the document protects used to send a bare request and read a 401; it now fails before the request leaves.
  
  An alternative naming a scheme with no credential slot (`digest`, `negotiate`) is dropped instead of failing the
  emit, as long as another alternative of the operation is handled. A scheme name containing a comma is an `EmitException`.
  
  No other family depends on this one; the demo examples that consume the generator are adapted in the same change.

## 0.2.2

### Patch Changes

- [#250](https://github.com/softistx/nxgt-krepo/pull/250) [`a532490`](https://github.com/softistx/nxgt-krepo/commit/a532490c89e008572840e11ec4e1350b69056fd6) Thanks [@SteveGT96](https://github.com/SteveGT96)! - The server dependencies move to their latest patch and minor releases: Ktor 3.6.0, the MongoDB driver 5.13.0, Kafka clients 4.4.0, the RabbitMQ client 5.37.0, Hibernate Validator 9.1.5 and KotlinPoet 2.4.0. Lettuce stays at 7.7 and kotlinx.serialization at the toolchain's 1.11.0.

<!-- Everything below the title is written by Changesets, newest first; this note is a footer
     because anything between the title and the first section would be pushed down by every
     release. -->

---

This is the release line for `io.github.softistx:stx-openapi-generator`.

Everything through **0.2.1** was released in lockstep with every other library in this repository,
under one shared version and one shared changelog. That history is in
[the root `CHANGELOG.md`](../../../CHANGELOG.md); this file starts where it stops.
