# 05 — UI Implementation (Views & Managed Beans)

Maps every wireframe to concrete Facelets fragments and the Java JSF managed
bean that backs them. One responsive view (`tracker.xhtml`) covers all four
wireframes via conditional rendering + CSS breakpoints.

## 1. Managed bean: `TrackingBean` (Java, `@RequestScoped`, `@Named`)

Named `tracking`. Request-scoped (state lives in Redis, plan 04). Injects the
session store, session context, and shipment service.

### Responsibilities & members
```
@Named("tracking") @RequestScoped
public class TrackingBean {
    @Inject SessionContext session;
    @Inject RedisSessionStore store;
    @Inject ShipmentService shipmentService;

    private TrackingSearch search;   // hydrated in @PostConstruct

    @PostConstruct init():   search = store.load(session.getId());

    // ----- getters for the view -----
    List<ProNumberEntry> getEntries()
    List<ShipmentLookupResult> getResults()
    int  getCount()                 // entries.size
    int  getMax()                   // TrackingConstants.MAX_PRO_NUMBERS (10)
    String getCounterText()         // "n/10"
    boolean isAddDisabled()         // count >= max
    boolean isRemoveVisible()       // count > 1
    boolean isSearchActive()        // any non-blank entry
    String getFindButtonLabel()     // "Find My Shipment" | "Find My Shipments (n)"
    boolean isHasResults()
    int getResultsCount()

    // ----- actions (each ends with store.save) -----
    void addNumber()                // append ProNumberEntry(); guard max
    void removeNumber(String rowId) // remove by id; guard min 1
    String findShipments()          // initialize per-PRO result panels
    void lookupShipment(String pro) // one async Ajax lookup; updates one panel
    void toggleExpand(String pro)   // flip ShipmentLookupResult.expanded
    void expandFirst()              // "Expand first" link
    String reset()                  // clear entries+results
}
```

### `findShipments()` / async lookup logic
```
1. collect non-blank entries -> normalized PRO strings (dedupe)
2. results = pros.map { pro -> ShipmentLookupResult(pro, displayPro(pro), PENDING) }
3. store.save(session.id, search)
4. return null (stay on same view; pending result panels render below the form)
5. each result panel immediately fires its own `<f:ajax>` request to
   `lookupShipment(r.queriedPro)`, so PROs are looked up independently and panels
   can transition LOADING -> LOADED/NOT_FOUND/ERROR without waiting for siblings
6. if exactly one result is LOADED -> mark it expanded (design flow convenience)
```

## 2. `tracker.xhtml` structure (single responsive view)

Uses `layout.xhtml` (plan 03). Content region composed of fragments:

```
<ui:composition template="/WEB-INF/templates/layout.xhtml">
  <ui:define name="content">
    <h:form id="trackForm">
      #{include: /WEB-INF/fragments/pro-input.xhtml}     <!-- search & input -->
      #{include: /WEB-INF/fragments/results.xhtml}       <!-- rendered if hasResults -->
      #{include: /WEB-INF/fragments/empty-state.xhtml}   <!-- rendered if !hasResults -->
    </h:form>
  </ui:define>
</ui:composition>
```

Use `<f:ajax>` on add/remove/expand for smooth partial updates; full submit is
fine for `findShipments` in the POC. Each pending result row also has a stable
per-row Ajax trigger (for example a hidden command component clicked by a small
startup script) so lookups are separate browser requests and can run in parallel.

## 3. Fragment ↔ wireframe mapping

### `pro-input.xhtml` → design1 A/B, design3 A/B (Search & Input)
- **Toolbar:** `PRO NUMBERS <span>#{tracking.counterText}</span>` on the left;
  `+ Add Number` command link on the right (`disabled=#{tracking.addDisabled}`,
  action `#{tracking.addNumber}`, `<f:ajax render="trackForm">`).
- **Rows:** `<ui:repeat value="#{tracking.entries}" var="e">`
  - leading checkbox (visual, per design — non-functional select marker)
  - `<h:inputText value="#{e.value}" pt:placeholder="PRO-4821763 or 4821763">`
  - `x` remove commandLink rendered when `#{tracking.removeVisible}`,
    action `#{tracking.removeNumber(e.id)}`.
- **Find button:** `<h:commandButton value="#{tracking.findButtonLabel}"
  action="#{tracking.findShipments}"
  styleClass="btn-find #{tracking.searchActive ? 'btn-find--active' : 'btn-find--muted'}">`
  - Prefix search icon (`Q`/magnifier) via CSS/inline SVG.
- Matches design1-A (1 empty row, muted button) and design1-B (2 rows, active
  dark button labelled `Find My Shipments (2)`).

### `empty-state.xhtml` → design1/3 lower area
- Rendered when `#{not tracking.hasResults}`.
- Bullseye SVG in pale-green circle; heading "Track your freight in real time";
  helper text "Enter up to 10 PRO numbers above and tap Find My Shipment to see
  pickup times, driver info, current location, and estimated delivery.";
  gold demo hint "Demo: try 4821763 or 3390045".

### `results.xhtml` → design2/4 C (Results list)
- Rendered when `#{tracking.hasResults}`.
- **Results toolbar:** `RESULTS <span>#{tracking.resultsCount}</span>` left;
  `Expand first` commandLink right (`#{tracking.expandFirst}`).
- **Cards:** `<ui:repeat value="#{tracking.results}" var="r">`
  - **Loading/Pending card:** when `#{r.state eq 'PENDING' or r.state eq 'LOADING'}`
    show `displayPro` + spinner and fire/render only that row's Ajax lookup.
  - `result-card` with:
    - color thumb (green tint if transit, gold tint if delivered) — bind class
      to `#{r.shipment.status.cssClass}`.
    - `displayPro` (mono), route `#{r.shipment.origin} -> #{r.shipment.destination}`.
    - status **badge**: `<span class="badge #{r.shipment.status.cssClass}">
      #{r.shipment.status.label}</span>`.
    - chevron toggle commandLink → `#{tracking.toggleExpand(r.queriedPro)}`.
  - **Not-found card:** when `#{r.state eq 'NOT_FOUND'}` show `displayPro` + muted
    "No shipment found" (graceful; not in wireframes but needed).
  - **Error card:** when `#{r.state eq 'ERROR'}` show `displayPro`, a short error,
    and a retry command for that row.
  - **Expanded panel:** when `#{r.expanded}` include `detail.xhtml` inline
    (design2-D / design4-D).

### `detail.xhtml` → design2/4 D (Expanded shipment detail)
Rendered inside an expanded card OR as the standalone detail region. Contains:
- **Summary strip:** boxed `displayPro`, route, right-aligned status badge.
- **SHIPMENT DETAILS** section (label/value rows): Shipper, Consignee,
  Commodity, Weight (`#{r.shipment.weightLbs} lbs`), Pieces (`… pcs`).
- **PICKUP & DRIVER** (visible in browser design4-D): Pickup Time, Driver,
  Driver Phone. On mobile these still render (stacked); design2-D omitted them
  for space but including them is acceptable and responsive.
- **LOCATION & DELIVERY:** Current Loc., Last Update, Est. Delivery.
- **SHIPMENT TIMELINE:** `<ui:repeat value="#{r.shipment.timeline}" var="t">`
  - marker span class `timeline-marker--#{t.state.name().toLowerCase()}`
  - time (mono), title (bold), location (muted).
- **Layout:** single column on mobile; CSS grid 2-column ≥1024px (plan 03 §4)
  — left = Details + Pickup/Driver, right = Location/Delivery + Timeline
  (matches design4-D).

## 4. Section label styling
`SHIPMENT DETAILS`, `LOCATION & DELIVERY`, `PICKUP & DRIVER`,
`SHIPMENT TIMELINE` render in gold uppercase (`.section-label`) per wireframes.

## 5. Accessibility / responsive notes
- All inputs get `<h:outputLabel>`/`aria-label` (labels are visually the
  placeholder in the design; keep accessible names).
- Touch targets ≥44px on mobile (button, remove `x`, chevron).
- `inputmode="numeric"` hint via passthrough attr on PRO inputs for mobile
  keyboards.
- Ensure focus order: rows → add → find → results.
- Add stable `data-testid` passthrough attributes for Playwright on key elements:
  `pro-input`, `add-pro`, `remove-pro`, `find-shipments`, `results-count`,
  `result-card`, `result-toggle`, `shipment-detail`, and `empty-state`. Do not
  bind E2E tests to generated JSF client ids or decorative CSS classes.

## 6. Navigation model
Single-page: search form and results coexist on `tracker.xhtml`. No page
navigation needed; `index.xhtml` simply forwards to `tracker.xhtml`. Expansion
is in-place (design C↔D are the same page collapsed/expanded).

## 7. Exit criteria (visual parity)
- design1-A/B reproduced (empty vs 2-row active states + dynamic button label).
- design2-C reproduced (2 cards, correct badges/colors, results toolbar).
- design2-D / design4-D reproduced (all detail sections + 3-step timeline with
  correct completed/current markers for `4821763`).
- design3/4 reproduced at desktop breakpoint (full-width header, 2-col detail).
- Playwright can locate and exercise all interactive controls via stable
  `data-testid` attributes at mobile and desktop viewports.
