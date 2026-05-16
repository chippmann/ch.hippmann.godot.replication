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
