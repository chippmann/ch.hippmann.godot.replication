plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.godot.kotlin.jvm)
    alias(libs.plugins.kotlinx.serialization)
    id("ch.hippmann.publish")
}

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
    compileOnly(libs.godot.kotlin.jvm.api)
    implementation(libs.hippmann.godot.utilities)
    api(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
}

kotlin {
    jvmToolchain(libs.versions.jvmToolchainVersion.get().toInt())
}

val projectName = name
val baseUrl = "github.com/chippmann/ch.hippmann.godot.replication"
publishConfig {
    mavenCentralUser = project.propOrEnv("mavenCentralUsername")
    mavenCentralPassword = project.propOrEnv("mavenCentralPassword")
    gpgInMemoryKey = project.propOrEnv("signingInMemoryKey")
    gpgPassword = project.propOrEnv("signingInMemoryKeyPassword")

    pom {
        name.set(projectName)
        description.set("Basic godot multiplayer replication implemented in Kotlin for Kotlin.")
        url.set("https://$baseUrl")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://$baseUrl/blob/main/LICENSE")
                distribution.set("https://$baseUrl/blob/main/LICENSE")
            }
        }
        developers {
            developer {
                id.set("maintainer")
                name.set("Cedric Hippmann")
                url.set("https://github.com/chippmann")
                email.set("cedric@hippmann.com")
            }
        }
        scm {
            connection.set("scm:git:https://$baseUrl")
            developerConnection.set("scm:git:$baseUrl.git")
            tag.set("main")
            url.set("https://$baseUrl")
        }
    }
}

tasks {
    // disable shadow jar creation to be able to publish (otherwise we have a jar conflict. It's not needed anyways. Ideally this should be fixed in Godot Kotlin directly)
    shadowJar.configure {
        enabled = false
    }

    afterEvaluate {
        findByName("copyJars")?.dependsOn(withType(Sign::class.java))
    }
}

fun Project.propOrEnv(name: String): String? {
    var property: String? = findProperty(name) as String?
    if (property == null) {
        property = System.getenv(name)
    }
    return property
}