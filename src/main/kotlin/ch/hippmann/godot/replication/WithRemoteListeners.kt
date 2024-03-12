package ch.hippmann.godot.replication

import godot.Node
import godot.annotation.RegisterFunction
import godot.annotation.Rpc

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

    @Rpc
    @RegisterFunction
    fun authorityOnPeerSubscribeForWithRemoteListeners()

    @Rpc
    @RegisterFunction
    fun authorityOnPeerUnsubscribeForWithRemoteListeners()

    @Rpc
    @RegisterFunction
    fun peerOnAuthorityReadyForWithRemoteListeners()
}
