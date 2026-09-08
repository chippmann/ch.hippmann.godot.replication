package ch.hippmann.godot.replication.endtoend

import java.io.File

object SampleProject {
    val godotExecutable: String = System.getProperty("godot.executable") ?: "godot"
    val directory: File = File(System.getProperty("sample.directory") ?: "../sample").absoluteFile
    val logsDirectory: File = File(System.getProperty("logs.directory") ?: "build/end-to-end-logs").absoluteFile
}
