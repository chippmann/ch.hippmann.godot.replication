package ch.hippmann.godot.replication

import ch.hippmann.godot.replication.autoload.RemoteListenerReadyRedirector.Companion.notifyReady
import ch.hippmann.godot.utilities.logging.debug
import godot.Node

class RemoteListenerManager : WithRemoteListeners, WithNodeAccess by WithNodeAccessDelegate() {
    private var onPeerSubscribed: (peerId: Long) -> Unit = {}
    private var onPeerUnsubscribed: (peerId: Long) -> Unit = {}

    private val thisNodeAsWithRemoteListeners: WithRemoteListeners
        get() = thisNode.get() as WithRemoteListeners

    override val listeningPeers: MutableList<Long> = mutableListOf()

    override fun <T> T.initListening(
            onPeerSubscribed: (peerId: Long) -> Unit,
            onPeerUnsubscribed: (peerId: Long) -> Unit,
    ) where T : Node, T : WithRemoteListeners {
        initNodeAccess()
        this@RemoteListenerManager.onPeerSubscribed = onPeerSubscribed
        this@RemoteListenerManager.onPeerUnsubscribed = onPeerUnsubscribed

        this.ready.connect(this, WithRemoteListeners::notificationOnReadyForWithRemoteListeners)
        this.treeExiting.connect(this, WithRemoteListeners::notificationOnExitingTreeForWithRemoteListeners)
        this.multiplayer?.peerDisconnected?.connect(this, WithRemoteListeners::notificationOnPeerDisconnectedForWithRemoteListeners)
        debug { "RemoteListener[${this.name}]: initialised" }
    }

    override fun withRemoteListeners(block: (peerId: Long) -> Unit) {
        listeningPeers.forEach { peerId ->
            block(peerId)
        }
    }

    override suspend fun withRemoteListenersSuspending(block: suspend (peerId: Long) -> Unit) {
        listeningPeers.forEach { peerId ->
            block(peerId)
        }
    }

    override fun notificationOnReadyForWithRemoteListeners() {
        notifyReady { peerId ->
            ifAuthority {
                debug { "RemoteListener[${this.name}]: send ready to peers as authority" }
                rpcId(peerId, thisNodeAsWithRemoteListeners::peerOnAuthorityReadyForWithRemoteListeners)
            }
            ifPeer {
                if (peerId == 1L) {
                    debug { "RemoteListener[${this.name}]: request subscription with authority" }
                    rpcId(peerId, thisNodeAsWithRemoteListeners::authorityOnPeerSubscribeForWithRemoteListeners)
                }
            }
        }

//        ifAuthority {
//            debug { "RemoteListener[${this.name}]: send ready to peers as authority" }
//            rpc(thisNodeAsWithRemoteListeners::peerOnAuthorityReadyForWithRemoteListeners)
//        }
//        ifPeer {
//            debug { "RemoteListener[${this.name}]: request subscription with authority as peer" }
//            rpc(thisNodeAsWithRemoteListeners::authorityOnPeerSubscribeForWithRemoteListeners)
//        }
    }

    override fun notificationOnExitingTreeForWithRemoteListeners() {
        ifPeer {
            debug { "RemoteListener[${this.name}]: leaving tree. sending unsubscribe to authority" }
            rpc(thisNodeAsWithRemoteListeners::authorityOnPeerUnsubscribeForWithRemoteListeners)
        }
    }

    override fun notificationOnPeerDisconnectedForWithRemoteListeners(peerId: Long) {
        listeningPeers.removeAll { listeningPeerId -> listeningPeerId == peerId }
    }

    override fun authorityOnPeerSubscribeForWithRemoteListeners() {
        ifAuthority {
            val peerId = multiplayer?.getRemoteSenderId() ?: return

            if (peerId != 0) {
                debug { "RemoteListener[${this.name}]: received new subscription from peer with id $peerId" }
                listeningPeers.add(peerId.toLong())
                onPeerSubscribed(peerId.toLong())
            }
        }
    }

    override fun authorityOnPeerUnsubscribeForWithRemoteListeners() {
        ifAuthority {
            val peerId = multiplayer?.getRemoteSenderId() ?: return
            if (peerId != 0) {
                debug { "RemoteListener[${this.name}]: received unsubscribe from peer with id $peerId" }
                listeningPeers.remove(peerId.toLong())
                onPeerUnsubscribed(peerId.toLong())
            }
        }
    }

    override fun peerOnAuthorityReadyForWithRemoteListeners() {
        ifPeer {
            debug { "RemoteListener[${this.name}]: received that authority is ready. request subscription with authority as peer" }
            rpc(thisNodeAsWithRemoteListeners::authorityOnPeerSubscribeForWithRemoteListeners)
        }
    }
}
