package ch.hippmann.godot.replication

import godot.Node
import godot.annotation.RegisterFunction
import godot.annotation.Rpc
import godot.annotation.RpcMode
import godot.annotation.TransferMode


interface Synchronized: WithRemoteListeners {

    val syncConfig: SyncConfigs

    fun <T> T.initSynchronization() where T : Node, T: Synchronized

    fun performSynchronization()

    @RegisterFunction
    fun notificationOnReadyForSynchronized()

    @Rpc(rpcMode = RpcMode.ANY)
    @RegisterFunction
    fun replicateForSynchronizedReliable(fqName: String, data: String)

    @Rpc(rpcMode = RpcMode.ANY, transferMode = TransferMode.UNRELIABLE)
    @RegisterFunction
    fun replicateForSynchronizedUnreliable(fqName: String, data: String)

    @Rpc(rpcMode = RpcMode.ANY, transferMode = TransferMode.UNRELIABLE_ORDERED, transferChannel = 0)
    @RegisterFunction
    fun replicateForSynchronizedUnreliableOrderedChannel0(fqName: String, data: String)
    @Rpc(rpcMode = RpcMode.ANY, transferMode = TransferMode.UNRELIABLE_ORDERED, transferChannel = 1)
    @RegisterFunction
    fun replicateForSynchronizedUnreliableOrderedChannel1(fqName: String, data: String)
    @Rpc(rpcMode = RpcMode.ANY, transferMode = TransferMode.UNRELIABLE_ORDERED, transferChannel = 2)
    @RegisterFunction
    fun replicateForSynchronizedUnreliableOrderedChannel2(fqName: String, data: String)
    @Rpc(rpcMode = RpcMode.ANY, transferMode = TransferMode.UNRELIABLE_ORDERED, transferChannel = 3)
    @RegisterFunction
    fun replicateForSynchronizedUnreliableOrderedChannel3(fqName: String, data: String)
    @Rpc(rpcMode = RpcMode.ANY, transferMode = TransferMode.UNRELIABLE_ORDERED, transferChannel = 4)
    @RegisterFunction
    fun replicateForSynchronizedUnreliableOrderedChannel4(fqName: String, data: String)
    @Rpc(rpcMode = RpcMode.ANY, transferMode = TransferMode.UNRELIABLE_ORDERED, transferChannel = 5)
    @RegisterFunction
    fun replicateForSynchronizedUnreliableOrderedChannel5(fqName: String, data: String)
    @Rpc(rpcMode = RpcMode.ANY, transferMode = TransferMode.UNRELIABLE_ORDERED, transferChannel = 6)
    @RegisterFunction
    fun replicateForSynchronizedUnreliableOrderedChannel6(fqName: String, data: String)
    @Rpc(rpcMode = RpcMode.ANY, transferMode = TransferMode.UNRELIABLE_ORDERED, transferChannel = 7)
    @RegisterFunction
    fun replicateForSynchronizedUnreliableOrderedChannel7(fqName: String, data: String)
    @Rpc(rpcMode = RpcMode.ANY, transferMode = TransferMode.UNRELIABLE_ORDERED, transferChannel = 8)
    @RegisterFunction
    fun replicateForSynchronizedUnreliableOrderedChannel8(fqName: String, data: String)
    @Rpc(rpcMode = RpcMode.ANY, transferMode = TransferMode.UNRELIABLE_ORDERED, transferChannel = 9)
    @RegisterFunction
    fun replicateForSynchronizedUnreliableOrderedChannel9(fqName: String, data: String)
}
