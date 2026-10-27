---
"stx-openapi-generator": major
---

`security` is modelled as alternatives (OR), each an AND of schemes, instead of being flattened. `Operation.security`
is now `List<List<SecurityRequirement>>` and `@ApiOperation(security = [...])` carries one string per alternative,
its schemes joined by `,`, with `""` for the anonymous alternative `{}`.

The generated clients (Ktorfit `ApiAuth`, Spring `apiAuthFilter`) send the first alternative whose credentials are all
configured and only those, send nothing when none is configured and `{}` is allowed, and otherwise throw an
`IllegalStateException` naming the expected alternatives before sending. A client that holds no credential for an
operation the document protects used to send a bare request and read a 401; it now fails before the request leaves.

An alternative naming a scheme with no credential slot (`digest`, `negotiate`) is dropped instead of failing the
emit, as long as another alternative of the operation is handled. A scheme name containing a comma is an `EmitException`.

No other family depends on this one; the demo examples that consume the generator are adapted in the same change.
