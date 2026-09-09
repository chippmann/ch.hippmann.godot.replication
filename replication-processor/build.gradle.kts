plugins {
    alias(libs.plugins.kotlin.jvm)
    id("ch.hippmann.publish")
}

kotlin {
    jvmToolchain(libs.versions.jvmToolchain.get().toInt())
}

dependencies {
    implementation(libs.ksp.api)
}

publishConfig {
    description.set("KSP processor that generates the bindings for @Synced properties of ch.hippmann.godot:replication.")
}
