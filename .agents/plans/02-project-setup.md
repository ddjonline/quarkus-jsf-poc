# 02 — Project Setup (Gradle Kotlin DSL, Java 25 + Kotlin 2.4.0)

Scaffold the Quarkus project with a **mixed Kotlin + Java** source layout,
Gradle **Kotlin DSL**, Java 25 toolchain, and all runtime extensions.

## 1. Prerequisites

| Tool | Version | Check |
|------|---------|-------|
| JDK | **25** | `java -version` |
| Gradle | 8.10+ (wrapper committed) | `./gradlew --version` |
| Docker + Compose | current | `docker compose version` |
| Node.js + npm | current LTS | `node --version`, `npm --version` |
| Quarkus CLI (optional) | matching 3.37.1 | `quarkus --version` |

## 2. Steps (ordered)

1. **Initialize Gradle wrapper** at repo root (project already git-inited):
   `gradle init` → *application*, *Kotlin DSL build script*, or hand-author the
   files below. Commit `gradlew`, `gradlew.bat`, `gradle/wrapper/*`.
2. **Author `settings.gradle.kts`** — root project name + Quarkus plugin
   management repo.
3. **Author `build.gradle.kts`** — plugins, Java 25 toolchain, Kotlin config,
   Quarkus BOM + extensions (see §4/§5).
4. **Author `gradle.properties`** — Quarkus platform version, JVM args.
5. **Create source sets** — `src/main/kotlin`, `src/main/java`,
   `src/main/resources`, `src/main/resources/META-INF/resources` (web root),
   `src/main/webapp` (JSF `.xhtml` — see plan 03 for chosen web root).
6. **Add `application.properties`** skeleton (details in plans 03/04).
7. **Add Playwright test scaffold** — `package.json`, `playwright.config.ts`,
   and `tests/e2e/` for browser E2E validation in plan 08.
8. **Smoke test:** `./gradlew quarkusDev` boots; add a trivial JAX-RS or
    `index.xhtml` later to confirm.

> ⚠️ **DO NOT GENERATE CODE YET** — this document specifies the target files
> and their contents so implementation is mechanical when we begin.

## 3. Directory layout (target)

```
quarkus-jsf-poc/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradlew / gradlew.bat / gradle/wrapper/
├── src/
│   ├── main/
│   │   ├── kotlin/app/freighttrack/{domain,data,service,session}/
│   │   ├── java/app/freighttrack/web/
│   │   ├── resources/
│   │   │   ├── application.properties
│   │   │   └── META-INF/resources/           # static: css, img (see plan 03)
│   │   └── webapp/                            # JSF Facelets *.xhtml + WEB-INF
│   │       └── WEB-INF/{web.xml,faces-config.xml,beans.xml}
│   └── test/
│       └── kotlin/app/freighttrack/
├── docker/
│   ├── haproxy/haproxy.cfg
│   ├── postgres/initdb/{01-schema.sql,02-seed.sql}   # DB schema + seed
│   └── app/Dockerfile.jvm
├── tests/e2e/                                  # Playwright browser E2E specs
├── package.json
├── package-lock.json
├── playwright.config.ts
├── .env                                       # local DB creds / secrets (git-ignored)
└── docker-compose.yml
```

## 4. `settings.gradle.kts`

```kotlin
pluginManagement {
    val quarkusPluginVersion: String by settings
    repositories { mavenCentral(); gradlePluginPortal() }
    plugins {
        id("io.quarkus") version quarkusPluginVersion
        kotlin("jvm") version "2.4.0"
        kotlin("plugin.allopen") version "2.4.0"
        kotlin("plugin.noarg") version "2.4.0"
    }
}
rootProject.name = "freighttrack"
```

## 5. `gradle.properties`

```properties
quarkusPluginVersion=3.37.1
quarkusPlatformGroupId=io.quarkus.platform
quarkusPlatformArtifactId=quarkus-bom
quarkusPlatformVersion=3.37.1

org.gradle.caching=true
org.gradle.configuration-cache=false   # Quarkus plugin friendliness
```

## 6. `build.gradle.kts`

```kotlin
plugins {
    kotlin("jvm")
    kotlin("plugin.allopen")
    kotlin("plugin.noarg")
    id("io.quarkus")
}

repositories { mavenCentral() }

val quarkusPlatformGroupId: String by project
val quarkusPlatformArtifactId: String by project
val quarkusPlatformVersion: String by project

dependencies {
    implementation(enforcedPlatform("$quarkusPlatformGroupId:$quarkusPlatformArtifactId:$quarkusPlatformVersion"))

    // --- Core / Kotlin ---
    implementation("io.quarkus:quarkus-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("io.quarkus:quarkus-arc")            // CDI
    implementation("io.quarkus:quarkus-jackson")        // JSON serialization (Redis session)

    // --- JSF (Jakarta Faces via Apache MyFaces) ---
    implementation("org.apache.myfaces.core.extensions.quarkus:myfaces-quarkus:4.1.3")
    implementation("org.apache.myfaces.core.extensions.quarkus:myfaces-quarkus-deployment:4.1.3")

    // --- Redis (shared session store) ---
    implementation("io.quarkus:quarkus-redis-client")

    // --- Jackson Kotlin module (required for @JsonProperty on data classes) ---
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // --- PostgreSQL (shipment data source) ---
    implementation("io.quarkus:quarkus-hibernate-orm-panache-kotlin")
    implementation("io.quarkus:quarkus-jdbc-postgresql")

    // --- Health / observability (optional but useful for compose healthchecks) ---
    implementation("io.quarkus:quarkus-smallrye-health")

    // --- Test ---
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("io.rest-assured:rest-assured")
}

group = "app.freighttrack"
version = "1.0.0-POC"

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(25)) }
}

kotlin {
    jvmToolchain(25)
    compilerOptions { javaParameters.set(true) }   // helps CDI/JSF param names
}

// CDI/JSF need proxyable (open) + no-arg beans when written in Kotlin.
// JPA entities (Panache Kotlin) also require open + no-arg.
allOpen {
    annotation("jakarta.enterprise.context.ApplicationScoped")
    annotation("jakarta.enterprise.context.RequestScoped")
    annotation("jakarta.enterprise.context.SessionScoped")
    annotation("jakarta.inject.Singleton")
    annotation("jakarta.ws.rs.Path")
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}
noArg {
    annotation("jakarta.enterprise.context.ApplicationScoped")
    annotation("jakarta.enterprise.context.SessionScoped")
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.Embeddable")
}

tasks.test { useJUnitPlatform() }
```

## 7. Playwright test scaffold

Add a small Node/npm project at repo root for browser E2E only; keep the Quarkus
app build in Gradle.

`package.json` target shape:
```json
{
  "private": true,
  "scripts": {
    "e2e": "playwright test",
    "e2e:ui": "playwright test --ui"
  },
  "devDependencies": {
    "@playwright/test": "^1.49.0"
  }
}
```

`playwright.config.ts` target shape:
```ts
import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './tests/e2e',
  retries: process.env.CI ? 2 : 0,
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:8080',
    trace: 'retain-on-failure'
  },
  projects: [
    { name: 'chromium-mobile', use: { ...devices['Pixel 5'], viewport: { width: 375, height: 812 } } },
    { name: 'chromium-desktop', use: { ...devices['Desktop Chrome'], viewport: { width: 1280, height: 900 } } }
  ]
});
```

Install once with `npm install` and `npx playwright install --with-deps chromium`
where needed. Tests should run against an already-started app or compose topology;
do not have Playwright silently start Docker.

> **Version pin caveat:** confirm the exact `quarkus-myfaces` release aligned
> with Quarkus **3.37.1** at implementation time (Quarkiverse versions track the
> core BOM). If no 3.37.x-aligned MyFaces exists yet, fall back to the nearest
> compatible Quarkiverse release and document it.

## 8. Rationale for the plugins

- `kotlin("jvm")` — compile Kotlin sources.
- `plugin.allopen` — CDI produces runtime proxies of scoped beans; Kotlin
  classes are `final` by default, so scoped-annotated classes must be `open`.
- `plugin.noarg` — Session/Application scoped beans need a synthetic no-arg
  constructor for proxy/serialization.
- JSF managed beans stay in **Java** (plan 00, ADR-5) to avoid needing these on
  the view layer, but the plugins keep Kotlin service/session beans valid.

## 9. Java 25 considerations

- Toolchain pinned so Gradle provisions/uses JDK 25 consistently across dev and
  the Docker build stage.
- Preview features are **not** required; keep `--enable-preview` off for
  reproducible container builds.
- Confirm the chosen Quarkus 3.37.1 line officially supports JDK 25 as a build
  and runtime target; if only "runs on" is supported, keep bytecode target at a
  supported LTS and run on 25.

### Host JDK 26 caveat

Kotlin 2.4.0's `org.jetbrains.kotlin.com.intellij.util.lang.JavaVersion.parse()`
does **not** recognize version string `"26.0.1"`, causing an
`IllegalArgumentException` when the build runs on JDK 26. The **toolchain**
must be set to **25** even if the host JDK is 26 — Gradle auto-provisions JDK 25
for compilation while the build scripts run on the host JDK 26.

System Gradle 9.x handles this correctly; Gradle wrapper 8.10 does not build on
JDK 26 at all. Upgrade wrapper to ≥9.x if the host JDK is ≥25.

## 10. Exit criteria

- `./gradlew build` compiles the mixed Kotlin/Java tree.
- `./gradlew quarkusDev` starts and serves a placeholder page.
- Extensions resolved: myfaces (+undertow), redis-client,
  hibernate-orm-panache-kotlin, jdbc-postgresql, health.
- In dev mode, Quarkus **Dev Services** auto-provision Postgres + Redis
  containers (no manual DB needed for local `quarkusDev`).
- `npm install` creates `package-lock.json`; `npm run e2e -- --list` discovers
  Playwright specs once plan 08 tests are added.
