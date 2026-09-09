plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlinx.serialization)
    id("ch.hippmann.publish")
}

kotlin {
    jvmToolchain(libs.versions.jvmToolchain.get().toInt())
}

dependencies {
    api(project(":replication-core"))
    api(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    compileOnly(libs.godot.common)
    compileOnly(libs.godot.core)
    compileOnly(libs.godot.api)
    compileOnly(libs.godot.bootstrap)
    compileOnly(libs.godot.extension)
    compileOnly(libs.godot.coroutines)
}


publishConfig {
    description.set("Peer to peer multiplayer replication for Godot-JVM: lobby, master migration, ownership and property synchronization.")
}
