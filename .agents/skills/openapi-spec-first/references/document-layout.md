# The document, file by file

The shape `examples/spring-orders/openapi/` has, and the conventions `nxgt-ktor` and `nxgt-rest` use
for the same thing. Written by hand, not fetched — this is the repo's own convention.

## The tree

```
openapi/
  openapi.yaml            info, servers, tags, the paths map, securitySchemes — $ref wiring, zero inline schemas
  api-docs.yaml           the bundle: redocly's output, committed, and what specFile names
  paths/                  one file per URL, snake_case, braces stripped
                          /orders/{id}/status → orders_id_status.yaml
  components/schemas/     PascalCase, basename == component name: Order.yaml, OrderPage.yaml
  components/responses/   PascalCase by HTTP meaning: BadRequest.yaml, NotFound.yaml, Conflict.yaml
  components/parameters/  lowercase, named after the parameter: id.yaml, size.yaml, cursor.yaml
  components/security/    PascalCase scheme name: Bearer.yaml
```

| Layer | Convention | Example |
| --- | --- | --- |
| `paths/` | snake_case, URL segments joined by `_`, path-template braces stripped, kebab → `_` | `/auth/refresh-token` → `auth_refresh_token.yaml` |
| `components/schemas/` | PascalCase; the basename is the component name in the bundle | `Order.yaml` → `#/components/schemas/Order` |
| a request body | the operation's name in PascalCase, suffixed **`Request`** | `placeOrder` → `PlaceOrderRequest.yaml` |
| `components/responses/` | PascalCase by what the status *means*, not by its number | `ResourceDeleted.yaml` |
| `components/parameters/` | lowercase, the parameter's own name | `cursor.yaml` |
| bundle | always `api-docs.yaml`, sibling of `openapi.yaml`, committed | |

## Naming a body, and what the suffix is doing

**A request body schema is named after its operation, with a `Request` suffix.** `placeOrder` takes
`PlaceOrderRequest`, `changeStatus` takes `ChangeStatusRequest`. Two reasons, and the second is the
one that bites:

- A schema is a noun and an `operationId` is a verb phrase, so an unsuffixed `PlaceOrder` reads as a
  *command* — and generated, it is a Kotlin class that looks like a use case sitting next to a
  function of nearly the same name. `service.placeOrder(PlaceOrderRequest(…))` says which is which.
- It keeps the body's name tied to the operation it belongs to. A body is not a resource: two
  operations on `/orders` may send different shapes, and naming both after the resource forces a
  choice between an unhelpfully generic name and a wrong one.

**Name it after the operation, not after the resource.** `PATCH /orders/{id}/status` is
`changeStatus`, so its body is `ChangeStatusRequest` — not `UpdateOrderRequest`, which describes a
different operation and would leave the schema and the `operationId` telling two stories. If the name
reads wrong, the `operationId` is the thing to reconsider; the schema follows it.

**`Request` and `Response` are not symmetrical here, on purpose.** `OrderResponse` is the *envelope*
— `{ data: Order }`, matching what the controller actually returns — while `PlaceOrderRequest` is the
body itself, unenveloped, because that is what a client sends. Do not "fix" the asymmetry by wrapping
requests: the envelope exists so a response can carry `metadata`, and a request has none.

## Reusable parameters, and one place they stop working

**Every parameter used more than once is a file in `components/parameters/`**, `$ref`d from each
operation. `id.yaml` is written once and referenced by every operation on `/orders/{id}` and
`/orders/{id}/status`; a parameter's description, type and `required` then have one home, and a
change to paging cannot land on three of the four operations that page.

**But `$ref` them per operation, never from the path item.** OpenAPI lets a path item declare
`parameters` that apply to all its verbs, which would let `/orders/{id}` name `id` once for both
`get` and `delete`. **This generator ignores them**, and it does so silently: it emits
`findOrder(): OrderResponse` and `cancelOrder()` with no `id` parameter and no warning. It was caught
here only because a hand-written controller then failed to override anything — an API with no
implementor to contradict it would have shipped a route that drops its path variable. Verified
against `spring-orders` on the 3.1 document; `docs/openapi-support.md` records it.

## The root

Paths `$ref` a whole path-item file, relative and with no leading `./`. Nothing else is in it.

```yaml
openapi: 3.1.0
info:
  title: Spring Orders
  description: A demo order book on stx-spring-boot — keyset paging, an audit trail and translated failures.
  version: 1.0.0
servers:
  - url: http://localhost:8080
    description: Dev server
tags:
  - name: orders-controller
    description: Placing, reading and moving orders along
  - name: health-controller
    description: Whether the application is up
paths:
  /orders:
    $ref: paths/orders.yaml
  /orders/{id}:
    $ref: paths/orders_id.yaml
  /orders/{id}/status:
    $ref: paths/orders_id_status.yaml
```

**A tag per endpoint group, named `<resource>-controller`**, because that is what becomes an
interface: `orders-controller` → `IOrdersService`.

## A path file

Every verb on one URL, in one file. `$ref`s go up and across into `components/`.

```yaml
get:
  tags:
    - orders-controller
  summary: One order.
  operationId: findOrder
  parameters:
    - $ref: ../components/parameters/id.yaml
  responses:
    '200':
      description: The order that matches the id.
      content:
        application/json:
          schema:
            $ref: ../components/schemas/OrderResponse.yaml
    '404':
      $ref: ../components/responses/NotFound.yaml
    '500':
      $ref: ../components/responses/InternalError.yaml
delete:
  tags:
    - orders-controller
  summary: Cancel an order.
  operationId: cancelOrder
  parameters:
    - $ref: ../components/parameters/id.yaml
  responses:
    '204':
      $ref: ../components/responses/ResourceDeleted.yaml
    '404':
      $ref: ../components/responses/NotFound.yaml
```

## A schema, a response, a parameter

`required` before `properties`, a `description` on everything, and a sibling `$ref` for a nested
component.

```yaml
# components/schemas/OrderPage.yaml — the envelope, which is part of the contract
type: object
description: One page of orders — the rows in `data`, the cursors in `metadata`.
required: [ data, metadata ]
properties:
  data:
    type: array
    description: The rows on this page.
    items:
      $ref: ./Order.yaml
  metadata:
    $ref: ./PageInfo.yaml
```

```yaml
# components/schemas/OrderStatus.yaml — an enum is its own file
type: string
description: Where an order has got to.
enum:
  - PENDING
  - PAID
  - SHIPPED
  - CANCELLED
```

```yaml
# components/responses/NotFound.yaml — one per HTTP meaning, all pointing at one error schema
description: No order has that id.
content:
  application/json:
    schema:
      $ref: ../schemas/ErrorResponse.yaml
```

```yaml
# components/responses/ResourceDeleted.yaml — a body-less response is a description and nothing else
description: The order was cancelled. No body.
```

```yaml
# components/parameters/cursor.yaml
name: cursor
in: query
description: |
  Where a keyset page resumes from — the `endCursor` of the previous page, absent on the first.
  Opaque, and one issued for a differently sorted query is refused rather than followed.
schema:
  type: string
```

## Redocly

`redocly.yaml` at the repo root, one alias per module, so the commands name an API rather than a path.

```yaml
extends:
  - recommended

apis:
  orders@v1:
    root: ./examples/spring-orders/openapi/openapi.yaml
    rules:
      no-ambiguous-paths: error
      security-defined: off
      no-server-example.com: off
      info-license: off
```

```bash
redocly lint orders@v1
redocly bundle orders@v1 -o examples/spring-orders/openapi/api-docs.yaml
redocly preview-docs orders@v1 -p 8086
```

**Turn a `recommended` rule off with a comment saying why, and never by making the document lie.**
`security-defined` is off in `spring-orders` because the application authenticates nobody; declaring
a scheme it does not enforce to satisfy a linter is worse than the warning. `info-license` is off
because there is no LICENSE to point at, and `no-server-example.com` because the server really is
localhost.

**A single inapplicable operation goes in `.redocly.lint-ignore.yaml`, not in a rule override** —
`redocly lint --generate-ignore-file` writes it, and it is generated, so regenerate rather than edit.
`spring-orders` has one entry: `/health` has no 4XX, because it has no parameters and no body to get
wrong.
