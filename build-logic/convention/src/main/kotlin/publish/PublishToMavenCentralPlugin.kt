package publish

import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.MavenPublishPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication

abstract class PublishExtension {
    abstract val description: Property<String>
    abstract val repositoryUrl: Property<String>
    abstract val mavenCentralUser: Property<String>
    abstract val mavenCentralPassword: Property<String>
    abstract val gpgInMemoryKey: Property<String>
    abstract val gpgPassword: Property<String>
}

class PublishToMavenCentralPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.plugins.apply(org.gradle.api.publish.maven.plugins.MavenPublishPlugin::class.java)
        val extension = target.extensions.create("publishConfig", PublishExtension::class.java)
        extension.repositoryUrl.convention("github.com/chippmann/ch.hippmann.godot.replication")
        extension.mavenCentralUser.convention(target.propertyOrEnvironment("mavenCentralUsername"))
        extension.mavenCentralPassword.convention(target.propertyOrEnvironment("mavenCentralPassword"))
        extension.gpgInMemoryKey.convention(target.propertyOrEnvironment("signingInMemoryKey"))
        extension.gpgPassword.convention(target.propertyOrEnvironment("signingInMemoryKeyPassword"))

        target.afterEvaluate { evaluatedProject ->
            val canSign = listOf(extension.mavenCentralUser, extension.mavenCentralPassword, extension.gpgInMemoryKey, extension.gpgPassword)
                .all { credential -> credential.isPresent }

            evaluatedProject.pluginManager.apply(MavenPublishPlugin::class.java)
            if (canSign) {
                evaluatedProject.logger.info("Will sign artifact for project \"${evaluatedProject.name}\" and setup publishing")
                evaluatedProject.extensions.getByType(MavenPublishBaseExtension::class.java).apply {
                    publishToMavenCentral()
                    signAllPublications()
                }
            } else {
                evaluatedProject.logger.warn("Cannot sign project \"${evaluatedProject.name}\" as credentials are missing. Publishing will only work to maven local!")
            }

            evaluatedProject.configurePom(extension)
        }
    }

    private fun Project.configurePom(extension: PublishExtension) {
        val baseUrl = extension.repositoryUrl.get()
        extensions.getByType(PublishingExtension::class.java).publications.withType(MavenPublication::class.java).configureEach { publication ->
            publication.pom { pom ->
                pom.name.set(name)
                pom.description.set(extension.description)
                pom.url.set("https://$baseUrl")
                pom.licenses { licenses ->
                    licenses.license { license ->
                        license.name.set("MIT License")
                        license.url.set("https://$baseUrl/blob/main/LICENSE")
                        license.distribution.set("https://$baseUrl/blob/main/LICENSE")
                    }
                }
                pom.developers { developers ->
                    developers.developer { developer ->
                        developer.id.set("maintainer")
                        developer.name.set("Cedric Hippmann")
                        developer.url.set("https://github.com/chippmann")
                        developer.email.set("cedric@hippmann.com")
                    }
                }
                pom.scm { scm ->
                    scm.connection.set("scm:git:https://$baseUrl")
                    scm.developerConnection.set("scm:git:$baseUrl.git")
                    scm.tag.set("main")
                    scm.url.set("https://$baseUrl")
                }
            }
        }
    }

    private fun Project.propertyOrEnvironment(name: String): String? =
        (findProperty(name) as String?) ?: System.getenv(name)
}
