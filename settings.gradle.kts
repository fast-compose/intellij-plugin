rootProject.name = "fast-compose-intellij-plugin"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

// Builds against a local Fast Compose engine checkout instead of Maven Central: -PlocalEngine=../fast-compose, or
// localEngine in local.properties (not committed), which IDE syncs read since they can't pass -P.
val localEngine: String? = providers.gradleProperty("localEngine").orNull?.takeIf { it.isNotBlank() }
    ?: file("local.properties").takeIf { it.isFile }?.let { file ->
        java.util.Properties().apply { file.inputStream().use { load(it) } }.getProperty("localEngine")?.takeIf { it.isNotBlank() }
    }
localEngine?.let { path ->
    includeBuild(path) {
        dependencySubstitution {
            substitute(module("io.github.fast-compose:fast-compose")).using(project(":fast-compose"))
        }
    }
}
