package ch.hippmann.godot.replication.session

import ch.hippmann.godot.replication.NetworkConfiguration
import ch.hippmann.godot.replication.sync.FrameClock
import godot.annotation.Script
import godot.api.Node

/**
 * The runtime node the library installs under the scene tree root. It runs before every game node in `_process`
 * and after every game node in `_physicsProcess`, so received state is visible during the frame and sent state
 * is the freshest.
 */
@Script
class ReplicationManager : Node() {
    internal var runtime: SessionRuntime? = null
        private set

    override fun _ready() {
        processPriority = Int.MIN_VALUE / 2
        processPhysicsPriority = Int.MAX_VALUE / 2
        instance = this
    }

    override fun _process(delta: Double) {
        FrameClock.advance()
        val runtime = runtime ?: return
        runtime.pump()
        runtime.frame()
    }

    override fun _physicsProcess(delta: Double) {
        FrameClock.advance()
        val runtime = runtime ?: return
        runtime.pump()
        runtime.physicsStep()
        runtime.transport.flush()
    }

    override fun _exitTree() {
        if (instance === this) {
            instance = null
        }
    }

    internal fun startSession(configuration: NetworkConfiguration): SessionRuntime {
        check(runtime == null) { "A session is already running" }
        return SessionRuntime(this, configuration).also { runtime = it }
    }

    internal fun endSession() {
        runtime = null
    }

    companion object {
        var instance: ReplicationManager? = null
            private set
    }
}
