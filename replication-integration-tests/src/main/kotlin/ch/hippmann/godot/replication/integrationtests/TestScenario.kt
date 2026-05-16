package ch.hippmann.godot.replication.it

interface TestScenario {
    suspend fun runAsServer(ctx: TestContext)
    suspend fun runAsClient(ctx: TestContext)
}

enum class Role { SERVER, CLIENT }

data class TestArgs(
    val scenarioFqcn: String,
    val role: Role,
    val port: Int,
    val peerId: String,
    val resultDir: String,
    val clientCount: Int,
) {
    companion object {
        fun parse(userArgs: List<String>): TestArgs {
            val map = userArgs
                .zipWithNext()
                .filterIndexed { idx, _ -> idx % 2 == 0 }
                .filter { (k, _) -> k.startsWith("--") }
                .associate { (k, v) -> k.removePrefix("--") to v }

            return TestArgs(
                scenarioFqcn = required(map, "scenario"),
                role = Role.valueOf(required(map, "role").uppercase()),
                port = required(map, "port").toInt(),
                peerId = required(map, "peer-id"),
                resultDir = required(map, "result-dir"),
                clientCount = map["client-count"]?.toInt() ?: 0,
            )
        }

        private fun required(map: Map<String, String>, key: String): String =
            requireNotNull(map[key]) { "missing required test arg --$key" }
    }
}
