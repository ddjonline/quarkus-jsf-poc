# 06 — Services & Data (PostgreSQL-backed)

Business logic + a **PostgreSQL** data source that supplies the two demo
shipments from the wireframes. The schema and seed rows are created by **SQL
init scripts** run by the Postgres container on first start (plan 07); the app
reads the data through Hibernate ORM / Panache. All app code is Kotlin,
`@ApplicationScoped`.

## 1. Data flow overview

```
Postgres (seeded by SQL init scripts)
   │  JDBC (url/user/password from ENV)
   ▼
ShipmentEntity / TrackingEventEntity  (JPA / Panache)
   │  mapped -> domain
   ▼
ShipmentRepository (Panache)  ──►  ShipmentService  ──►  TrackingBean (web)
```

Both app instances point at the **same** Postgres service, so lookups are
identical regardless of which node (app-1/app-2) serves the request.

## 2. Datasource configuration — ENV vars only

`application.properties` references environment variables; **no credentials are
hard-coded**:

```properties
# --- PostgreSQL datasource (values supplied via environment) ---
quarkus.datasource.db-kind=postgresql
quarkus.datasource.username=${FT_DB_USER}
quarkus.datasource.password=${FT_DB_PASSWORD}
quarkus.datasource.jdbc.url=${FT_DB_URL}

# Hibernate: schema is owned by SQL init scripts, so DO NOT auto-generate.
quarkus.hibernate-orm.schema-management.strategy=none
quarkus.hibernate-orm.log.sql=false
```

| Env var | Example | Meaning |
|---------|---------|---------|
| `FT_DB_URL` | `jdbc:postgresql://postgres:5432/freighttrack` | JDBC URL (host = compose service name) |
| `FT_DB_USER` | `freighttrack` | DB user |
| `FT_DB_PASSWORD` | `freighttrack` | DB password |

> `schema-management.strategy=none` — the database is authoritative; Hibernate
> must not create/drop/validate-away the SQL-seeded schema. (In dev/test with
> Dev Services, switch to `drop-and-create` + an import script; see plan 08.)

## 3. Schema (created by SQL init scripts — see plan 07)

Two tables reproduce the domain model from plan 01.

### `shipment`
| Column | Type | Notes |
|--------|------|-------|
| `pro_number` | `varchar(32)` PK | canonical zero-padded 11 digits, e.g. `00004821763` |
| `display_pro` | `varchar(40)` | `PRO-4821763` |
| `status` | `varchar(24)` | maps to `ShipmentStatus` name |
| `origin` | `varchar(80)` | `Memphis, TN` |
| `destination` | `varchar(80)` | `Nashville, TN` |
| `shipper` | `varchar(120)` | |
| `consignee` | `varchar(120)` | |
| `commodity` | `varchar(120)` | |
| `weight_lbs` | `integer` | |
| `pieces` | `integer` | |
| `pickup_time` | `varchar(48)` | preformatted display string (POC) |
| `driver_name` | `varchar(80)` | |
| `driver_phone` | `varchar(32)` | |
| `current_location` | `varchar(120)` | |
| `last_update` | `varchar(48)` | |
| `estimated_delivery` | `varchar(48)` | |

### `tracking_event`
| Column | Type | Notes |
|--------|------|-------|
| `id` | `bigserial` PK | |
| `pro_number` | `varchar(32)` FK → `shipment.pro_number` | |
| `seq` | `integer` | ordering (0-based, oldest→newest) |
| `time_label` | `varchar(16)` | `07:42 AM` |
| `title` | `varchar(80)` | `Picked up` |
| `location` | `varchar(120)` | `Memphis, TN - Hartfield Industrial` |
| `state` | `varchar(16)` | maps to `TrackingEventState` name |

Index: `tracking_event(pro_number, seq)`.

> Date/time values remain preformatted display strings to stay faithful to the
> wireframes (plan 01 §3 note). A follow-up can migrate to `timestamptz`.

## 4. JPA entities (Kotlin + Panache)

Entities live in `app.freighttrack.data`. `open`/no-arg handled by the Kotlin
plugins (plan 02 §6). Companion-object Panache repositories or
`PanacheRepository` — **chosen: `PanacheRepositoryBase`** to keep entities plain.

```kotlin
@Entity @Table(name = "shipment")
class ShipmentEntity {
    @Id @Column(name = "pro_number") lateinit var proNumber: String
    @Column(name = "display_pro")    lateinit var displayPro: String
    @Column(name = "status")         lateinit var status: String
    lateinit var origin: String
    lateinit var destination: String
    lateinit var shipper: String
    lateinit var consignee: String
    lateinit var commodity: String
    @Column(name = "weight_lbs") var weightLbs: Int = 0
    var pieces: Int = 0
    @Column(name = "pickup_time")        lateinit var pickupTime: String
    @Column(name = "driver_name")        lateinit var driverName: String
    @Column(name = "driver_phone")       lateinit var driverPhone: String
    @Column(name = "current_location")   lateinit var currentLocation: String
    @Column(name = "last_update")        lateinit var lastUpdate: String
    @Column(name = "estimated_delivery") lateinit var estimatedDelivery: String

    @OneToMany(fetch = FetchType.EAGER)
    @JoinColumn(name = "pro_number")
    @OrderBy("seq ASC")
    var events: MutableList<TrackingEventEntity> = mutableListOf()
}

@Entity @Table(name = "tracking_event")
class TrackingEventEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null
    @Column(name = "pro_number") lateinit var proNumber: String
    var seq: Int = 0
    @Column(name = "time_label") lateinit var timeLabel: String
    lateinit var title: String
    lateinit var location: String
    lateinit var state: String
}
```

## 5. `ShipmentRepository` (Kotlin, Panache, `@ApplicationScoped`)

Queries Postgres and maps entities → immutable domain types (plan 01). PRO
normalization is applied before lookup.

```kotlin
@ApplicationScoped
class ShipmentRepository : PanacheRepositoryBase<ShipmentEntity, String> {

    fun findByPro(pro: String): Shipment? =
        findById(pro.normalizePro())?.toDomain()

    fun all(): List<Shipment> = listAll().map { it.toDomain() }
}

private fun String.normalizePro(): String {
    val digits = trim()
        .removePrefix("PRO-")
        .removePrefix("PRO ")
        .filter { it.isDigit() }
    return if (digits.isBlank()) "" else digits.padStart(TrackingConstants.PRO_MAX_DIGITS, '0')
}

private fun ShipmentEntity.toDomain() = Shipment(
    proNumber = proNumber,
    displayPro = displayPro,
    status = ShipmentStatus.valueOf(status),
    origin = origin, destination = destination,
    shipper = shipper, consignee = consignee, commodity = commodity,
    weightLbs = weightLbs, pieces = pieces,
    pickupTime = pickupTime, driverName = driverName, driverPhone = driverPhone,
    currentLocation = currentLocation, lastUpdate = lastUpdate,
    estimatedDelivery = estimatedDelivery,
    timeline = events.map { it.toDomain() }
)

private fun TrackingEventEntity.toDomain() = TrackingEvent(
    timeLabel = timeLabel, title = title, location = location,
    state = TrackingEventState.valueOf(state)
)
```

> Read methods that touch the DB run within a transaction/`@Transactional`
> context (or a read-only session); `ShipmentService.lookup` is annotated
> `@Transactional` for the POC.

## 6. `ShipmentService` (Kotlin, `@ApplicationScoped`)

Unchanged contract from before — now delegates to the Panache repository. This
is the stable seam the web layer depends on.

```kotlin
@ApplicationScoped
class ShipmentService(private val repo: ShipmentRepository) {

    @Transactional
    fun find(pro: String): Shipment? = repo.findByPro(pro)

    /** Batch lookup preserving input order, dedup, wrapping results. */
    @Transactional
    fun lookup(pros: List<String>): List<ShipmentLookupResult> =
        pros.map { it.normalizePro() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(TrackingConstants.MAX_PRO_NUMBERS)
            .map { pro ->
                val shipment = repo.findByPro(pro)
                ShipmentLookupResult(
                    queriedPro = pro,
                    displayPro = pro.displayPro(),
                    state = if (shipment == null) LookupState.NOT_FOUND else LookupState.LOADED,
                    shipment = shipment
                )
            }
}

private fun String.displayPro() = "PRO-${trimStart('0').ifEmpty { "0" }}"
```

## 7. Seed data (authoritative version now lives in SQL)

The two demo shipments are **defined in the SQL init scripts** (plan 07 §4), not
in Kotlin. Content mirrors the wireframes exactly:

**`4821763` — IN_TRANSIT** (Memphis, TN → Nashville, TN)
- Shipper Hartfield Industrial Supply · Consignee Valley Manufacturing Co.
- Commodity Hydraulic Equipment · Weight 1240 · Pieces 6
- Pickup `Jun 26, 2026 - 07:42 AM` · Driver Marcus D. Reyes · `(901) 555-0182`
- Current `Jackson, TN - I-40 East MM 83` · Last update `Jun 26, 2026 - 10:15 AM`
- ETA `Jun 26, 2026 - 2:00-4:00 PM`
- Timeline: Picked up (COMPLETED) → Departed terminal (COMPLETED) → En route (CURRENT)

**`3390045` — DELIVERED** (Birmingham, AL → Atlanta, GA)
- Fabricated-but-consistent detail fields; 4-step timeline all COMPLETED ending
  in Delivered.

(Exact column values enumerated in plan 07 §4 as `INSERT` statements.)

## 8. Interaction with the web layer

```
TrackingBean.findShipments()
   -> collect entries -> List<String> pros
   -> shipmentService.lookup(pros) -> List<ShipmentLookupResult>   // hits Postgres
   -> search.results = results
   -> store.save(sessionId, search)      // Redis (plan 04)
```

Note: the domain `Shipment` objects returned from Postgres are what get stored
into the Redis session (`TrackingSearch.results`). They remain `Serializable`
POJOs — no JPA proxies leak into the session (mapping to domain in the repo
detaches them).

## 9. Validation rules
- Blank rows ignored; duplicate PROs collapsed; cap 10 (UI-enforced, service
  defensively truncates); unknown PRO → `ShipmentLookupResult(found=false)`.

## 10. Tests (see plan 08 for full matrix)
- `ShipmentRepositoryTest` (`@QuarkusTest`, Dev Services Postgres): seeded DB
  returns `4821763` / `00004821763` IN_TRANSIT with 3 ordered events;
  `PRO-3390045` resolves to `00003390045`; unknown → null; `normalizePro`
  handles prefix/spacing/digits-only/zero-padding.
- `ShipmentServiceTest`: dedupe + blank filtering + order preserved; not-found
  wrapping.

## 11. Exit criteria
- `shipmentService.find("4821763")` returns canonical `00004821763` IN_TRANSIT shipment with 3 timeline
  events (last = CURRENT), sourced from Postgres.
- `shipmentService.find("PRO-3390045")` resolves (prefix stripped + padded) to DELIVERED.
- `shipmentService.find("0000000")` returns `null`.
- App boots with datasource fully configured from `FT_DB_URL` / `FT_DB_USER` /
  `FT_DB_PASSWORD` env vars (no hard-coded credentials).
