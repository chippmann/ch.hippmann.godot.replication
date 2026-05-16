package ch.hippmann.godot.replication.it

import ch.hippmann.godot.utilities.coroutines.mainDispatcher
import godot.annotation.RegisterClass
import godot.annotation.RegisterFunction
import godot.api.Node
import godot.api.OS
import godot.core.PackedStringArray
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@RegisterClass
class TestRunner : Node() {
    private lateinit var args: TestArgs
    private lateinit var ctx: TestContext

    @Suppress("DEPRECATION")
    private val scope = CoroutineScope(SupervisorJob() + mainDispatcher())

    @RegisterFunction
    override fun _ready() {
        val userArgs: List<String> = OS.getCmdlineUserArgs().toList()
        println("[TestRunner] user args: $userArgs")

        args = try {
            TestArgs.parse(userArgs)
        } catch (t: Throwable) {
            System.err.println("[TestRunner] failed to parse args: ${t.message}")
            getTree()?.quit(2)
            return
        }

        ctx = TestContext(this, args)

        scope.launch {
            val scenario = try {
                instantiateScenario(args.scenarioFqcn)
            } catch (t: Throwable) {
                fail("could not instantiate scenario ${args.scenarioFqcn}", t)
                return@launch
            }

            try {
                when (args.role) {
                    Role.SERVER -> scenario.runAsServer(ctx)
                    Role.CLIENT -> scenario.runAsClient(ctx)
                }
                pass()
            } catch (t: Throwable) {
                fail("scenario threw", t)
            }
        }
    }

    private fun instantiateScenario(fqcn: String): TestScenario {
        val cls = Class.forName(fqcn)
        val ctor = cls.getDeclaredConstructor().apply { isAccessible = true }
        return ctor.newInstance() as TestScenario
    }

    private fun pass() {
        println("[TestRunner] ${args.peerId} passed")
        ctx.writeResult(passed = true, data = ctx.report())
        getTree()?.quit(0)
    }

    private fun fail(message: String, t: Throwable) {
        System.err.println("[TestRunner] ${args.peerId} failed: $message")
        t.printStackTrace()
        ctx.writeResult(
            passed = false,
            data = ctx.report(),
            error = "$message: ${t.javaClass.name}: ${t.message}\n${t.stackTraceToString()}",
        )
        getTree()?.quit(1)
    }

    private fun PackedStringArray.toList(): List<String> {
        val out = mutableListOf<String>()
        for (i in 0 until size) out += get(i)
        return out
    }
}
