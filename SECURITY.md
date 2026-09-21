# Security policy

## Reporting a vulnerability

**Do not open a public issue.** Report it privately through
[GitHub Security Advisories](https://github.com/softistx/nxgt-krepo/security/advisories/new),
which lets us discuss and fix the problem before it is visible.

Tell us which library and version, what an attacker can do, and the smallest way to reproduce it.
A failing spec is the clearest form of that — this repository's specs run against real backing
services through `libs/core/stx-testing`, so a reproduction usually fits in one `FeatureSpec`.

You should get a first reply within a week. If a report is accepted, we will agree a disclosure
date with you and credit you in the advisory and the release notes unless you ask us not to.

## Supported versions

The libraries are pre-1.0 and each **family** — a library and its framework integrations, such as
`stx-jpa`, `stx-jpa-ktor` and `stx-jpa-spring` — carries its own version. **Only the latest release
of the affected family is supported**: a fix ships in that family's next version, never as a patch
to an older one. Tell us the version of the artifact you are on; `docs/releasing.md` explains how
one is cut and `docs/consuming.md` how the versions relate.

## What is in scope

Every library under `libs/`, as published. In particular the parts that decide who may do what, or
that put a credential or a user's data somewhere:

- `stx-spring-boot` — security, CORS, the error bodies a client sees
- `stx-ktor` — the CORS policy it shares with the above
- `stx-storage` — presigned URLs and upload forms, which delegate authority to a browser
- `stx-jpa`, `stx-mongo` — query construction, and therefore injection
- `stx-migrations` — the lease that keeps two instances from running one migration twice
- `stx-telemetry` — what ends up in a log or a span, which is where secrets leak by accident

The modules under `examples/` and `server/` are demonstrations, not products: they are not published
and are not in scope. Neither is a dependency's own vulnerability — report those upstream — though
we do want to hear about one we pin or re-export.
