plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlinx.serialization)
    id("ch.hippmann.publish")
}

kotlin {
    jvmToolchain(libs.versions.jvmToolchain.get().toInt())
    explicitApi()
}

dependencies {
    api(libs.kotlinx.serialization.core)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(kotlin("test"))
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}


publishConfig {
    description.set("Pure Kotlin protocol, codec and state machines of the Godot-JVM replication library.")
}
