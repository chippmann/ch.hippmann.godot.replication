package ch.hippmann.godot.replication.integrationtests

interface TestScenario {
    /**
     * Godot scene file (as a `res://` path) that this scenario expects to be the main
     * scene of the test process. Most scenarios use [DEFAULT_SCENE], which contains a
     * [TestRunner] root with a single Replicator child whose `managedScenes` is preset.
     * Scenarios that need a different node hierarchy can point at their own `.tscn`.
     */
    val scenePath: String get() = DEFAULT_SCENE

    suspend fun runAsServer(context: TestContext)
    suspend fun runAsClient(context: TestContext)

    companion object {
        const val DEFAULT_SCENE = "res://scenes/replication_basic.tscn"
    }
}

enum class Role { SERVER, CLIENT }

data class TestArgs(
    val scenarioFullyQualifiedClassName: String,
    val role: Role,
    val port: Int,
    val peerName: String,
    val resultDir: String,
    val expectedClientCount: Int,
) {
    companion object {
        fun parse(userArgs: List<String>): TestArgs {
            val map = userArgs
                .zipWithNext()
                .filterIndexed { index, _ -> index % 2 == 0 }
                .filter { (key, _) -> key.startsWith("--") }
                .associate { (key, value) -> key.removePrefix("--") to value }

            return TestArgs(
                scenarioFullyQualifiedClassName = required(map, "scenario"),
                role = Role.valueOf(required(map, "role").uppercase()),
                port = required(map, "port").toInt(),
                peerName = required(map, "peer-name"),
                resultDir = required(map, "result-dir"),
                expectedClientCount = map["expected-client-count"]?.toInt() ?: 0,
            )
        }

        private fun required(map: Map<String, String>, key: String): String =
            requireNotNull(map[key]) { "missing required test arg --$key" }
    }
}
