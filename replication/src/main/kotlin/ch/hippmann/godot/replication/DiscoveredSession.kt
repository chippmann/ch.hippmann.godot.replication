package ch.hippmann.godot.replication

data class DiscoveredSession(
    val address: String,
    val port: Int,
    val lobbyName: String,
    val playerCount: Int,
    val maximumPlayers: Int,
    val passwordRequired: Boolean,
    val encrypted: Boolean = false,
    /** PEM to pin when joining an encrypted session found on the LAN. */
    val certificate: String = "",
)
