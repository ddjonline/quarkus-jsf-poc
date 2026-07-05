# 00 — Project Overview & Architecture

> **FreightTrack** — a Quarkus + Jakarta Server Faces (JSF) proof-of-concept
> shipment tracking web app that is responsive across mobile and desktop
> browsers, load balanced round-robin across two Quarkus instances, with a
> shared session backed by Redis.

## 1. Goal

Deliver a working POC that proves:

1. **JSF on Quarkus** renders a responsive UI matching the four wireframes in
   `.agents/designs`.
2. **Round-robin load balancing** (HAProxy) distributes requests across **two**
   identical Quarkus instances with **no LB stickiness**.
3. **Session continuity** is preserved regardless of which instance serves a
   request, because session state lives in **Redis** (shared session) and JSF
   view state is saved **client-side**.

## 2. What the app does (from the wireframes)

FreightTrack lets a user look up freight shipments by **PRO number**:

| Wireframe | Screen | Purpose |
|-----------|--------|---------|
| design1 (mobile A/B) | Search & Input | Enter 1–10 PRO numbers, add/remove rows, `Find My Shipment(s)` |
| design2 (mobile C/D) | Results list + Expanded detail | Collapsed result cards with status badge; expand to full detail + timeline |
| design3 (browser A/B) | Search & Input | Same as design1, desktop responsive layout |
| design4 (browser C/D) | Results + detail (2-column) | Same as design2, desktop 2-column detail layout |

**Demo PRO numbers:** `4821763` (In Transit) and `3390045` (Delivered).

Key UI behaviors:
- PRO number counter `n/10`, `+ Add Number`, per-row remove (`x`).
- Primary button label changes with count: `Find My Shipment` → `Find My Shipments (2)`.
- Button is muted/disabled while inputs are empty, active (dark green) when populated.
- Result card: PRO, origin → destination, status badge (In Transit / Delivered), expandable chevron.
- Detail view sections: **Shipment Details**, **Pickup & Driver**, **Location & Delivery**, **Shipment Timeline** (ordered events with completed/current markers).
- Empty state: bullseye icon + "Track your freight in real time" helper text.

## 3. Target technology stack

| Concern | Choice | Notes |
|---------|--------|-------|
| Runtime framework | **Quarkus 3.37.1** | Servlet stack required for JSF |
| Web/UI framework | **JSF (Jakarta Faces 4.x)** via **Quarkiverse `quarkus-myfaces`** | Apache MyFaces on Quarkus |
| Servlet container | **`quarkus-undertow`** | Pulled in by the JSF extension |
| JDK | **Java 25** | Toolchain-pinned |
| Secondary language | **Kotlin 2.4.0** | Domain, services, config, Redis session layer |
| JSF managed beans | **Java** | Cleanest CDI/JSF proxy interop (see §5) |
| Build | **Gradle (Kotlin DSL)** | Mixed Java + Kotlin source sets |
| Session store | **Redis** via `quarkus-redis-client` | Shared application session |
| Data store | **PostgreSQL** via `quarkus-jdbc-postgresql` + Hibernate ORM/Panache | Source of shipment data; seeded by SQL init scripts |
| Load balancer | **HAProxy** | `balance roundrobin`, no stickiness |
| Orchestration | **Docker Compose** | 2× app, 1× redis, 1× postgres, 1× haproxy |
| Styling | Custom CSS (mobile-first, responsive) | Matches the green/gold FreightTrack theme |
| Browser E2E | **Playwright** | UI parity, responsive behavior, and Redis session continuity through HAProxy |

## 4. Runtime architecture

```
                         ┌────────────────────────────┐
                         │          Browser            │
                         │  (mobile viewport / desktop)│
                         └──────────────┬──────────────┘
                                        │ HTTP :8080
                                        ▼
                         ┌────────────────────────────┐
                         │          HAProxy            │
                         │   balance roundrobin        │
                         │   (NO sticky cookie)        │
                         └───────┬──────────────┬──────┘
                       req n ──► │              │ ◄── req n+1
                                 ▼              ▼
                    ┌────────────────┐  ┌────────────────┐
                    │  quarkus-app-1 │  │  quarkus-app-2 │
                    │  JSF + MyFaces │  │  JSF + MyFaces │
                    │  state=client  │  │  state=client  │
                    └──┬──────────┬──┘  └──┬──────────┬──┘
             session   │          │        │          │  session
             (R/W)     │          │ data   │ data     │  (R/W)
                       ▼          │ (read) │ (read)   ▼
              ┌────────────────┐  │        │  ┌────────────────┐
              │     Redis      │  ▼        ▼  │   (same Redis)  │
              │  session store │ ┌──────────┐ └────────────────┘
              └────────────────┘ │ Postgres │
                                 │ shipments│  ◄── seeded by SQL
                                 │  (data)  │       init scripts
                                 └──────────┘
```

## 5. Key architectural decisions (ADR summary)

- **ADR-1 — JSF impl = Apache MyFaces (`quarkus-myfaces`).** First-class
  Quarkiverse extension with native/JVM support; MojarraSy alternative not
  packaged for Quarkus.
- **ADR-2 — Round-robin + Redis-shared session (NOT sticky sessions).**
  The requirement pairs "roundrobin" with "session affinity leveraging Redis."
  We reconcile this by making instances **stateless per request**: any node can
  serve any request because session data is externalized to Redis. HAProxy does
  round-robin with **no** `cookie` stickiness directive.
- **ADR-3 — JSF `STATE_SAVING_METHOD = client`.** JSF view state is serialized
  into the page (encrypted + MAC signed) rather than stored server-side, so a
  postback can land on either instance. Shared secret keys are configured
  identically on both instances.
- **ADR-4 — Application session data in Redis.** The user's working set
  (entered PRO numbers, last search results, expanded card) is stored in Redis
  keyed by a session id cookie, via a thin Kotlin `RedisSessionStore`. This is
  what provides "session affinity" semantics under round-robin.
- **ADR-5 — Managed beans in Java, everything else Kotlin.** JSF/CDI relies on
  proxyable no-arg constructors and property accessors; Java beans avoid the
  `all-open`/`no-arg` plugin friction. Kotlin covers domain, services, Redis,
  and config where it is most expressive.
- **ADR-6 — Shipment data in PostgreSQL, seeded by SQL init scripts.** Shipment
  data is served from a Postgres container instead of an in-memory map. The
  schema and demo rows (2 shipments + timeline events) are created by `.sql`
  scripts mounted into the Postgres image's `/docker-entrypoint-initdb.d`, so
  the database is self-seeding on first start. The Quarkus datasource
  (URL / user / password) is configured **exclusively via environment
  variables**. Both app instances read the same database — data is identical
  regardless of which node serves a request.

## 6. Non-goals (POC scope)

- Real carrier integrations / external freight API.
- Authentication / authorization.
- Real-time push (WebSocket) location updates — timeline is static seeded data.
- Production hardening (TLS termination, secrets management, DB migrations
  tooling, HA Redis/Postgres clusters).

## 7. Plan document index (execute in order)

| # | File | Deliverable |
|---|------|-------------|
| 00 | `00-overview.md` | This document |
| 01 | `01-domain-model.md` | Object model derived from the designs |
| 02 | `02-project-setup.md` | Gradle Kotlin DSL project, Java 25 + Kotlin 2.4.0, dependencies |
| 03 | `03-jsf-integration.md` | MyFaces config, Facelets templates, responsive shell |
| 04 | `04-session-redis.md` | Redis-shared session + JSF client state saving |
| 05 | `05-ui-implementation.md` | Views/beans mapped to each wireframe |
| 06 | `06-services-data.md` | Shipment service + Postgres-backed repository |
| 07 | `07-docker-compose.md` | 2× Quarkus + Redis + Postgres + HAProxy round-robin |
| 08 | `08-testing-validation.md` | Verify round-robin + Playwright browser E2E/session continuity |

## 8. Definition of done

- `docker compose up` starts 5 containers (app1, app2, redis, postgres, haproxy).
- Postgres is seeded from SQL init scripts on first start (2 shipments +
  timeline events); apps connect using env-supplied URL/user/password.
- Browsing `http://localhost:8080` shows the FreightTrack search screen.
- Entering `4821763` + `3390045` and searching shows 2 result cards with correct
  statuses; expanding shows full detail + timeline.
- Repeated requests hit both app instances (verified via a response header
  showing the serving instance id) yet the session (entered PROs / results)
  persists — proving Redis-backed continuity under round-robin.
- Playwright E2E proves the core search/results/expand flow, session continuity
  through HAProxy, and responsive layout at 375px and ≥1024px.
