import godot.annotation.processor.classgraph.AnnotationProcessingMode
import godot.gradle.GodotLanguage
import java.util.concurrent.TimeUnit

plugins {
    alias(libs.plugins.godot.jvm)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.ksp)
}

godot {
    languages.set(setOf(GodotLanguage.KOTLIN))
    registration {
        annotationProcessingMode.set(AnnotationProcessingMode.Inferred)
    }
    godotProjectDirectory.set(projectDir)
    isGodotCoroutinesEnabled.set(true)
}

dependencies {
    implementation(project(":replication"))
    ksp(project(":replication-processor"))
}

fun godotExecutable(): String = System.getenv("GODOT_EDITOR")?.takeIf { it.isNotBlank() } ?: "godot"

val installAddonLibraries = tasks.register<Copy>("installAddonLibraries") {
    group = "godot"
    description = "Copies the Godot-JVM native libraries from GODOT_JVM_ADDON_LIBRARIES into addons/jvm/libs."

    val librariesSource = System.getenv("GODOT_JVM_ADDON_LIBRARIES")
        ?: rootProject.file("../../../04_godot/harness/tests/addons/jvm/libs").absolutePath
    from(librariesSource)
    into(projectDir.resolve("addons/jvm/libs"))
}

val importProject = tasks.register("importProject") {
    group = "godot"
    description = "Imports the sample project headless so the class cache knows every JVM class."
    dependsOn(tasks.named("build"), installAddonLibraries)

    val workingDirectory = projectDir
    val logFile = layout.buildDirectory.file("import-project.log")
    val executable = godotExecutable()

    doLast {
        val classCache = workingDirectory.resolve(".godot/global_script_class_cache.cfg")
        repeat(2) { attempt ->
            if (attempt > 0 && classCache.isFile && classCache.readText().contains("ReplicationManager")) return@doLast
            // Godot writes megabytes of output while importing; a pipe nobody drains would deadlock it.
            val log = logFile.get().asFile.also { it.parentFile.mkdirs() }
            val process = ProcessBuilder(executable, "--headless", "--path", workingDirectory.absolutePath, "--import")
                .redirectErrorStream(true)
                .redirectOutput(log)
                .start()
            if (!process.waitFor(5, TimeUnit.MINUTES)) {
                process.destroyForcibly()
                process.waitFor(1, TimeUnit.MINUTES)
            }
        }
        check(classCache.isFile && classCache.readText().contains("ReplicationManager")) {
            val log = logFile.get().asFile
            val tail = if (log.isFile) log.readLines().takeLast(40).joinToString("\n") else "(no log written)"
            "The import did not register the library classes, see $log:\n$tail"
        }
    }
}

tasks.matching { task -> task.name == "registrarGenerationIndexExistingRegistrationFiles" }.configureEach {
    mustRunAfter(installAddonLibraries)
}

// KSP also runs over the registrar source set the Godot plugin generates, which Gradle otherwise flags as an undeclared dependency.
tasks.matching { task -> task.name == "kspRegistrarGenerationKotlin" }.configureEach {
    mustRunAfter(tasks.matching { task -> task.name == "registrarGenerationGenerateFiles" })
}

// The Godot plugin repackages jvm/*.jar through finalizers of `jar`; Godot must not start while they still write.
importProject.configure {
    mustRunAfter(tasks.withType<Jar>(), tasks.withType<Copy>())
}
