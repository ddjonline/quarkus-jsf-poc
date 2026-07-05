pluginManagement {
    val quarkusPluginVersion: String = providers.gradleProperty("quarkusPluginVersion").get()
    repositories { mavenCentral(); gradlePluginPortal() }
    plugins {
        id("io.quarkus") version quarkusPluginVersion
        kotlin("jvm") version "2.4.0"
        kotlin("plugin.allopen") version "2.4.0"
        kotlin("plugin.noarg") version "2.4.0"
    }
}
rootProject.name = "freighttrack"
