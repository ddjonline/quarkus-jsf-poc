# FreightTrack Quarkus JSF POC

Implementation plans live in `.agents/plans/` and should be executed in numeric
order. The project targets Quarkus + JSF with Redis-backed session continuity,
Postgres-seeded shipment data, and HAProxy round-robin across two app instances.

Key rule: user-entered PRO numbers are numeric-only and normalized to an
11-digit, zero-padded canonical key before lookup, while UI labels keep the
short `PRO-4821763` style display form.
