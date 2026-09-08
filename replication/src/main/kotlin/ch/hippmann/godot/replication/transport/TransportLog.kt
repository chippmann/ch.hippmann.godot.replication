package ch.hippmann.godot.replication.transport

import godot.global.GD

/** Opt in diagnostics for connection and packet routing problems; off by default because every line is a JNI call. */
object TransportLog {
    var enabled: Boolean = false

    inline fun log(message: () -> String) {
        if (enabled) GD.print("Replication: ${message()}")
    }
}
