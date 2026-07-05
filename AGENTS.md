# AGENTS.md

FreightTrack — a Quarkus + JSF shipment-tracking POC. Responsive UI, HAProxy
round-robin across 2 Quarkus instances, Redis-shared session, Postgres data.
Detailed specs live in `.agents/plans/` (execute in numeric order). Designs in
`.agents/designs/`.

## Tech stack (do not deviate without updating the plans)
- **Quarkus 3.37.1**, **Java 25** (toolchain-pinned), **Kotlin 2.4.0**
- **JSF / Jakarta Faces** via Quarkiverse `quarkus-myfaces` (Apache MyFaces + Undertow)
- **Gradle (Kotlin DSL)**, mixed `src/main/kotlin` + `src/main/java`
- **Redis** (`quarkus-redis-client`) — shared session store
- **PostgreSQL** (`quarkus-jdbc-postgresql` + Hibernate ORM Panache Kotlin) — data
- **Docker Compose**: app1, app2, redis, postgres, haproxy; **HAProxy** `balance roundrobin`, no stickiness

## Language split
- **Kotlin**: domain, `data/` (JPA entities + Panache repos), services, session/Redis, config.
- **Java**: JSF managed beans only (cleanest CDI/JSF proxy interop).
- Kotlin scoped beans/entities require `allOpen` + `noArg` plugins (see `02-project-setup.md`).

## Package layout (`app.freighttrack`)
`domain/` · `data/` · `service/` · `session/` (Kotlin) · `web/` (Java JSF beans)

## Non-negotiable architecture rules
- **Round-robin safe**: no request may depend on instance-local heap state.
  - JSF `STATE_SAVING_METHOD=client`; identical `SECRET`/`MAC_SECRET` on both instances.
  - App session (`TrackingSearch`) lives in **Redis**, keyed by `FT_SESSION` cookie (NOT used for LB routing).
  - JSF beans are `@RequestScoped`; hydrate from Redis → act → persist to Redis each request.
- **Config via env only** — never hard-code creds/secrets:
  `FT_DB_URL`, `FT_DB_USER`, `FT_DB_PASSWORD`, `FT_REDIS_HOST`, `FT_JSF_SECRET`, `FT_JSF_MAC_SECRET`, `FT_INSTANCE_ID`.
- **DB owns the schema**: seeded by SQL init scripts (`docker/postgres/initdb/`); Hibernate `schema-management.strategy=none` in prod/compose. Re-seed via `docker compose down -v`.
- Domain types are immutable, `Serializable` POJOs; never leak JPA entities/proxies into the Redis session — map entities → domain in the repository.

## Domain / frontend rules
- **PRO number**: numeric only, 1–11 digits, **zero left-padded to 11** for the canonical lookup key (matches padded `pro_number` in DB). Validate client- and server-side.
- **1–10 PRO inputs** per search (`MAX_PRO_NUMBERS=10`).
- Each PRO = a **separate, asynchronous, parallel** lookup; results render in an **accordion**, each panel updated **independently** via per-item `LookupState` (PENDING/LOADING/LOADED/NOT_FOUND/ERROR).
- One responsive Facelets view (CSS breakpoints), not separate mobile/desktop pages.

## Quality requirements
- Follow the FreightTrack theme + wireframes exactly (green/gold; badges; timeline markers).
- Usable at 375px (mobile) and ≥1024px (desktop); no horizontal scroll on mobile; touch targets ≥44px.
- Deterministic, seeded demo data: `4821763` (IN_TRANSIT), `3390045` (DELIVERED).
- Tests: Kotlin `@QuarkusTest` + Dev Services (Postgres/Redis auto-provisioned). Cover repo/service logic, PRO validation/padding, session round-trip, dedupe/order.
- Prefer editing existing files; keep changes minimal and plan-aligned.

## Build & run
```
./gradlew build              # compile mixed Kotlin/Java
./gradlew quarkusDev         # local dev (Dev Services start Postgres + Redis)
./gradlew test               # unit/integration tests
docker compose build && docker compose up -d   # full topology (5 containers)
open http://localhost:8080
```

## Verify (see `08-testing-validation.md`)
- Round-robin: repeated requests alternate `X-Ft-Instance: app-1/app-2`.
- Session continuity: same `FT_SESSION` cookie across instances retains state (Redis).
- DB seeded: `psql ... SELECT pro_number,status FROM shipment;` → 2 rows.
- Graceful degradation when Redis or Postgres is stopped (no crash loop).
