# 03 — JSF Integration, Facelets & Responsive Shell

Configure Apache MyFaces on Quarkus, establish the Facelets template
hierarchy, and build the responsive CSS shell that reproduces the FreightTrack
theme for both mobile (design1/2) and desktop (design3/4).

## 1. MyFaces / Quarkus configuration

### `application.properties` (JSF-relevant keys)
```properties
# Servlet context
quarkus.http.port=8080
quarkus.http.root-path=/

# JSF state saving — CLIENT so postbacks can hit either instance (ADR-3)
quarkus.myfaces.faces-config.state-saving-method=client
# fallbacks / raw context params if extension does not surface the above:
# jakarta.faces.STATE_SAVING_METHOD=client
# jakarta.faces.PROJECT_STAGE=Production

# Facelets
jakarta.faces.FACELETS_REFRESH_PERIOD=-1
jakarta.faces.FACELETS_SKIP_COMMENTS=true
```

> The `quarkus-myfaces` extension exposes many params directly; where a typed
> Quarkus config key is unavailable, set the raw JSF context-param in `web.xml`
> (§3). **Client state saving is mandatory** for round-robin (plan 04).

### `web.xml` (`src/main/webapp/WEB-INF/web.xml`)
```xml
<web-app xmlns="https://jakarta.ee/xml/ns/jakartaee" version="6.0">
  <context-param>
    <param-name>jakarta.faces.STATE_SAVING_METHOD</param-name>
    <param-value>client</param-value>
  </context-param>
  <context-param>
    <param-name>jakarta.faces.PROJECT_STAGE</param-name>
    <param-value>Production</param-value>
  </context-param>
  <!-- Shared secret keys so client state written by app1 is readable by app2 -->
  <context-param>
    <param-name>org.apache.myfaces.SECRET</param-name>
    <param-value>${FT_JSF_SECRET}</param-value>
  </context-param>
  <context-param>
    <param-name>org.apache.myfaces.MAC_SECRET</param-name>
    <param-value>${FT_JSF_MAC_SECRET}</param-value>
  </context-param>

  <servlet>
    <servlet-name>Faces Servlet</servlet-name>
    <servlet-class>jakarta.faces.webapp.FacesServlet</servlet-class>
    <load-on-startup>1</load-on-startup>
  </servlet>
  <servlet-mapping>
    <servlet-name>Faces Servlet</servlet-name>
    <url-pattern>*.xhtml</url-pattern>
  </servlet-mapping>

  <welcome-file-list>
    <welcome-file>index.xhtml</welcome-file>
  </welcome-file-list>
</web-app>
```

> **Shared client-state secret** — both instances MUST use identical
> `SECRET`/`MAC_SECRET` (injected via env in compose, plan 07) so encrypted +
> MAC-signed view state produced by one node validates on the other.

### `faces-config.xml` and `beans.xml`
- `faces-config.xml` — mostly empty for POC (annotation-driven beans);
  register a global `ResourceBundle` for labels if desired.
- `beans.xml` — `bean-discovery-mode="annotated"`.

## 2. Web root decision

Use **`src/main/webapp`** for Facelets (`*.xhtml`, `WEB-INF/*`) — the servlet
convention MyFaces expects. Static assets (CSS/img) live under
`src/main/resources/META-INF/resources` (Quarkus static serving) **or** JSF
resource library `src/main/webapp/resources/`. **Chosen:** JSF resource
library so we can use `<h:outputStylesheet library="css" name="app.css"/>` and
get versioned resource URLs.

```
src/main/webapp/
├── index.xhtml                 # search screen (redirect/alias to tracker)
├── tracker.xhtml              # main single-page tracker (search + results)
├── WEB-INF/
│   ├── web.xml
│   ├── faces-config.xml
│   ├── beans.xml
│   └── templates/
│       └── layout.xhtml       # master template (header/footer shell)
└── resources/
    ├── css/app.css
    └── img/ (logo, bullseye icon)
```

## 3. Template hierarchy (Facelets)

### `WEB-INF/templates/layout.xhtml` — master shell
Reproduces the persistent chrome seen in every wireframe:
- **Header band** (dark green `#2f4a1f`-ish): gold logo tile + `FREIGHTTRACK`
  wordmark, `Shipment Tracker` H1, subtitle "Enter PRO numbers to locate your
  freight".
- **Gold accent rule** under the header.
- `<ui:insert name="content"/>` region.
- **Footer**: `FREIGHTTRACK - 24/7 DISPATCH: (800) 555-0100`.
- Responsive `<meta name="viewport" content="width=device-width, initial-scale=1"/>`.
- Include `app.css`.

Template clients (`tracker.xhtml`) use `<ui:composition template="/WEB-INF/templates/layout.xhtml">`
and fill the `content` insert.

## 4. Responsive design system (CSS)

Extracted palette/typography from the wireframes:

| Token | Value (approx) | Usage |
|-------|----------------|-------|
| `--ft-green-dark` | `#33471f` | header, active button |
| `--ft-green-muted`| `#9db47f` | disabled/empty-state button |
| `--ft-gold` | `#b8862b` | logo tile, accent rule, section labels |
| `--ft-bg` | `#ececec` | page body |
| `--ft-card` | `#ffffff` | input rows, cards |
| `--ft-badge-transit` | pale green | In Transit pill |
| `--ft-badge-delivered` | pale gold/tan | Delivered pill |
| body font | system sans-serif | UI text |
| mono font | monospace | PRO numbers, detail values, timeline times |

**Mobile-first strategy:**
- Base styles target ~375px phone (design1/2): single column, phone-frame max
  width ~430px centered.
- `@media (min-width: 768px)` → browser layout (design3/4): full-width header,
  wider input rows, `+ Add Number` right-aligned in the toolbar.
- `@media (min-width: 1024px)` → detail view becomes **2-column grid**
  (design4 D): left column = Shipment Details + Pickup & Driver, right column =
  Location & Delivery + Timeline. On mobile these stack (design2 D).

**Component classes (to author in `app.css`):**
- `.ft-header`, `.ft-logo`, `.ft-accent-rule`, `.ft-footer`
- `.pro-toolbar` (`PRO NUMBERS n/10` + `+ Add Number`)
- `.pro-row` (checkbox + input + `x`)
- `.btn-find` / `.btn-find--muted` / `.btn-find--active`
- `.empty-state` (bullseye circle + helper text + demo hint)
- `.results-toolbar` (`RESULTS n` + `Expand first`)
- `.result-card`, `.result-card__thumb`, `.badge`, `.badge-transit`, `.badge-delivered`
- `.detail-section`, `.detail-row` (label left / value right), `.section-label`
- `.timeline`, `.timeline-item`, `.timeline-marker--completed` (ring),
  `.timeline-marker--current` (filled dot)

## 5. Responsiveness = single markup, CSS-switched

Per requirement ("run on both mobile and web browsers"), we render **one set of
Facelets** and let CSS media queries adapt layout. We do **not** maintain
separate mobile/desktop views. The wireframe pairs (design1↔3, design2↔4)
represent the same views at different breakpoints.

## 6. Icons / assets

- Logo tile: gold rounded square (CSS) + `FREIGHTTRACK` text — no image needed.
- Bullseye empty-state icon: inline SVG in a Facelet fragment.
- Status badges: CSS pills (no images).
- Timeline markers: CSS circles.

Minimizing binary assets keeps the POC self-contained.

## 7. Exit criteria

- `tracker.xhtml` renders through `layout.xhtml` with correct header/footer.
- Page is legible and correctly laid out at 375px and 1280px.
- View state is emitted client-side (inspect page: hidden
  `jakarta.faces.ViewState` contains serialized state, not a server token).
