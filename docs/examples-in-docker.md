# Examples in Docker

Seven examples run in a container, on the workspace's own databases, behind its Traefik:

| Example | Kind | Database | Address |
| --- | --- | --- | --- |
| `jpa-shop` | Ktor server | Postgres — its own role and database | <http://jpa-shop.localhost/products> |
| `spring-orders` | Spring Boot server | Mongo `rs0` — its own user | <http://spring-orders.localhost/orders> |
| `graphix-shop` | Ktor GraphQL server | none | <http://graphix-shop.localhost/sandbox> |
| `demo-api` | Ktor server | none | <http://demo-api.localhost/failures/typed> |
| `demo-client` | runs once | calls `demo-api` | — |
| `demo-spring-client` | runs once | calls `demo-api` | — |
| `workflow-checkout` | runs once | Redis, database 15 | — |

The others are not applications: `artifacts-*` and `graphix-codegen` are checks on published
artifacts and generated code, and `material-demo` is a UI.

Everything is one file, the repository root's `compose.yaml`, with one `.env` beside it:

```bash
cp .env.example .env                       # fill the CHANGE_ME
docker compose --profile jpa-shop up -d    # http://jpa-shop.localhost/products
docker compose restart jpa-shop            # after an edit
docker compose --profile jpa-shop down
```

## What has to be running first

Nothing here starts a database or a proxy. The examples join an external Docker network named
`proxy` and expect to find there, by these aliases:

| Alias | What | Needed by |
| --- | --- | --- |
| `postgres` | PostgreSQL, with a superuser | `jpa-shop` |
| `mongo1:27017`, `mongo2:27018` | a MongoDB replica set `rs0`, with authentication and a root user | `spring-orders` |
| `redis` | Redis, no authentication | `workflow-checkout` |
| — | Traefik, with its Docker provider on `proxy` and a plain-http entrypoint `web` | every server |

`.env` can point each one elsewhere (`POSTGRES_HOST`, `MONGO_SEEDS`, `MONGO_REPLICA_SET`,
`REDIS_HOST`, and the `TRAEFIK_*` trio). Servers that publish no port on the host are reachable this
way and no other: a `./kotlin run` of `jpa-shop` or `spring-orders` from a shell does not reach
them, the container does.

Every `*.localhost` name resolves to this machine with nothing in `/etc/hosts`. The `web` entrypoint
must serve plain http: one that redirects to `https` leaves every hostname answering 404.

## Two profiles per example

Every example has two, named after it: `jpa-shop` and `jpa-shop-prod`, `spring-orders` and
`spring-orders-prod`, and so on. Run one example at a time, and one of its two profiles: both answer
to the same hostname. There is no profile for everything at once, because a dev container holds a
compile.

- **`<example>`**, dev, mounts the checkout at `/workspace`, and `dev-run.sh` packages the module there and then
  `exec`s `java -jar` on the result — so the toolchain exits once the compile is done and the
  application is the only process left (Memory, below). An edit is a `docker compose restart <example>`
  away, and the restart compiles incrementally: the build output is a
  volume of its own, kept between restarts, and never the host's `build/` — a Linux build writing
  there would invalidate the IDE's and macOS's. One image, `krepo-examples-dev`, serves every
  example, since nothing of the repository is copied into it.
- **`<example>-prod`** builds `./kotlin package -f executable-jar` into an image, `krepo-examples/<module>`,
  and runs it on a JRE, with `-XX:MaxRAMPercentage=75` under a 768 MiB limit. It is what a deployment
  would run, and it is the profile that found the bug below. Nothing restarts it on its own: it runs
until `docker compose down`.

`compose.yaml` writes each example once, in one of two shapes, and its two services merge that with
`x-dev` or `x-prod`:

- a **server** gets an `x-<example>` block: its environment, its Traefik router, what it depends
  on, and — `demo-api` — its network alias and healthcheck;
- a **run-once** example gets only an `x-<example>-env` map.

A new example is one of those and two services; one with a database also gets an
`<example>-db-init` service in both its profiles.

`examples/docker/` holds what the services run: the `Dockerfile` (targets `dev` and `prod`),
`dev-run.sh`, and the two database scripts. All three are shell rather than the TypeScript of
`scripts/`, because they run inside images that have no bun: the Temurin dev image, `postgres:17`
and `mongo:8`. `dev-run.sh` is read from the mount rather than copied into the image, so an edit to
it needs no rebuild.

### mavenLocal is part of the build

The examples resolve `io.github.softistx:*` as published artifacts — the `stx-artifacts` template
explains why. So both profiles read the host's `~/.m2/repository`, read-only: `dev` mounts it, and
`prod` lends it to the build step as the `m2` build context without copying it into a layer. After
changing a library, publish it before starting an example, or the example runs the old one:

```bash
./kotlin publish mavenLocal -m stx-graphix --non-transitive
```

### Caches

The toolchain's distribution, compiler and dependency jars live in one volume,
`krepo-kotlin-cache`, shared by every `dev` service. The first `dev` start downloads them; the next
example reuses them. The `prod` build keeps its own BuildKit cache for the same files.

## The databases

`<example>-db-init` runs before the application in either of its profiles, exits, and is what the application
`depends_on`. Both scripts are idempotent: a second `up` changes nothing but the password, which is
set every time so that editing `.env` takes effect.

- **Postgres** (`postgres-init.sh`, as the superuser): creates the role and the database it owns.
  Nothing is ever dropped. `jpa-shop` then creates its own tables, since it runs with `CREATE_DROP`.
- **Mongo** (`mongo-init.sh`, as root): creates a user in `admin` with `readWrite` on the example's
  databases and nothing else — `spring_orders` for the orders and the migration ledger,
  `spring_orders_telemetry` for `stx.telemetry.mongo`. The replica set enforces authentication, so
  the connection string carries the user and `authSource=admin`; keep its password hex, because it
  goes into that string unencoded.
- **Redis** needs nothing. `workflow-checkout` writes to database 15 under a namespace it deletes on
  the way out.

The administrator's credentials are only ever in the root `.env`, which git ignores, and only the
`-db-init` services receive them: the Postgres superuser's password, and the replica set's root
user and password — whatever the servers were started with.

## Running the clients

`demo-client` and `demo-spring-client` call `http://demo-api:8080/`, the alias `demo-api` holds on
`proxy` in either profile. Each client's profile includes demo-api, and the client waits for its
healthcheck — the port accepting a connection, checked with bash's `/dev/tcp` because the JRE image
has no curl. `--exit-code-from` stops demo-api again once the client is done and returns the
client's exit code; without it, compose stays attached to the server after the client exits.

```bash
docker compose --profile demo-client up --exit-code-from demo-client
``` Each client creates
what it reads, so either runs against a fresh server, in any order.

The clients, like `workflow-checkout`, run once, print what they did, and exit with
`restart: "no"` — `docker compose --profile <example> up` again runs them again.

## Memory

`dev` used to be `./kotlin run`, and that keeps the toolchain's JVM alive as the application's
parent for as long as the application runs. Measured on `jpa-shop`, 2026-10-04:

| `dev` container | settled | ready after `restart` |
| --- | --- | --- |
| `./kotlin run` — toolchain 450 MiB + application 370 MiB | 730–860 MiB | 7–8 s |
| `dev-run.sh` — package, then `exec java -jar` | 325 MiB | 7 s unchanged, 12 s after an edit |

`spring-orders` settles at 435–455 MiB the same way. The compile is now the peak, and only lasts
while it runs: a first compile into an empty build directory sampled 780 MiB for `jpa-shop` and
1,000 MiB for `spring-orders`, a recompile 590 MiB. `dev` is bounded at 1.5 GiB so
that a runaway example is killed by docker rather than by the host's OOM killer, which picks its own
victim. The application's heap is `APP_JAVA_OPTS` (`-XX:MaxRAMPercentage=50`), not
`JAVA_TOOL_OPTIONS`, which would reach the toolchain's JVM too. A `prod` container measured
320–390 MiB; its heap setting, `-XX:MaxRAMPercentage=75`, is in the image's entrypoint, so the dev
image — another target of the same Dockerfile — never inherits it. Still, `docker compose down` what you are done with.

## What it measured

- The Temurin image has neither `curl` nor `wget`, and `./kotlin` needs one to download its
  distribution: *"Please install 'wget' or 'curl'"*. The `toolchain` stage installs curl.
- `./kotlin package -f executable-jar` writes
  `tasks/_<module>_executableJarJvm/<module>-jvm-executable.jar` under the build directory —
  `/build/<module>` in dev; the
  `prod` stage copies it from there and `dev-run.sh` runs it from there — two readers of one path.
- `graphix-shop`'s executable jar started with no schema documents at all and failed on
  `Serializer for class 'Any' is not found`: `stx-graphix` found `classpath:graphql/` on disk and in
  a jar named by `java.class.path`, but not inside a Spring Boot executable jar, where the class
  loader is the only thing that can name `BOOT-INF/classes/`. `docs/graphix.md` has the fix.
