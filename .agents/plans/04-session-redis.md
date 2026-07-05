# 04 — Session Affinity via Redis + JSF Client State

This is the crux of the POC. HAProxy uses **round-robin with no stickiness**, so
every request may hit a different Quarkus instance. Two kinds of state must
survive that:

1. **JSF view state** (per-view UI tree) → solved by **client-side state saving**
   (plan 03, ADR-3). Nothing to store server-side; state rides in the page.
2. **Application session state** (the user's `TrackingSearch`: entered PROs +
   last results + expanded flags) → solved by a **Redis-backed shared session**
   (this document, ADR-4).

Together these give "session affinity" semantics: the user's experience is
continuous no matter which node answers.

## 1. Why not standard `@SessionScoped` beans?

A CDI `@SessionScoped` bean lives in the in-heap HTTP session of a single JVM.
Under round-robin, app2 has no knowledge of a session created on app1, so state
would appear to reset every other request. Options considered:

| Option | Verdict |
|--------|---------|
| HAProxy sticky cookie | Rejected — requirement mandates round-robin |
| Undertow distributable session replicated to Redis | Heavier; no first-class Quarkus support (ADR-2 alt) |
| **External Redis session store keyed by our own cookie** | **Chosen** — simple, explicit, framework-agnostic |

## 2. Design: `RedisSessionStore`

A Kotlin `@ApplicationScoped` component that reads/writes the `TrackingSearch`
working set to Redis, keyed by a session id carried in a cookie.

### Cookie
- Name: `FT_SESSION`
- Value: opaque UUID (v4), `HttpOnly`, `SameSite=Lax`, `Path=/`.
- Issued on first request if absent (via a JSF `PhaseListener` or servlet
  `Filter`, see §4).
- **Not** used by HAProxy for routing (no `cookie` directive in haproxy.cfg).

### Redis key & value
- Key: `ft:session:{uuid}`
- Value: serialized `TrackingSearch`.
- Serialization: **JSON** (preferred for debuggability) via kotlinx/Jackson,
  or Java serialization. **Chosen: JSON** — store types are simple data classes.
- TTL: `1800s` (30 min), refreshed on each write (sliding expiration).

### Client (Quarkus Redis)
Use the Quarkus Redis Data API (`io.quarkus.redis.datasource.RedisDataSource` /
reactive variant). A typed value command:
```
ValueCommands<String, TrackingSearch> value =
    redisDataSource.value(String::class.java, TrackingSearch::class.java)
```

### Interface sketch (Kotlin)

> **Field injection required:** `RedisSessionStore` MUST use field injection
> (`@Inject lateinit var redis: RedisDataSource`) because Kotlin's `noArg`
> plugin leaves constructor-injected fields null. The `ValueCommands` field
> must use `by lazy` to defer initialization until after `redis` is injected.

```kotlin
@ApplicationScoped
open class RedisSessionStore {
    @Inject
    lateinit var redis: RedisDataSource

    private val cmd: ValueCommands<String, TrackingSearch> by lazy {
        redis.value(String::class.java, TrackingSearch::class.java)
    }

    private fun key(id: String) = "ft:session:$id"
    private val ttl = Duration.ofMinutes(30)

    fun load(id: String): TrackingSearch =
        cmd.get(key(id)) ?: TrackingSearch.fresh()       // fresh working set

    fun save(id: String, search: TrackingSearch) {
        cmd.setex(key(id), ttl.seconds, search)           // sliding TTL
    }

    fun clear(id: String) = redis.key().del(key(id))
}
```

## 3. How beans use it (the flow)

The JSF managed bean (`TrackingBean`, plan 05) is **`@RequestScoped`** — NOT
session scoped — so it holds no cross-request state in the JVM. On each request:

```
1. Resolve FT_SESSION id from cookie (SessionContext).
2. search = redisSessionStore.load(id)     // hydrate from Redis
3. Bind `search` to the view; handle the action (add row / search / expand).
4. redisSessionStore.save(id, search)      // persist back to Redis
```

Because hydrate→act→persist happens fully within one request against Redis, the
next request can land on the other instance and see identical state.

### `SessionContext` helper (Kotlin, `@RequestScoped`)
Wraps cookie extraction/creation so beans just call `sessionContext.id`.

> **Default `id` value required:** Use `var id: String = ""` instead of
> `lateinit var id`. The HTTP filter sets `id` before any request processing in
> production, but in `@QuarkusTest` unit tests the filter does not run.
> `lateinit` causes `UninitializedPropertyAccessException` when CDI-injected
> beans access `id` in a test context.

```kotlin
@RequestScoped
open class SessionContext {
    var id: String = ""
    var isNew: Boolean = false
}
```

## 4. Issuing the cookie — Servlet `Filter`

A `@WebFilter("/*")` (or Quarkus `@ApplicationScoped` filter) runs before the
Faces servlet:
```
- read FT_SESSION cookie
- if missing: id = UUID, add Set-Cookie (HttpOnly, Lax), sessionContext.isNew = true
- else: sessionContext.id = cookie value
- also add response header `X-Ft-Instance: {INSTANCE_ID}` (for round-robin proof)
```
`INSTANCE_ID` comes from env var (`FT_INSTANCE_ID`, e.g. `app-1`/`app-2`), set in
compose (plan 07). This header is what plan 08 uses to prove round-robin.

## 5. Persist-back timing

Two acceptable approaches — pick **A** for simplicity:

- **A (explicit):** each `TrackingBean` action method ends with
  `store.save(id, search)`. Clear, easy to reason about. Every action method
  (`addNumber`, `removeNumber`, `findShipments`, `validateEntry`, `lookupShipment`,
  `toggleExpand`, `expandFirst`, `reset`) persists the working set back to
  Redis. This includes `validateEntry()` called on input blur via AJAX — the
  checkbox state is written to Redis so it survives any round-robin instance
  switch. `TrackingBean.init()` reloads from Redis on each request, so the
  working set is always current.
- **B (phase listener):** a JSF `PhaseListener` on `RENDER_RESPONSE`/after
  invoke-application persists automatically. Less boilerplate, more magic.

## 6. JSF client-state saving requirements recap

- `STATE_SAVING_METHOD=client` on both instances (plan 03).
- **Identical** `org.apache.myfaces.SECRET` and `MAC_SECRET` on both instances
  (env-injected, plan 07) so signed/encrypted view state is portable.
- Keep the serialized view small: the heavy data (`Shipment` list) lives in
  Redis, not the view tree. Bind view to lightweight references
  (PRO strings, booleans) where possible to limit client-state size.

## 7. Redis connection config

`application.properties`:
```properties
quarkus.redis.hosts=redis://${FT_REDIS_HOST:localhost}:6379
quarkus.redis.timeout=3s
# optional: quarkus.redis.password=...
```
Both app instances point at the same `redis` compose service.

## 8. Failure / edge handling (POC-level)

- Redis unreachable at load → return empty `TrackingSearch` and log a warning
  (app still renders search screen).
- Unknown/expired session id → treated as fresh (empty working set), TTL renews
  on next save.
- Cookie disabled in browser → each request is a fresh session (acceptable for
  POC; document as a known limitation).

## 9. Exit criteria

- With 2 instances behind round-robin, entering PROs on request 1 (app-1) and
  submitting on request 2 (app-2) yields correct results — state came from Redis.
- `redis-cli KEYS 'ft:session:*'` shows the session key; `GET` returns JSON.
- `X-Ft-Instance` header alternates across requests, proving no stickiness.
