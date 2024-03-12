plugins {
    alias(libs.plugins.godot.kotlin.jvm)
    alias(libs.plugins.kotlinx.serialization)
    `maven-publish`
}

group = "ch.hippmann.godot"
version = libs.versions.godot.kotlin.jvm.replication.get()

repositories {
    mavenLocal()
    mavenCentral()
}

godot {
    classPrefix.set("Repl")
    projectName.set("replication")
    isRegistrationFileGenerationEnabled.set(false)
}

dependencies {
    compileOnly(libs.godot.kotlin.jvm)
    implementation(libs.hippmann.godot.utilities)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
}

kotlin {
    jvmToolchain(libs.versions.jvmToolchainVersion.get().toInt())
}

java {
    withSourcesJar()
    withJavadocJar()
}

publishing {
    publications {
        @Suppress("UNUSED_VARIABLE")
        val utilities by creating(MavenPublication::class) {
            pom {
                name.set(project.name)
                description.set("Basic godot multiplayer replication implemented in Kotlin for Kotlin")
            }
            artifactId = "replication"
            description = "Basic godot multiplayer replication implemented in Kotlin for Kotlin"
            artifact(tasks.jar)
            artifact(tasks.getByName("sourcesJar"))
            artifact(tasks.getByName("javadocJar"))
        }
    }
}