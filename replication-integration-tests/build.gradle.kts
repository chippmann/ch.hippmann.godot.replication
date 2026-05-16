plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.godot.kotlin.jvm)
    alias(libs.plugins.kotlinx.serialization)
}

repositories {
    mavenLocal()
    mavenCentral()
}

// godot-kotlin-jvm 0.13.1-4.4.1 bundles a Kotlin stdlib that's missing
// `kotlin.time.Clock` (the runtime stripped that experimental class even though
// the version reads 2.1.10). kotlinx-datetime 0.7.x depends on it; pin to the
// last 0.6.x which has its own Clock implementation.
configurations.configureEach {
    resolutionStrategy {
        force("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")
    }
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

// The godot-kotlin-jvm KSP processor generates a godot.Entry class for every Kotlin source
// set. We only want it for `main` (which gets loaded into Godot). The test source set is
// pure JVM Kotest code that runs OUTSIDE Godot, so disable the KSP step there.
tasks.matching { it.name == "kspTestKotlin" }.configureEach {
    enabled = false
}

// The plugin writes dependency-supplied .gdj files under gdj/dependencies/<libProjectName>/...
// but godot-jvm resolves @RegisterClass instances by canonical package path (gdj/<package>/...),
// so we mirror each dependency tree up one level. Idempotent — safe to re-run.
val flattenDependencyGdj by tasks.registering {
    val gdjRoot = projectDir.resolve("gdj")
    val depRoot = gdjRoot.resolve("dependencies")
    doLast {
        if (!depRoot.exists()) return@doLast
        depRoot.listFiles()?.filter { it.isDirectory }?.forEach { libDir ->
            libDir.walkTopDown().filter { it.isFile && it.extension == "gdj" }.forEach { src ->
                val rel = src.relativeTo(libDir)
                val dst = gdjRoot.resolve(rel)
                dst.parentFile.mkdirs()
                src.copyTo(dst, overwrite = true)
            }
        }
    }
}

tasks.matching { it.name == "copyJars" }.configureEach {
    finalizedBy(flattenDependencyGdj)
}

tasks.test {
    useJUnitPlatform()

    // Each test class spawns up to 3 Godot subprocesses. Running test classes in
    // parallel forks (Gradle's default for some configurations) would mean N×3 Godot
    // processes contending for CPU, ports, and the JVM bootstrap simultaneously — we
    // saw flaky pollUntil timeouts under that pressure. Force one test class at a time.
    maxParallelForks = 1

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
