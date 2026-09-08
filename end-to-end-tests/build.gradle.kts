plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlinx.serialization)
}

kotlin {
    jvmToolchain(libs.versions.jvmToolchain.get().toInt())
}

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(kotlin("test"))
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    dependsOn(":sample:importProject")

    systemProperty("godot.executable", System.getenv("GODOT_EDITOR")?.takeIf { it.isNotBlank() } ?: "godot")
    systemProperty("sample.directory", rootProject.file("sample").absolutePath)
    systemProperty("logs.directory", layout.buildDirectory.dir("end-to-end-logs").get().asFile.absolutePath)
    systemProperty("verbose.transport", System.getProperty("verbose.transport") ?: "false")
    environment("JAVA_HOME", System.getProperty("java.home"))

    // The Godot processes load these, so a rebuilt sample must rerun the scenarios.
    inputs.dir(rootProject.file("sample/jvm")).withPropertyName("sampleJars")
    inputs.dir(rootProject.file("sample/addons/jvm/libs")).withPropertyName("addonLibraries")
    inputs.dir(rootProject.file("sample/scenes")).withPropertyName("sampleScenes")

    maxParallelForks = 1
    testLogging.showStandardStreams = true
}
