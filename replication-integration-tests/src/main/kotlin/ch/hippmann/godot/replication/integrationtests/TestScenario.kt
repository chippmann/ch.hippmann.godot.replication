package ch.hippmann.godot.replication.integrationtests

interface TestScenario {
    suspend fun runAsServer(context: TestContext)
    suspend fun runAsClient(context: TestContext)

    companion object {
        /** Default scene used when the scenario class isn't `@TestScene`-annotated. */
        const val DEFAULT_SCENE_PATH = "res://scenes/replication_basic.tscn"

        /** Resolves the scene path for a scenario class — annotation wins, default else. */
        fun scenePathFor(scenarioClass: Class<out TestScenario>): String =
            scenarioClass.getAnnotation(TestScene::class.java)?.path ?: DEFAULT_SCENE_PATH
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
