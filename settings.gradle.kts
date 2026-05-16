pluginManagement {
    includeBuild("build-logic")

    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
    }

    resolutionStrategy.eachPlugin {
        if (requested.id.id == "com.utopia-rise.godot-kotlin-jvm") {
            useModule("com.utopia-rise:godot-gradle-plugin:${requested.version}")
        }
    }
}

// The published utilities-0.0.9 jar references kotlinx-datetime 0.7.x types (e.g.
// DateTimeFormatBuilder.WithYearMonth) and indirectly kotlin.time.Clock — which the
// godot-kotlin-jvm bootstrap stdlib strips out. Pull utilities from its sibling repo
// instead so it builds against this project's pinned kotlinx-datetime 0.6.2.
includeBuild("../ch.hippmann.godot.utilities") {
    dependencySubstitution {
        substitute(module("ch.hippmann.godot:utilities")).using(project(":utilities"))
    }
}

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        mavenLocal()
        mavenCentral()
    }
}

plugins {
    // https://plugins.gradle.org/plugin/org.gradle.toolchains.foojay-resolver-convention
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}

rootProject.name = "replication"

include("replication")
include("replication-it")
