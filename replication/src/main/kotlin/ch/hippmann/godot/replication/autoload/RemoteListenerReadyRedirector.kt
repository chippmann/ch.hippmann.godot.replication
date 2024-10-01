package ch.hippmann.godot.replication.autoload

import ch.hippmann.godot.replication.WithNodeAccess
import ch.hippmann.godot.replication.WithRemoteListeners
import godot.Node
import godot.annotation.RegisterClass
import godot.annotation.RegisterFunction
import godot.annotation.Rpc
import godot.annotation.RpcMode
import godot.extensions.getNodeAs

@RegisterClass
class RemoteListenerReadyRedirector: Node() {
    companion object {
        @PublishedApi
        internal val listeners: MutableMap<String, (peerId: Long) -> Unit> = mutableMapOf()

        @Suppress("NOTHING_TO_INLINE")
        inline fun <T> T.notifyReady(noinline onRemoteReady: (peerId: Long) -> Unit) where T: WithRemoteListeners, T: WithNodeAccess {
            val node = thisNode.get() ?: return
            val nodePath = node.getPath().path

            listeners[nodePath] = onRemoteReady

            val remoteListenerReadyRedirector = requireNotNull(thisNode.get()?.getNodeAs<RemoteListenerReadyRedirector>("/root/${RemoteListenerReadyRedirector::class.simpleName}")) {
                "Did not find ${RemoteListenerReadyRedirector::class.simpleName} at path '/root/${RemoteListenerReadyRedirector::class.simpleName}'. Did you forget to load it as autoload singleton?"
            }
            remoteListenerReadyRedirector.rpc(remoteListenerReadyRedirector::remoteReady, nodePath)
        }

        @Suppress("NOTHING_TO_INLINE")
        inline fun <T> T.deregister() where T: WithRemoteListeners, T: WithNodeAccess {
            val node = thisNode.get() ?: return
            val nodePath = node.getPath().path

            listeners.remove(nodePath)
        }
    }

    @Rpc(rpcMode = RpcMode.ANY)
    @RegisterFunction
    fun remoteReady(nodePath: String) {
        val peerId = multiplayer?.getRemoteSenderId() ?: return
        listeners[nodePath]?.invoke(peerId.toLong())
    }
}
