package ch.hippmann.godot.replication

data class DiscoveredSession(
    val address: String,
    val port: Int,
    val lobbyName: String,
    val playerCount: Int,
    val maximumPlayers: Int,
    val passwordRequired: Boolean,
)
