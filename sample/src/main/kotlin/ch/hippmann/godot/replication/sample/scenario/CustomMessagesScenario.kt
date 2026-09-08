package ch.hippmann.godot.replication.sample.scenario

import ch.hippmann.godot.replication.LobbyConfiguration
import ch.hippmann.godot.replication.Network
import ch.hippmann.godot.replication.PlayerProfile
import ch.hippmann.godot.replication.rpc.Target
import godot.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable

@Serializable
data class Chat(val text: String, val stamp: Int)

@Serializable
data class Ping(val nonce: Int)

/** Typed messages to everyone and to the master only. */
class CustomMessagesScenario : Scenario {
    override suspend fun run(runner: ScenarioRunner, context: ScenarioContext) {
        if (context.isHost) {
            Network.host(LobbyConfiguration("Mara's arena"), PlayerProfile(context.playerName), context.port)
            ScenarioLog.event("hosting", "port" to context.port)
        } else {
            Network.join(context.joinAddress, context.joinPort, PlayerProfile(context.playerName))
            ScenarioLog.event("joined")
        }
        runner.awaitConnected(3)

        val chats = LinkedHashMap<Int, Chat>()
        val pings = ArrayList<Int>()
        runner.launch { Network.messages<Chat>().collect { received -> chats[received.sender.value] = received.payload } }
        runner.launch { Network.messages<Ping>().collect { received -> pings += received.sender.value } }
        delay(300)

        Network.send(Chat("hello from ${context.playerName}", Network.localPlayerId.value * 100))
        if (!Network.isMaster) Network.send(Ping(Network.localPlayerId.value), Target.Master, reliable = false)

        runner.awaitUntil { chats.size == 2 }
        if (Network.isMaster) runner.awaitUntil { pings.size == 2 }
        delay(500)
        ScenarioLog.event("messages", "chats" to chats.values.map { chat -> chat.text }.sorted(), "stamps" to chats.values.map { chat -> chat.stamp }.sorted(), "pings" to pings.sorted())
        if (Network.isMaster) runner.awaitConnected(1) else delay(300)
        Network.leave()
    }
}
