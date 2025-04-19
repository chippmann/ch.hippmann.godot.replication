package ch.hippmann.godot.replication

import godot.api.Node
import godot.annotation.RegisterFunction
import godot.annotation.Rpc
import godot.annotation.RpcMode

interface WithRemoteListeners {
    val listeningPeers: List<Long>

    fun <T> T.initListening(
        onPeerSubscribed: (peerId: Long) -> Unit = {},
        onPeerUnsubscribed: (peerId: Long) -> Unit = {},
    ) where T : Node, T : WithRemoteListeners

    fun withRemoteListeners(block: (peerId: Long) -> Unit)

    suspend fun withRemoteListenersSuspending(block: suspend (peerId: Long) -> Unit)

    @RegisterFunction
    fun notificationOnReadyForWithRemoteListeners()

    @RegisterFunction
    fun notificationOnExitingTreeForWithRemoteListeners()

    @RegisterFunction
    fun notificationOnPeerDisconnectedForWithRemoteListeners(peerId: Long)

    @Rpc(rpcMode = RpcMode.ANY)
    @RegisterFunction
    fun authorityOnPeerSubscribeForWithRemoteListeners()

    @Rpc(rpcMode = RpcMode.ANY)
    @RegisterFunction
    fun authorityOnPeerUnsubscribeForWithRemoteListeners()

    @Rpc(rpcMode = RpcMode.ANY)
    @RegisterFunction
    fun peerOnAuthorityReadyForWithRemoteListeners()
}
