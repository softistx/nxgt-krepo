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

```bash
cd examples/jpa-shop
cp .env.example .env      # fill the CHANGE_ME
docker compose up -d
curl http://jpa-shop.localhost/products
```

## What has to be running first

Nothing here starts a database or a proxy. The examples join the external `proxy` network and reach
the servers there by alias — `postgres`, `mongo1`/`mongo2`, `redis` — so the workspace's own
compose projects (nxgt-docker's `traefik`, `database/postgres`, `database/mongo`,
`database/redis`) have to be up. Those servers publish no port on the host, which is also why a
`./kotlin run` of `jpa-shop` or `spring-orders` from a shell does not reach them: the container is the
way in.

Every `*.localhost` name resolves to this machine with nothing in `/etc/hosts`. Traefik serves it on
the `web` entrypoint over plain http, which needs nxgt-docker's `traefik/docker-compose.dev.yaml`
included — without it `web` redirects everything to `https` and each hostname answers 404.

## Two profiles

`COMPOSE_PROFILES` in the example's `.env` picks one; set exactly one.

- **`dev`** mounts the checkout at `/workspace`, and `dev-run.sh` packages the module there and then
  `exec`s `java -jar` on the result — so the toolchain exits once the compile is done and the
  application is the only process left (Memory, below). An edit is a `docker compose restart dev`
  away, and the restart compiles incrementally: the build output is a
  volume of its own, kept between restarts, and never the host's `build/` — a Linux build writing
  there would invalidate the IDE's and macOS's. One image, `krepo-examples-dev`, serves every
  example, since nothing of the repository is copied into it.
- **`prod`** builds `./kotlin package -f executable-jar` into an image, `krepo-examples/<module>`,
  and runs it on a JRE, with `-XX:MaxRAMPercentage=75` under a 768 MiB limit. It is what a deployment
  would run, and it is the profile that found the bug below. Nothing restarts it on its own: it runs
until `docker compose down`.

`examples/docker/` holds what they share: the `Dockerfile` (targets `dev` and `prod`), the
`compose.base.yaml` both services `extend`, `dev-run.sh`, and the two database scripts. All three
are shell rather than the TypeScript of `scripts/`, because they run inside images that have no bun:
the Temurin dev image, `postgres:17` and `mongo:8`. `dev-run.sh` is read from the mount rather than
copied into the image, so an edit to it needs no rebuild. Each example's
`compose.yaml` adds only its module name, its environment, its Traefik router and, where it has one,
its `db-init`.

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

`db-init` runs before the application in either profile, exits, and is what the application
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

The administrator's credentials are only ever in the example's `.env`, which git ignores, and only
`db-init` receives them. The values are the ones in nxgt-docker's `database/postgres/.env`
(`POSTGRES_PASSWORD`) and `database/mongo/.env` (`MONGO_ROOT_USERNAME`, `MONGO_ROOT_PASSWORD`).

## Running the clients

`demo-client` and `demo-spring-client` call `http://demo-api:8080/`, the alias `demo-api` holds on
`proxy` in either profile. A compose project cannot `depends_on` another, so start `demo-api` first.
The clients, like `workflow-checkout`, run once, print what they did, and exit with
`restart: "no"` — `docker compose up` again runs them again.

## Memory

`dev` used to be `./kotlin run`, and that keeps the toolchain's JVM alive as the application's
parent for as long as the application runs. Measured on `jpa-shop`, 2026-10-04:

| `dev` container | settled | ready after `restart` |
| --- | --- | --- |
| `./kotlin run` — toolchain 450 MiB + application 370 MiB | 730–860 MiB | 7–8 s |
| `dev-run.sh` — package, then `exec java -jar` | 325 MiB | 7 s unchanged, 12 s after an edit |

`spring-orders` settles at 435 MiB the same way. The compile is now the peak — 590 MiB was the
highest sample during a recompile — and only lasts while it runs. `dev` is bounded at 1.5 GiB so
that a runaway example is killed by docker rather than by the host's OOM killer, which picks its own
victim. The application's heap is `APP_JAVA_OPTS` (`-XX:MaxRAMPercentage=50`), not
`JAVA_TOOL_OPTIONS`, which would reach the toolchain's JVM too. A `prod` container measured
320–390 MiB. Still, `docker compose down` what you are done with.

## What it measured

- The Temurin image has neither `curl` nor `wget`, and `./kotlin` needs one to download its
  distribution: *"Please install 'wget' or 'curl'"*. The `toolchain` stage installs curl.
- `./kotlin package -f executable-jar` writes
  `tasks/_<module>_executableJarJvm/<module>-jvm-executable.jar` under the build directory; the
  `prod` stage copies it from there and `dev-run.sh` runs it from there — two readers of one path.
- `graphix-shop`'s executable jar started with no schema documents at all and failed on
  `Serializer for class 'Any' is not found`: `stx-graphix` found `classpath:graphql/` on disk and in
  a jar named by `java.class.path`, but not inside a Spring Boot executable jar, where the class
  loader is the only thing that can name `BOOT-INF/classes/`. `docs/graphix.md` has the fix.
