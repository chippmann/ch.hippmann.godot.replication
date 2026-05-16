package ch.hippmann.godot.replication.integrationtests

import ch.hippmann.godot.replication.SyncConfig
import ch.hippmann.godot.replication.SyncConfigs
import ch.hippmann.godot.replication.syncConfig
import godot.annotation.RegisterClass
import godot.annotation.RegisterFunction
import godot.api.Node
import godot.api.OS
import godot.core.PackedStringArray
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Root script of every integration-test scene. Reads the test arguments Godot was
 * launched with, instantiates the scenario class by FQCN, and runs `runAsServer` /
 * `runAsClient` on it. Writes a JSON result file before quitting.
 *
 * The scene that hosts this script is responsible for declaring the Replicator (and
 * any other test fixtures) — the scenario code does NOT build the scene graph.
 */
@RegisterClass
class TestRunner : Node() {
    private lateinit var args: TestArgs
    private lateinit var context: TestContext

    /**
     * Throwaway mutable property used by scenarios that need to construct a [SyncConfigs]
     * through the DSL without touching a real fixture. The Synchronizer DSL requires a
     * `KMutableProperty0` on a `Node` subclass — TestRunner already is a Node.
     */
    var probeIntValue: Int = 0

    /**
     * Build a [SyncConfigs] entry for [probeIntValue] using the syncConfig DSL with
     * the requested transfer mode/channel. Scenarios use this to inspect that the
     * DSL surface actually propagates options into the resulting [SyncConfig].
     */
    fun buildProbeSyncConfig(
        method: SyncConfig.SyncMethod,
        channel: SyncConfig.SyncChannel,
    ): SyncConfigs = syncConfig {
        property(::probeIntValue) {
            syncMethod = method
            syncChannel = channel
        }
    }

    /**
     * Build a [SyncConfigs] entry that also exercises the `syncOnSpawn` and
     * `syncOnTick` toggles. Used by `SyncConfigDslTest` to pin every DSL →
     * `SyncConfig` channel of propagation.
     */
    fun buildProbeSyncConfigFull(
        method: SyncConfig.SyncMethod,
        channel: SyncConfig.SyncChannel,
        spawn: Boolean,
        tick: Boolean,
    ): SyncConfigs = syncConfig {
        property(::probeIntValue) {
            syncMethod = method
            syncChannel = channel
            syncOnSpawn = spawn
            syncOnTick = tick
        }
    }

    /** Probe target for the custom-serializer DSL surface. */
    var probeLabel: String = ""

    /**
     * Build a [SyncConfigs] entry that overrides the serializer/deserializer
     * lambdas. The encoder wraps the value with a marker prefix so the test can
     * see — by inspecting the wire string — that the custom path actually ran.
     */
    fun buildCustomSerializerSyncConfig(): SyncConfigs = syncConfig {
        property(
            property = ::probeLabel,
            serializer = { "__CUSTOM__:$this" },
            deserializer = { it.removePrefix("__CUSTOM__:") },
        )
    }

    private val mainThreadDispatcher = GodotMainDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + mainThreadDispatcher)

    @RegisterFunction
    override fun _ready() {
        val userArgs: List<String> = OS.getCmdlineUserArgs().toList()
        println("[TestRunner] user args: $userArgs")

        args = try {
            TestArgs.parse(userArgs)
        } catch (parseFailure: Throwable) {
            System.err.println("[TestRunner] failed to parse args: ${parseFailure.message}")
            getTree()?.quit(2)
            return
        }

        context = TestContext(this, args)

        scope.launch {
            val scenario = try {
                instantiateScenario(args.scenarioFullyQualifiedClassName)
            } catch (instantiationFailure: Throwable) {
                fail(
                    "could not instantiate scenario ${args.scenarioFullyQualifiedClassName}",
                    instantiationFailure,
                )
                return@launch
            }

            try {
                when (args.role) {
                    Role.SERVER -> scenario.runAsServer(context)
                    Role.CLIENT -> scenario.runAsClient(context)
                }
                pass()
            } catch (scenarioFailure: Throwable) {
                fail("scenario threw", scenarioFailure)
            }
        }
    }

    @RegisterFunction
    override fun _process(delta: Double) {
        mainThreadDispatcher.drain()
    }

    private fun instantiateScenario(fullyQualifiedClassName: String): TestScenario {
        val scenarioClass = Class.forName(fullyQualifiedClassName)
        val constructor = scenarioClass.getDeclaredConstructor().apply { isAccessible = true }
        return constructor.newInstance() as TestScenario
    }

    private suspend fun pass() {
        println("[TestRunner] ${args.peerName} passed")
        context.writeResult(passed = true)
        gracefulShutdown(exitCode = 0)
    }

    private suspend fun fail(message: String, cause: Throwable) {
        System.err.println("[TestRunner] ${args.peerName} failed: $message")
        cause.printStackTrace()
        context.writeResult(
            passed = false,
            error = "$message: ${cause.javaClass.name}: ${cause.message}\n${cause.stackTraceToString()}",
        )
        gracefulShutdown(exitCode = 1)
    }

    /**
     * Close the multiplayer peer so its disconnect packet flushes before we tear the
     * SceneTree down (otherwise remote peers wait on ENet's keepalive timeout —
     * seconds). Then let the SceneTree settle for a handful of frames so any
     * Synchronizer ticker coroutines on Dispatchers.Default observe tree_exiting
     * cancellation and exit before the JVM is asked to shut down. Without this delay
     * those coroutines are mid-JNI when the JVM requests a safepoint at process exit
     * and the JVM SIGABRTs — see BUGS.md #20.
     */
    private suspend fun gracefulShutdown(exitCode: Int) {
        runCatching { multiplayer?.multiplayerPeer?.close() }
        kotlinx.coroutines.delay(300)
        getTree()?.quit(exitCode)
    }

    private fun PackedStringArray.toList(): List<String> {
        val list = mutableListOf<String>()
        for (index in 0 until size) list += get(index)
        return list
    }
}
