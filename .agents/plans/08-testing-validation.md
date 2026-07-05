# 08 — Testing & Validation

Prove the POC's three claims: **JSF UI parity**, **round-robin load
balancing**, and **cross-instance session continuity via Redis** — plus that
shipment data is served from **PostgreSQL** (seeded by SQL scripts). Use
Playwright for browser-realistic UI and session-flow validation.

## 1. Unit tests (Kotlin, `@QuarkusTest` where DI needed)

| Test | Asserts |
|------|---------|
| `ShipmentRepositoryTest` | Postgres-backed: `4821763` / `00004821763`=IN_TRANSIT with 3 ordered events (last=CURRENT), `3390045` / `00003390045`=DELIVERED; unknown→null; `normalizePro` strips `PRO-`, keeps digits only, trims, zero-pads |
| `ShipmentServiceTest` | `find("4821763")` → canonical `00004821763` with 3 timeline events, last=CURRENT; `find("PRO-3390045")` resolves; `find("0000000")`=null; `lookup` dedupes + filters blanks + preserves order |
| `RedisSessionStoreTest` | save→load round-trips `TrackingSearch` (JSON); TTL set; missing key → empty `TrackingSearch` |

`RedisSessionStoreTest` uses **Quarkus Dev Services for Redis**; the
`ShipmentRepository`/`ShipmentService` tests use **Quarkus Dev Services for
Postgres** — both auto-start throwaway containers in tests, no manual setup.

> **Test schema/seed:** for `@QuarkusTest`, apply the seed data via
> `quarkus.hibernate-orm.sql-load-script` (test profile). The test profile uses
> `schema-management.strategy=drop-and-create` against the ephemeral Dev Services
> DB. The import SQL (`test-import.sql`) must use **explicit column names** in
> `INSERT` statements — Hibernate's generated DDL column order varies and may not
> match the Docker SQL schema order (see plan 06 §7 note).

> **Application properties profile split:**
> ```properties
> %prod.quarkus.datasource.jdbc.url=${FT_DB_URL}             # env-only in prod
> %test.quarkus.datasource.devservices.enabled=true           # auto-provision for tests
> %test.quarkus.hibernate-orm.schema-management.strategy=drop-and-create
> %test.quarkus.hibernate-orm.sql-load-script=test-import.sql
> ```
> Without the `%test` profile prefix, Quarkus will attempt to resolve
> `${FT_DB_URL}` in tests, which fails when the env var is not set.
> Dev Services auto-configures the datasource URL/user/password automatically.

## 2. Bean/logic tests

| Test | Asserts |
|------|---------|
| `TrackingBeanTest` | `addNumber` respects max=10; `removeNumber` respects min=1; `findButtonLabel` = "Find My Shipment" for ≤1, "Find My Shipments (n)" for >1; `searchActive` false when all blank |

> **SessionContext dependency:** `TrackingBean.init()` calls `session.getId()`.
> `SessionContext.id` must be `var id: String = ""` (with default), not
> `lateinit var`. In `@QuarkusTest`, the HTTP `SessionFilter` does not run, so
> `id` is never set. A `lateinit` value throws
> `UninitializedPropertyAccessException` in every test method that accesses the
> bean.

## 3. Integration / UI smoke (RestAssured)

Run against a single instance (`@QuarkusTest`):
- `GET /tracker.xhtml` → 200, body contains "Shipment Tracker",
  "Track your freight in real time", "Demo: try 4821763 or 3390045".
- Response contains hidden `jakarta.faces.ViewState` input → confirms JSF
  active and (given config) client state saving.
- `Set-Cookie: FT_SESSION=...` present on first request; `HttpOnly`.

> Full JSF postback flows are awkward to script with RestAssured (view state +
> form ids). For the POC, cover postback behavior via `TrackingBeanTest`
> (logic) + Playwright (§6), rather than brittle HTTP form replay.

## 4. Round-robin verification (scripted, against compose)

With `docker compose up`:
```bash
for i in $(seq 1 6); do
  curl -s -o /dev/null -D - http://localhost:8080/tracker.xhtml \
    | grep -i 'x-ft-instance'
done
```
**Expect:** alternating `app-1` / `app-2` (round-robin, no stickiness).

## 5. Cross-instance session continuity (the money test)

Script with Playwright (preferred) and optionally curl with a shared cookie jar
so the *same* session id hits *both* instances.

Playwright flow against compose/HAProxy:
- Navigate to `/tracker.xhtml`, capture the first document response's
  `X-Ft-Instance` header, and assert `FT_SESSION` cookie exists.
- Enter `4821763` and `3390045`, submit, and assert 2 result cards render.
- Reload or navigate again using the same browser context until a document
  response shows the other `X-Ft-Instance` value.
- Assert the entered PROs/results still render, proving state came from Redis
  instead of instance-local heap.

Fallback curl sketch:

```bash
CJ=/tmp/ft.cookies

# Request 1 — likely app-1: establishes FT_SESSION, GET search page
curl -s -c $CJ -b $CJ -D - http://localhost:8080/tracker.xhtml | grep -i x-ft-instance

# ... perform a search postback (Playwright is easiest) entering 4821763 & 3390045 ...

# Subsequent GET — likely app-2 — must still show the 2 results
curl -s -c $CJ -b $CJ -D - http://localhost:8080/tracker.xhtml | grep -i x-ft-instance
```

**Expect:**
- `X-Ft-Instance` differs between the two calls (proving different nodes).
- The second response still reflects the session's entered PROs / results
  (proving state came from Redis, not local heap).

**Redis inspection:**
```bash
docker exec ft-redis redis-cli KEYS 'ft:session:*'
docker exec ft-redis redis-cli GET ft:session:<uuid>   # JSON TrackingSearch
docker exec ft-redis redis-cli TTL ft:session:<uuid>    # ~1800, sliding
```

## 5b. Postgres data-source verification

Confirm the DB was seeded by the SQL init scripts and both apps read it:
```bash
# Schema + seed present
docker exec ft-postgres psql -U freighttrack -d freighttrack \
  -c "SELECT pro_number, status FROM shipment ORDER BY pro_number;"
# expect: 00003390045|DELIVERED  and  00004821763|IN_TRANSIT

docker exec ft-postgres psql -U freighttrack -d freighttrack \
  -c "SELECT pro_number, seq, title, state FROM tracking_event ORDER BY pro_number, seq;"
# expect: 3 events for 4821763 (last=CURRENT), 4 events for 3390045 (all COMPLETED)
```
- Both `app1` and `app2` logs show a successful datasource connection using the
  env-supplied `FT_DB_URL`/`FT_DB_USER`/`FT_DB_PASSWORD` (no hard-coded creds).
- Search `4821763` through HAProxy → detail matches the seeded row regardless of
  which instance serves it (data is shared via Postgres, not per-instance).

## 6. Playwright browser E2E

Run against a started app, preferably the full compose topology through HAProxy:
```bash
E2E_BASE_URL=http://localhost:8080 npm run e2e
```

Specs live under `tests/e2e/` and use stable `data-testid` attributes from plan
05. Cover both configured projects: `chromium-mobile` at 375px and
`chromium-desktop` at ≥1280px.

Required specs:

| Spec | Asserts |
|------|---------|
| `tracker-ui.spec.ts` | Empty state renders; add/remove row changes `n/10`; find button label/plumbing changes for 1 vs 2 inputs; no horizontal scroll at 375px |
| `shipment-search.spec.ts` | Searching `4821763`,`3390045` renders 2 cards, correct status badges/routes, expands `4821763`, and displays details/timeline |
| `session-continuity.spec.ts` | Same browser context keeps `FT_SESSION`, observes both `X-Ft-Instance` values through HAProxy, and retains results across instance changes |
| `not-found.spec.ts` | Unknown numeric PRO renders a graceful "No shipment found" card without dropping valid results |

Keep assertions user-visible: roles, labels, visible text, and `data-testid` for
structural controls. Avoid JSF-generated ids and CSS class names except where
the class is itself the business contract (for example status color token).

## 7. Browser-based UI parity checklist (manual backup, both viewports)

Test at **375px** (mobile) and **≥1280px** (desktop):

| # | Check | Wireframe |
|---|-------|-----------|
| 1 | Empty state: 1 row, muted "Find My Shipment", bullseye + helper + demo hint | design1-A / design3-A |
| 2 | `+ Add Number` adds a row; counter `1/10`→`2/10`; button becomes active dark green + "Find My Shipments (2)" | design1-B / design3-B |
| 3 | Per-row `x` removes a row; hidden when only 1 row remains | design1/3 |
| 4 | Enter `4821763`,`3390045` → Find → 2 result cards, `RESULTS 2`, correct badges (In Transit green / Delivered gold) + route text | design2-C / design4-C |
| 5 | Expand `4821763` → full detail: Shipment Details, Location & Delivery, timeline (Picked up / Departed terminal = completed rings, En route = filled current) | design2-D / design4-D |
| 6 | Desktop detail renders 2-column (details left, location+timeline right); pickup & driver fields shown | design4-D |
| 7 | Unknown PRO → graceful "No shipment found" card | (extension) |
| 8 | Layout has no horizontal scroll at 375px; header/footer chrome present | all |

## 8. Failure-path checks
- Stop Redis (`docker compose stop redis`) → app still serves search page (empty
  working set), logs warning; no 500 (plan 04 §8).
- Restart Redis → sessions resume for new keys.
- Kill `app1` (`docker compose stop app1`) → HAProxy health check drops it;
  all traffic goes to `app2`; app still works (Redis-shared state + shared Postgres).
- Stop Postgres (`docker compose stop postgres`) → searches fail gracefully
  (error/empty results surfaced, no crash loop); recovers when Postgres returns.

## 9. Definition of done (rollup, ties to plan 00 §8)
- [ ] All unit/bean/store tests green (`./gradlew test`).
- [ ] Playwright browser E2E green (`E2E_BASE_URL=http://localhost:8080 npm run e2e`).
- [ ] `docker compose up` → 5 healthy containers (app1, app2, redis, postgres, haproxy).
- [ ] Postgres seeded from SQL init scripts (§5b) — 2 shipments + timeline events.
- [ ] Apps connect to Postgres via env-supplied URL/user/password only.
- [ ] Round-robin proven (§4 alternates instances).
- [ ] Session continuity proven across instances (§5/§6) + Redis keys present.
- [ ] UI parity checklist (§6/§7) passes on mobile and desktop.
- [ ] Failure paths (§8) behave gracefully.
