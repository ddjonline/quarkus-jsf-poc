# 07 — Docker Compose: 2× Quarkus + Redis + Postgres + HAProxy (round-robin)

Stand up the full topology locally: HAProxy front door round-robins to two
identical Quarkus app containers that share a Redis session store and a Postgres
database (seeded by SQL init scripts).

```
localhost:8080 ─► haproxy ─(roundrobin)─► app1:8080
                                      └─► app2:8080
                          app1, app2 ──► redis:6379
                          app1, app2 ──► postgres:5432  (seeded by SQL scripts)
```

## 1. Build artifact strategy

Use the **Quarkus JVM fast-jar** (`build/quarkus-app/`) for the POC (simplest;
native optional later). Two options:

- **A (recommended):** build the app once on the host (`./gradlew build`), copy
  `build/quarkus-app` into a slim JRE image. Fast iteration.
- **B:** multi-stage Dockerfile that runs the Gradle build inside the image
  (self-contained, slower).

Both app services use the **same image**, differentiated only by env
(`FT_INSTANCE_ID`).

## 2. `docker/app/Dockerfile.jvm` (option A)

```dockerfile
# Requires a JRE that supports the Java 25 bytecode produced by the build.
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY build/quarkus-app/lib/      /app/lib/
COPY build/quarkus-app/*.jar     /app/
COPY build/quarkus-app/app/      /app/app/
COPY build/quarkus-app/quarkus/  /app/quarkus/
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/quarkus-run.jar"]
```
(If a Temurin 25 JRE tag is unavailable at build time, use a 25 JDK image or an
early-access tag; note the substitution.)

## 3. `docker/haproxy/haproxy.cfg`

```
global
    log stdout format raw local0

defaults
    mode    http
    log     global
    option  httplog
    timeout connect 5s
    timeout client  30s
    timeout server  30s

frontend ft_frontend
    bind *:8080
    default_backend ft_backend

backend ft_backend
    balance roundrobin
    # NO `cookie` / stickiness directive — pure round-robin (ADR-2)
    option httpchk GET /q/health/ready
    http-check expect status 200
    server app1 app1:8080 check
    server app2 app2:8080 check
```

Key point: **no `cookie ... insert` line** → HAProxy does not pin clients to a
backend. Continuity comes entirely from Redis + JSF client state.

## 4. PostgreSQL init scripts (schema + seed)

Postgres officially runs any `*.sql` / `*.sh` placed in
`/docker-entrypoint-initdb.d` **once**, on first initialization of an empty data
directory (i.e. when the named volume is empty). Files execute in lexical order,
so prefix with numbers.

```
docker/postgres/initdb/
├── 01-schema.sql     # CREATE TABLE shipment, tracking_event (plan 06 §3)
└── 02-seed.sql       # INSERT the 2 demo shipments + timeline events
```

### `01-schema.sql`
```sql
CREATE TABLE shipment (
    pro_number         VARCHAR(32)  PRIMARY KEY,
    display_pro        VARCHAR(40)  NOT NULL,
    status             VARCHAR(24)  NOT NULL,
    origin             VARCHAR(80)  NOT NULL,
    destination        VARCHAR(80)  NOT NULL,
    shipper            VARCHAR(120) NOT NULL,
    consignee          VARCHAR(120) NOT NULL,
    commodity          VARCHAR(120) NOT NULL,
    weight_lbs         INTEGER      NOT NULL,
    pieces             INTEGER      NOT NULL,
    pickup_time        VARCHAR(48)  NOT NULL,
    driver_name        VARCHAR(80)  NOT NULL,
    driver_phone       VARCHAR(32)  NOT NULL,
    current_location   VARCHAR(120) NOT NULL,
    last_update        VARCHAR(48)  NOT NULL,
    estimated_delivery VARCHAR(48)  NOT NULL
);

CREATE TABLE tracking_event (
    id         BIGSERIAL PRIMARY KEY,
    pro_number VARCHAR(32) NOT NULL REFERENCES shipment(pro_number),
    seq        INTEGER     NOT NULL,
    time_label VARCHAR(16) NOT NULL,
    title      VARCHAR(80) NOT NULL,
    location   VARCHAR(120) NOT NULL,
    state      VARCHAR(16) NOT NULL
);
CREATE INDEX idx_event_pro_seq ON tracking_event(pro_number, seq);
```

### `02-seed.sql` (mirrors the wireframes — plan 06 §7)
```sql
INSERT INTO shipment VALUES
 ('00004821763','PRO-4821763','IN_TRANSIT','Memphis, TN','Nashville, TN',
  'Hartfield Industrial Supply','Valley Manufacturing Co.','Hydraulic Equipment',
  1240,6,'Jun 26, 2026 - 07:42 AM','Marcus D. Reyes','(901) 555-0182',
  'Jackson, TN - I-40 East MM 83','Jun 26, 2026 - 10:15 AM','Jun 26, 2026 - 2:00-4:00 PM'),
 ('00003390045','PRO-3390045','DELIVERED','Birmingham, AL','Atlanta, GA',
  'Southline Distribution','Peachtree Retail Group','Palletized Goods',
  2180,12,'Jun 25, 2026 - 09:10 AM','Andre L. Coleman','(205) 555-0147',
  'Atlanta, GA - Delivered','Jun 25, 2026 - 04:38 PM','Jun 25, 2026 - Delivered');

INSERT INTO tracking_event (pro_number,seq,time_label,title,location,state) VALUES
 ('00004821763',0,'07:42 AM','Picked up','Memphis, TN - Hartfield Industrial','COMPLETED'),
 ('00004821763',1,'08:55 AM','Departed terminal','Memphis, TN - Terminal 7','COMPLETED'),
 ('00004821763',2,'10:15 AM','En route','Jackson, TN - I-40 East MM 83','CURRENT'),
 ('00003390045',0,'09:10 AM','Picked up','Birmingham, AL - Southline DC','COMPLETED'),
 ('00003390045',1,'11:05 AM','Departed terminal','Birmingham, AL - Terminal 3','COMPLETED'),
 ('00003390045',2,'02:20 PM','Out for delivery','Atlanta, GA - Terminal 1','COMPLETED'),
 ('00003390045',3,'04:38 PM','Delivered','Atlanta, GA - Peachtree Retail','COMPLETED');
```

> **Re-seeding:** init scripts only run on an empty data dir. To re-apply after
> edits: `docker compose down -v` (drops the `pgdata` volume) then `up`.

## 5. `docker-compose.yml`

```yaml
services:
  redis:
    image: redis:7-alpine
    container_name: ft-redis
    command: ["redis-server","--save","","--appendonly","no"]
    ports: ["6379:6379"]            # exposed for debugging/verification
    healthcheck:
      test: ["CMD","redis-cli","ping"]
      interval: 5s
      timeout: 3s
      retries: 5

  postgres:
    image: postgres:16-alpine
    container_name: ft-postgres
    environment:
      POSTGRES_DB: ${FT_DB_NAME:-freighttrack}
      POSTGRES_USER: ${FT_DB_USER:-freighttrack}
      POSTGRES_PASSWORD: ${FT_DB_PASSWORD:-freighttrack}
    volumes:
      - ./docker/postgres/initdb:/docker-entrypoint-initdb.d:ro   # SQL seed scripts
      - pgdata:/var/lib/postgresql/data
    ports: ["5432:5432"]            # exposed for debugging/verification
    healthcheck:
      test: ["CMD-SHELL","pg_isready -U ${FT_DB_USER:-freighttrack} -d ${FT_DB_NAME:-freighttrack}"]
      interval: 5s
      timeout: 3s
      retries: 10

  app1:
    build:
      context: .
      dockerfile: docker/app/Dockerfile.jvm
    container_name: ft-app1
    environment:
      FT_INSTANCE_ID: app-1
      FT_REDIS_HOST: redis
      FT_DB_URL: jdbc:postgresql://postgres:5432/${FT_DB_NAME:-freighttrack}
      FT_DB_USER: ${FT_DB_USER:-freighttrack}
      FT_DB_PASSWORD: ${FT_DB_PASSWORD:-freighttrack}
      FT_JSF_SECRET: ${FT_JSF_SECRET:-changeme-shared-secret-32bytes!!}
      FT_JSF_MAC_SECRET: ${FT_JSF_MAC_SECRET:-changeme-shared-mac-secret-32b!!}
      QUARKUS_HTTP_HOST: 0.0.0.0
    depends_on:
      redis: { condition: service_healthy }
      postgres: { condition: service_healthy }
    expose: ["8080"]

  app2:
    build:
      context: .
      dockerfile: docker/app/Dockerfile.jvm
    container_name: ft-app2
    environment:
      FT_INSTANCE_ID: app-2
      FT_REDIS_HOST: redis
      FT_DB_URL: jdbc:postgresql://postgres:5432/${FT_DB_NAME:-freighttrack}
      FT_DB_USER: ${FT_DB_USER:-freighttrack}
      FT_DB_PASSWORD: ${FT_DB_PASSWORD:-freighttrack}
      FT_JSF_SECRET: ${FT_JSF_SECRET:-changeme-shared-secret-32bytes!!}
      FT_JSF_MAC_SECRET: ${FT_JSF_MAC_SECRET:-changeme-shared-mac-secret-32b!!}
      QUARKUS_HTTP_HOST: 0.0.0.0
    depends_on:
      redis: { condition: service_healthy }
      postgres: { condition: service_healthy }
    expose: ["8080"]

  haproxy:
    image: haproxy:2.9-alpine
    container_name: ft-haproxy
    volumes:
      - ./docker/haproxy/haproxy.cfg:/usr/local/etc/haproxy/haproxy.cfg:ro
    ports: ["8080:8080"]
    depends_on: [app1, app2]

volumes:
  pgdata:

networks:
  default:
    name: ft-net
```

> Credentials default to `freighttrack`/`freighttrack` for local convenience but
> are all overridable via a root `.env` file (compose auto-loads it). The app
> receives DB config **only** through `FT_DB_URL` / `FT_DB_USER` /
> `FT_DB_PASSWORD` (plan 06 §2).

## 6. Critical config parity between app1 & app2

| Concern | Requirement |
|---------|-------------|
| `FT_JSF_SECRET` / `FT_JSF_MAC_SECRET` | **Identical** on both — client view state portability (plan 04 §6) |
| `FT_REDIS_HOST` | Both → `redis` — shared session store |
| `FT_DB_URL` / `FT_DB_USER` / `FT_DB_PASSWORD` | Both → same `postgres` service — shared data |
| Image | Same image; only `FT_INSTANCE_ID` differs |
| JSF state saving | `client` (baked into `web.xml`/config, plan 03) |

The `FT_INSTANCE_ID` is surfaced back in the `X-Ft-Instance` response header by
the filter (plan 04 §4) to prove round-robin.

## 7. Health checks
- App exposes `/q/health/ready` via `quarkus-smallrye-health` (plan 02).
- HAProxy `option httpchk` gates traffic until each app is ready.
- Compose `depends_on` waits for Redis **and** Postgres healthy before apps
  start (so the schema/seed exist before the app connects).

## 8. Bring-up sequence
```
./gradlew build
docker compose build
docker compose up -d
# wait for health, then browse:
open http://localhost:8080
```

For browser E2E validation after the topology is healthy:
```
E2E_BASE_URL=http://localhost:8080 npm run e2e
```

Playwright runs from the host against HAProxy so tests exercise the same
round-robin path as a real browser. Keep this outside `docker-compose.yml` for
the POC unless CI later needs a dedicated test container.

## 9. Config wiring recap (env → app)
`application.properties` reads:
```
quarkus.redis.hosts=redis://${FT_REDIS_HOST:localhost}:6379
quarkus.datasource.jdbc.url=${FT_DB_URL}
quarkus.datasource.username=${FT_DB_USER}
quarkus.datasource.password=${FT_DB_PASSWORD}
```
`web.xml` reads `${FT_JSF_SECRET}` / `${FT_JSF_MAC_SECRET}` (use Quarkus config
expression or a startup observer that sets MyFaces secrets from env, since raw
`web.xml` does not interpolate env by default — implement via a
`ServletContextListener`/Quarkus config that injects the secrets programmatically).

> **Implementation note:** because plain `web.xml` won't expand `${ENV}`, the
> cleaner approach is to set the MyFaces SECRET/MAC via a small
> `@WebListener ServletContextListener` (or Quarkus `@Observes StartupEvent`)
> that reads env and calls `servletContext.setInitParameter(...)` before Faces
> initializes — document whichever the extension supports.

## 10. Exit criteria
- `docker compose up` → 5 healthy containers (app1, app2, redis, postgres, haproxy).
- On first start, Postgres runs `01-schema.sql` + `02-seed.sql`; verify with
  `docker exec ft-postgres psql -U freighttrack -d freighttrack -c 'SELECT pro_number,status FROM shipment;'`
  → returns 2 rows.
- Apps connect to Postgres using only env-supplied URL/user/password.
- `curl -si http://localhost:8080/tracker.xhtml` returns 200 with an
  `X-Ft-Instance` header; repeating alternates `app-1`/`app-2`.
- App reachable and functional end-to-end through HAProxy; searching `4821763`
  returns data sourced from Postgres.
- `E2E_BASE_URL=http://localhost:8080 npm run e2e` passes against HAProxy with
  both mobile and desktop Playwright projects.
