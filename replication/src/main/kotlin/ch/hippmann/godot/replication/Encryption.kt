package ch.hippmann.godot.replication

import ch.hippmann.godot.replication.transport.DirectStrategy

/** When links are wrapped in DTLS. Members pin each other's certificates; a typed public address trusts on first use. */
enum class Encryption {
    OFF,

    /** Sessions registered with a rendezvous service and joins to public addresses; the LAN stays plain. */
    ONLINE_ONLY,

    ALWAYS;

    fun forSession(online: Boolean): Boolean = this == ALWAYS || (this == ONLINE_ONLY && online)

    fun forTypedAddress(address: String): Boolean = this == ALWAYS || (this == ONLINE_ONLY && !DirectStrategy.isPrivate(address))
}
