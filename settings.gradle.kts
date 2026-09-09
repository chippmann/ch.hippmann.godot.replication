pluginManagement {
    includeBuild("build-logic")

    repositories {
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "ch.hippmann.godot.replication"

include("replication-core", "replication", "replication-processor", "sample", "end-to-end-tests", "rendezvous-service")
