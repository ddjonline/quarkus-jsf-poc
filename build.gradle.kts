plugins {
    kotlin("jvm")
    kotlin("plugin.allopen")
    kotlin("plugin.noarg")
    id("io.quarkus")
}

repositories { mavenCentral() }

val quarkusPlatformGroupId: String = project.findProperty("quarkusPlatformGroupId") as String
val quarkusPlatformArtifactId: String = project.findProperty("quarkusPlatformArtifactId") as String
val quarkusPlatformVersion: String = project.findProperty("quarkusPlatformVersion") as String

dependencies {
    implementation(enforcedPlatform("$quarkusPlatformGroupId:$quarkusPlatformArtifactId:$quarkusPlatformVersion"))

    implementation("io.quarkus:quarkus-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("io.quarkus:quarkus-arc")
    implementation("io.quarkus:quarkus-jackson")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    implementation("org.apache.myfaces.core.extensions.quarkus:myfaces-quarkus:4.1.3")
    implementation("org.apache.myfaces.core.extensions.quarkus:myfaces-quarkus-deployment:4.1.3")

    implementation("io.quarkus:quarkus-redis-client")

    implementation("io.quarkus:quarkus-hibernate-orm-panache-kotlin")
    implementation("io.quarkus:quarkus-jdbc-postgresql")

    implementation("io.quarkus:quarkus-smallrye-health")

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
    compilerOptions { javaParameters.set(true) }
}

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
