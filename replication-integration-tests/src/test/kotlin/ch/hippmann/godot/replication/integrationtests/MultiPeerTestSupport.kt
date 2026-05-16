package ch.hippmann.godot.replication.integrationtests

import io.kotest.assertions.fail
import java.io.File

internal object MultiPeerTestSupport {
    val godotBinary: String = System.getProperty("godot.bin")
        ?: error("godot.bin system property not set — configure GODOT_BIN env var or pass -Dgodot.bin=...")
    val godotProjectDir: File = File(
        System.getProperty("godot.project.dir")
            ?: error("godot.project.dir system property not set"),
    )
}

internal fun List<PeerResult>.assertAllPassed() {
    val failures = filterNot { it.passed }
    if (failures.isNotEmpty()) fail(renderMultiPeerFailure(this))
}

internal fun List<PeerResult>.clientsOnly(): List<PeerResult> = filter { it.role == "CLIENT" }

internal fun List<PeerResult>.serverResult(): PeerResult =
    single { it.role == "SERVER" }
