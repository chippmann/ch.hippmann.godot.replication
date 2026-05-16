plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.godot.kotlin.jvm)
    alias(libs.plugins.kotlinx.serialization)
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation(project(":replication"))
    implementation(libs.hippmann.godot.utilities)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotlinx.serialization.json)
}

kotlin {
    jvmToolchain(libs.versions.jvmToolchainVersion.get().toInt())
}

tasks.test {
    useJUnitPlatform()

    // The orchestrator needs to know where Godot lives and where the project dir is.
    systemProperty(
        "godot.bin",
        System.getenv("GODOT_BIN") ?: "/Applications/Godot.app/Contents/MacOS/Godot",
    )
    systemProperty(
        "godot.project.dir",
        projectDir.absolutePath,
    )

    // Tests spawn subprocesses; do not consume their stdio.
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = true
    }

    // Ensure jars + gdj are up to date before launching Godot subprocesses.
    dependsOn("copyJars")
}
