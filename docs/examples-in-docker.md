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

Everything is one file, the repository root's `compose.yaml`, with one `.env` beside it. Development
happens in one dev container, `workspace`; each example also has a `-prod` profile that bakes it into
an image:

```bash
cp .env.example .env                       # fill the CHANGE_ME
docker compose up -d                       # COMPOSE_PROFILES=workspace: the dev container
docker compose exec workspace bash         # or attach the IDE, below
./kotlin run -m jpa-shop                   # inside: http://jpa-shop.localhost/products
docker compose down
docker compose --profile jpa-shop-prod up -d   # an example's image instead, for this run only
```

`COMPOSE_PROFILES` in `.env` is what a bare `up` or `down` acts on. Without it every service has a
profile, so a bare `up` answers `no service selected`.

## What has to be running first

Nothing here starts a database or a proxy. The services join an external Docker network named
`proxy` and expect to find there, by these aliases:

| Alias | What | Needed by |
| --- | --- | --- |
| `postgres` | PostgreSQL, with a superuser | `jpa-shop` |
| `mongo1:27017`, `mongo2:27018` | a MongoDB replica set `rs0`, with authentication and a root user | `spring-orders` |
| `redis` | Redis, no authentication | `workflow-checkout` |
| — | Traefik, with its Docker provider on `proxy` and a plain-http entrypoint `web` | every server |

`.env` can point each one elsewhere (`POSTGRES_HOST`, `MONGO_SEEDS`, `MONGO_REPLICA_SET`,
`REDIS_HOST`, and the `TRAEFIK_*` trio). Servers that publish no port on the host are reachable this
way and no other: a `./kotlin run` of `jpa-shop` or `spring-orders` from a macOS shell does not reach
them, the dev container does.

Every `*.localhost` name resolves to this machine with nothing in `/etc/hosts`. The `web` entrypoint
must serve plain http: one that redirects to `https` leaves every hostname answering 404.

## The dev container

`workspace` mounts the checkout at `/workspace` and holds what working in it needs: JDK 25, the
toolchain's wrapper, git, and bun at the host's version. Nothing of the repository is copied into
its image, `krepo-workspace`, so it is rebuilt only when `docker/Dockerfile` changes.

- **The IDE** attaches through `.devcontainer/devcontainer.json`: in IntelliJ, *Remote Development →
  Dev Containers*, then the repository's folder. The IDE's backend then runs in the container, sees
  the same network as the examples, and its terminal and run configurations run there.
- **Any module** runs from that terminal with `./kotlin run -m <module>`, tests with `./kotlin test`.
  The container carries every example's settings at once — the Postgres and Mongo URIs and
  credentials, the Redis URI — so nothing has to be exported first.
- **Several servers at once**: each listens on a port of its own, and the container carries every
  server's Traefik router, each pointing at that port:

  | Example | Port | Address |
  | --- | --- | --- |
  | `graphix-shop` | 8081 | <http://graphix-shop.localhost/sandbox> |
  | `jpa-shop` | 8082 | <http://jpa-shop.localhost/products> |
  | `spring-orders` | 8083 | <http://spring-orders.localhost/orders> |
  | `demo-api` | 8084 | <http://demo-api.localhost/failures/typed> |

  `PORT` overrides each. The two clients call `http://127.0.0.1:8084/`, so they find a `demo-api`
  running beside them.
- **The build output** is a volume, `krepo-workspace-build`, mounted over the checkout's `build/`:
  that directory belongs to the host's IDE and `./kotlin` on macOS, and a Linux build writing there
  would invalidate both.
- **mavenLocal** is the host's `~/.m2/repository`, mounted writable, so a `./kotlin publish
  mavenLocal` in the container is what the host and the `-prod` builds see too.
- **Memory**: the container is bounded by `WORKSPACE_MEM_LIMIT`, 6 GiB by default, and holds the IDE
  backend, the toolchain and whatever runs. Docker's VM needs room for that beside the databases and
  Traefik — 10 GiB for the default. A runaway build is then killed by docker rather than by the
  host's OOM killer, which picks its own victim.

`jpa-shop-db-init` and `spring-orders-db-init` run with it (The databases, below). The dev container
replaced a dev profile per example, which compiled one module and could run only that one.

## The `-prod` profiles

`<example>-prod` builds `./kotlin package -f executable-jar` into an image,
`krepo-examples/<module>`, and runs it on a JRE, with `-XX:MaxRAMPercentage=75` under a 768 MiB
limit. It is what a deployment would run, and it is the profile that found the bug below. It
answers to the same hostname as the dev container's copy, so run one or the other. Nothing restarts
it on its own: it runs until `docker compose down`.

`compose.yaml` writes each example once and reads it twice: a server's `x-<example>` block holds its
environment and its Traefik labels — a map, so that the workspace can merge every example's — and
the run-once `workflow-checkout` has only an `x-workflow-checkout-env` map. A new example is one of
those, a line in the workspace's two merges, and a `-prod` service; one with a database also gets
an `<example>-db-init` service, in the `workspace` profile and its own.

`docker/`, beside `compose.yaml`, holds the `Dockerfile` (targets `workspace` and `prod`) and the two
database scripts. Those are shell rather than the TypeScript of `scripts/`, because they run in
`postgres:17` and `mongo:8`, which have no bun.

### mavenLocal is part of the build

The examples resolve `io.github.softistx:*` as published artifacts — the `stx-artifacts` template
explains why. So both read the host's `~/.m2/repository`: the workspace mounts it, and `prod` lends
it to the build step, read-only, as the `m2` build context without copying it into a layer. After
changing a library, publish it before running an example, or the example runs the old one:

```bash
./kotlin publish mavenLocal -m stx-graphix --non-transitive
```

### Caches

The toolchain's distribution, compiler and dependency jars, and the IDE backend's caches and
indexes, live in one volume, `krepo-kotlin-cache`, mounted at the workspace's `/root/.cache`. The
first start downloads them; every later one reuses them. The `prod` build keeps its own BuildKit
cache for the same files.

## The databases

`<example>-db-init` runs with the workspace, and before the image in the example's `-prod`
profile, which `depends_on` it; then it exits. Both scripts are idempotent: a second `up` changes nothing but the password, which is
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

In the dev container, start `demo-api` and run a client beside it:

```bash
./kotlin run -m demo-api &
./kotlin run -m demo-client
```

`--profile demo-client-prod` brings up `demo-api-prod` with it, under the alias `demo-api`, and the
client waits for its healthcheck — the port accepting a connection, checked with bash's `/dev/tcp`
because the JRE image has no curl. `--exit-code-from` stops demo-api again once the client is done
and returns the client's exit code:

```bash
docker compose --profile demo-client-prod up --exit-code-from demo-client-prod
```

Each client creates what it reads, so either runs against a fresh server, in any order. The
clients, like `workflow-checkout`, run once, print what they did, and exit.

## Memory

A `-prod` container measured 320–390 MiB; its heap setting, `-XX:MaxRAMPercentage=75`, is in the
image's entrypoint, so the workspace image — another target of the same Dockerfile — never
inherits it. In the dev container, `./kotlin run` keeps the toolchain's JVM alive as the
application's parent: measured on `jpa-shop`, 2026-10-04, the toolchain 450 MiB and the application
370 MiB. A first compile into an empty build directory peaked at 780 MiB for `jpa-shop` and 1,000
MiB for `spring-orders`. Still, `docker compose down` what you are done with.

## What it measured

- The Temurin image has neither `curl` nor `wget`, and `./kotlin` needs one to download its
  distribution: *"Please install 'wget' or 'curl'"*. The `toolchain` stage installs curl.
- `./kotlin package -f executable-jar` writes
  `tasks/_<module>_executableJarJvm/<module>-jvm-executable.jar` under the build directory, and the
  `prod` stage copies it from there.
- `graphix-shop`'s executable jar started with no schema documents at all and failed on
  `Serializer for class 'Any' is not found`: `stx-graphix` found `classpath:graphql/` on disk and in
  a jar named by `java.class.path`, but not inside a Spring Boot executable jar, where the class
  loader is the only thing that can name `BOOT-INF/classes/`. `docs/graphix.md` has the fix.
