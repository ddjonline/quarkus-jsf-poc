# 01 — Domain / Object Model

Derived directly from the four wireframes. All domain types are **Kotlin**,
immutable where practical, and **`java.io.Serializable`** (they are stored in
the Redis session as part of the user's working set).

> Package root: `app.freighttrack`
> Domain package: `app.freighttrack.domain`

## 1. Field inventory harvested from the designs

| Wireframe element | Source screen | Maps to |
|-------------------|---------------|---------|
| `PRO-4821763` / `4821763` input | design1/3 A,B | `ProNumberEntry.value` |
| `1/10`, `2/10` counter | design1/3 | derived: `entries.size` + `MAX_PRO_NUMBERS` |
| Status badge `In Transit` / `Delivered` | design2/4 C,D | `ShipmentStatus` enum |
| `Memphis, TN -> Nashville, TN` | result card | `Shipment.origin` / `destination` |
| SHIPPER `Hartfield Industrial Supply` | detail | `Shipment.shipper` |
| CONSIGNEE `Valley Manufacturing Co.` | detail | `Shipment.consignee` |
| COMMODITY `Hydraulic Equipment` | detail | `Shipment.commodity` |
| WEIGHT `1,240 lbs` | detail | `Shipment.weightLbs` (Int) |
| PIECES `6 pcs` | detail | `Shipment.pieces` (Int) |
| PICKUP TIME `Jun 26, 2026 - 07:42 AM` | detail (browser) | `Shipment.pickupTime` |
| DRIVER `Marcus D. Reyes` | detail (browser) | `Shipment.driverName` |
| DRIVER PHONE `(901) 555-0182` | detail (browser) | `Shipment.driverPhone` |
| CURRENT LOCATION `Jackson, TN - I-40 East MM 83` | detail | `Shipment.currentLocation` |
| LAST UPDATE `Jun 26, 2026 - 10:15 AM` | detail | `Shipment.lastUpdate` |
| EST. DELIVERY `Jun 26, 2026 - 2:00-4:00 PM` | detail | `Shipment.estimatedDelivery` |
| Timeline rows (time / title / location, open vs filled marker) | detail | `List<TrackingEvent>` |

## 2. Enums

### `ShipmentStatus`
Represents the badge shown on cards/detail. Carries a display label and a CSS
class token for the badge color.

```
enum class ShipmentStatus(val label: String, val cssClass: String) {
    PICKED_UP     ("Picked Up",   "badge-neutral"),
    IN_TRANSIT    ("In Transit",  "badge-transit"),   // pale green pill
    OUT_FOR_DELIVERY("Out for Delivery", "badge-transit"),
    DELIVERED     ("Delivered",   "badge-delivered"), // pale gold/tan pill
    EXCEPTION     ("Exception",   "badge-exception")
}
```

### `TrackingEventState`
Controls the timeline marker rendering (design2/4: open circle = completed,
filled dot = current).

```
enum class TrackingEventState { COMPLETED, CURRENT, PENDING }
```

## 3. Core domain types

### `ProNumberEntry`
One editable input row on the search screen (design1/3). Mutable `value`
because it is bound to a JSF input; `id` gives JSF a stable row key for
add/remove.

**Input rules (new requirement):**
- **Numeric only** — non-digit characters are rejected/stripped.
- **1 to 11 digits** — max length 11.
- **Zero left-padded to 11 digits** to form the canonical PRO used for lookup
  (e.g. `4821763` → `00004821763`).

```
data class ProNumberEntry(
    val id: String = UUID.randomUUID().toString(),
    var value: String = ""
) : Serializable {
    fun isBlank(): Boolean = value.trim().isEmpty()

    /** digits only, "PRO-" prefix and any non-digits stripped */
    fun digits(): String =
        value.trim().removePrefix("PRO-").removePrefix("PRO ").filter { it.isDigit() }

    /** valid = 1..11 digits after stripping */
    fun isValid(): Boolean =
        digits().let { it.isNotEmpty() && it.length <= TrackingConstants.PRO_MAX_DIGITS }

    /** canonical lookup key: zero left-padded to 11 digits */
    fun normalized(): String = digits().padStart(TrackingConstants.PRO_MAX_DIGITS, '0')
}
```

> The same `digits() -> padStart(11,'0')` normalization is applied server-side
> (repository) so the canonical form always matches the stored `pro_number`
> (plan 06/07 seed rows are stored zero-padded to 11 digits).

### `TrackingEvent`
A single timeline row.

```
data class TrackingEvent(
    val timeLabel: String,        // "07:42 AM"
    val title: String,            // "Picked up" / "Departed terminal" / "En route"
    val location: String,         // "Memphis, TN - Hartfield Industrial"
    val state: TrackingEventState // COMPLETED / CURRENT
) : Serializable
```

### `Shipment`
The aggregate for one PRO number (design2/4 detail + card summary fields).

```
data class Shipment(
    val proNumber: String,            // canonical, zero-padded 11 digits e.g. "00004821763"
    val displayPro: String,           // "PRO-4821763"
    val status: ShipmentStatus,

    // Route summary (card)
    val origin: String,               // "Memphis, TN"
    val destination: String,          // "Nashville, TN"

    // Shipment Details
    val shipper: String,
    val consignee: String,
    val commodity: String,
    val weightLbs: Int,
    val pieces: Int,

    // Pickup & Driver
    val pickupTime: String,           // preformatted display string for POC
    val driverName: String,
    val driverPhone: String,

    // Location & Delivery
    val currentLocation: String,
    val lastUpdate: String,
    val estimatedDelivery: String,

    // Timeline (ordered oldest -> newest)
    val timeline: List<TrackingEvent> = emptyList()
) : Serializable {
    val routeSummary: String get() = "$origin -> $destination"
}
```

> **POC date formatting note:** date/time values are stored as preformatted
> display strings to keep the mock data faithful to the wireframes without a
> formatting layer. A follow-up refactor can switch to `java.time` +
> `DateTimeFormatter` if needed.

### `ShipmentLookupResult`
Wraps a single lookup outcome. Because each PRO is now fetched by an
**independent asynchronous request** and rendered into its own accordion panel
(see plan 05), the result carries a **per-item `state`** so each panel can show
loading → loaded/not-found/error independently.

```
data class ShipmentLookupResult(
    val queriedPro: String,            // canonical padded PRO (accordion key)
    val displayPro: String,            // "PRO-4821763" for the panel header
    var state: LookupState = LookupState.PENDING,
    var shipment: Shipment? = null,    // populated when state == LOADED
    var expanded: Boolean = false,     // accordion expand/collapse (design2/4 C vs D)
    var errorMessage: String? = null   // populated when state == ERROR
) : Serializable {
    val found: Boolean get() = state == LookupState.LOADED && shipment != null
}
```

### `LookupState` (new)
Drives per-accordion-item rendering as async results arrive independently.

```
enum class LookupState {
    PENDING,    // queued, request not yet started
    LOADING,    // async request in flight (spinner in the panel header)
    LOADED,     // shipment data available
    NOT_FOUND,  // valid PRO, no matching shipment
    ERROR       // lookup failed (timeout/backend) — panel shows retry
}
```

### `TrackingSearch`
The user's working set for one browser session — this is the object persisted
to Redis (see plan 04). Holds both the editable input rows and the last results.

```
data class TrackingSearch(
    val entries: MutableList<ProNumberEntry> =
        mutableListOf(ProNumberEntry()),          // starts with one empty row
    var results: MutableList<ShipmentLookupResult> = mutableListOf()
) : Serializable {
    val count: Int get() = entries.size
    val hasResults: Boolean get() = results.isNotEmpty()
    val foundCount: Int get() = results.count { it.found }
}
```

## 4. Constants

```
object TrackingConstants {
    const val MAX_PRO_NUMBERS = 10     // "n/10" cap; also max parallel requests
    const val MIN_PRO_NUMBERS = 1
    const val PRO_MAX_DIGITS  = 11     // numeric PRO length; zero left-padded
}
```

## 5. Derived / view-helper values (computed in beans, not stored)

| Value | Rule | Source |
|-------|------|--------|
| Counter text `n/10` | `entries.size + "/" + MAX_PRO_NUMBERS` | design1/3 header |
| Button label | `foundInputs <= 1 ? "Find My Shipment" : "Find My Shipments (" + n + ")"` | design1/3 button |
| Button active/muted | active when ≥1 non-blank entry | design1 A (muted) vs B (dark) |
| Add disabled | when `entries.size >= MAX_PRO_NUMBERS` | `+ Add Number` |
| Remove visible | when `entries.size > MIN_PRO_NUMBERS` | per-row `x` |
| Results count `RESULTS n` | `results.size` | design2/4 |

## 6. Class relationship diagram

```
TrackingSearch (session-scoped, in Redis)
 ├── entries : List<ProNumberEntry>          (search inputs)
 └── results : List<ShipmentLookupResult>
                    └── shipment : Shipment?  (aggregate)
                                     └── timeline : List<TrackingEvent>
                                     └── status  : ShipmentStatus
                                                     (badge style)
```

## 7. Seed data (mirrors the wireframes exactly)

Two shipments, defined in the mock repository (plan 06):

**`4821763` — IN_TRANSIT**
- Route: Memphis, TN → Nashville, TN
- Shipper: Hartfield Industrial Supply · Consignee: Valley Manufacturing Co.
- Commodity: Hydraulic Equipment · Weight: 1240 · Pieces: 6
- Pickup: `Jun 26, 2026 - 07:42 AM` · Driver: Marcus D. Reyes · `(901) 555-0182`
- Current: `Jackson, TN - I-40 East MM 83`
- Last update: `Jun 26, 2026 - 10:15 AM` · ETA: `Jun 26, 2026 - 2:00-4:00 PM`
- Timeline:
  1. `07:42 AM` **Picked up** — Memphis, TN - Hartfield Industrial — COMPLETED
  2. `08:55 AM` **Departed terminal** — Memphis, TN - Terminal 7 — COMPLETED
  3. `10:15 AM` **En route** — Jackson, TN - I-40 East MM 83 — CURRENT

**`3390045` — DELIVERED**
- Route: Birmingham, AL → Atlanta, GA
- (Remaining detail fields fabricated consistently for the POC; timeline ends
  with a DELIVERED/COMPLETED final event.)

## 8. Package layout (target)

```
app.freighttrack
 ├── domain/         Shipment, TrackingEvent, ProNumberEntry, ShipmentLookupResult,
 │                   TrackingSearch, ShipmentStatus, TrackingEventState, TrackingConstants   (Kotlin)
 ├── data/           ShipmentEntity, TrackingEventEntity (JPA), ShipmentRepository (Panache) (Kotlin)
 ├── service/        ShipmentService                                                          (Kotlin)
 ├── session/        RedisSessionStore, SessionId cookie handling                             (Kotlin)
 └── web/            JSF managed beans: TrackingBean, ...                                      (Java)
```
