# FreightTrack Quarkus JSF POC

FreightTrack is a proof-of-concept shipment tracking application built with Quarkus, JSF, Redis, PostgreSQL, and HAProxy. It demonstrates a round-robin-safe server-rendered web application where request handling can alternate between two application instances without losing the user's tracking session.

## Architectural Overview

The application is designed around a simple but important constraint: no request may depend on instance-local heap state. HAProxy distributes traffic across two Quarkus instances using round-robin balancing, so either instance must be able to process any request from the same browser session.

Core request flow:

1. The user opens the JSF tracking page through HAProxy at `http://localhost:8080`.
2. HAProxy forwards each request to either `app1` or `app2` using `balance roundrobin`.
3. Request-scoped JSF managed beans hydrate the current tracking search state from Redis using the `FT_SESSION` cookie.
4. Submitted PRO numbers are validated, normalized to an 11-digit zero-padded lookup key, and looked up independently.
5. Shipment data is read from PostgreSQL through the repository/service layer.
6. Updated tracking search state is persisted back to Redis before the response completes.
7. JSF client-side state saving keeps JSF view state independent of application-instance memory.

Important design rules:

- `FT_SESSION` identifies the application session in Redis, but it is not used for load-balancer stickiness.
- JSF beans are request scoped so they do not retain per-user state in a specific JVM.
- Domain objects are serializable POJOs and are safe to store in Redis-backed session state.
- JPA entities are mapped to domain objects before leaving the data layer.
- PostgreSQL owns the production/compose schema through SQL init scripts in `docker/postgres/initdb/`.
- Hibernate schema generation is disabled in production/compose with `schema-management.strategy=none`.

## Tech Stack Description

- **Quarkus 3.37.1**: Java/Kotlin application runtime, CDI container, health endpoints, Dev Services, and production packaging.
- **Java 25**: Toolchain-pinned JVM version used for Java sources and Gradle builds.
- **Kotlin 2.4.0**: Used for domain models, repositories, services, session handling, and configuration-oriented application code.
- **JSF / Jakarta Faces via Quarkiverse MyFaces**: Server-rendered UI layer using Apache MyFaces on Quarkus/Undertow.
- **Gradle Kotlin DSL**: Build system for the mixed Kotlin and Java codebase.
- **Redis**: Shared session store for tracking search state across application instances.
- **PostgreSQL**: Persistent shipment and tracking-event database.
- **Hibernate ORM Panache Kotlin**: Data access layer over PostgreSQL.
- **HAProxy 2.9**: Local reverse proxy and round-robin load balancer for the two app containers.
- **Docker Compose**: Full local topology for `app1`, `app2`, Redis, PostgreSQL, and HAProxy.

## Quickstart For Building The Application

Prerequisites:

- JDK 25 available to Gradle toolchains.
- Docker and Docker Compose for the full multi-container topology.
- No local PostgreSQL or Redis setup is required for `quarkusDev` or tests because Quarkus Dev Services can provision them automatically.

Build and test locally:

```bash
./gradlew build
./gradlew test
```

Run the application in Quarkus dev mode:

```bash
./gradlew quarkusDev
```

Run the full round-robin topology:

```bash
docker compose build
docker compose up -d
```

Stop the full topology:

```bash
docker compose down
```

Reset the PostgreSQL seeded data volume and start fresh:

```bash
docker compose down -v
docker compose up -d
```

Production/compose configuration is supplied by environment variables. The compose file provides development defaults, but real deployments should set explicit values:

- `FT_DB_URL`
- `FT_DB_USER`
- `FT_DB_PASSWORD`
- `FT_REDIS_HOST`
- `FT_JSF_SECRET`
- `FT_JSF_MAC_SECRET`
- `FT_INSTANCE_ID`

## Quick Start For Using The Application

Start the full topology:

```bash
docker compose up -d
```

Open the application:

```text
http://localhost:8080
```

Use one or more demo PRO numbers:

- `4821763` for an in-transit shipment.
- `3390045` for a delivered shipment.

PRO number behavior:

- Input must be numeric only.
- Input length must be 1 to 11 digits.
- Up to 10 PRO numbers can be searched at once.
- Values are normalized to 11 digits for lookup, so `4821763` is searched as `00004821763`.
- Each PRO is looked up independently and rendered as its own result panel.

## Additional Notes

### HAProxy Round-Robin Setup

The HAProxy configuration lives in `docker/haproxy/haproxy.cfg`:

```haproxy
frontend ft_frontend
    bind *:8080
    default_backend ft_backend

backend ft_backend
    balance roundrobin
    option httpchk GET /q/health/ready
    http-check expect status 200
    server app1 app1:8080 check
    server app2 app2:8080 check
```

The important detail is `balance roundrobin`. HAProxy does not use sticky sessions. Requests are distributed between `app1` and `app2`, and the application remains session-safe because user tracking state is stored in Redis instead of in a specific Quarkus JVM.

### How To Verify Round-Robin Behavior

Start the compose topology:

```bash
docker compose up -d
```

Check the application through HAProxy several times:

```bash
curl -I http://localhost:8080
curl -I http://localhost:8080
curl -I http://localhost:8080
```

Responses should alternate between app instances when the application emits the `X-Ft-Instance` header, for example `app-1` and `app-2`.

You can also check the ready endpoint routed through HAProxy:

```bash
curl -i http://localhost:8080/q/health/ready
```

### How To Verify Seeded Data

PostgreSQL is seeded from `docker/postgres/initdb/` when the database volume is first created. Verify the demo shipments with:

```bash
docker compose exec postgres psql -U freighttrack -d freighttrack -c "SELECT pro_number, status FROM shipment ORDER BY pro_number;"
```

Expected seeded shipments:

- `00003390045`, `DELIVERED`
- `00004821763`, `IN_TRANSIT`

### How To Run Tests

Run the project test suite:

```bash
./gradlew test
```

Run a full build, including tests:

```bash
./gradlew build
```

The test profile uses Quarkus Dev Services for PostgreSQL and Redis, so Docker should be available when tests require those services.

### Operational Notes

- Use `docker compose logs -f haproxy app1 app2` to observe traffic through the proxy and application containers.
- Use `docker compose ps` to confirm all five services are running: `haproxy`, `app1`, `app2`, `redis`, and `postgres`.
- If seed data appears stale or missing, reset the database volume with `docker compose down -v` and start the stack again.
- Do not use local JVM memory, static fields, or session-scoped JSF beans for user tracking state; doing so breaks round-robin safety.
