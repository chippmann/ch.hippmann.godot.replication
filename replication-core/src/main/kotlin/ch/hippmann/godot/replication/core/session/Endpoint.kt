package ch.hippmann.godot.replication.core.session

import kotlinx.serialization.Serializable

@Serializable
public data class Endpoint(val address: String, val port: Int) {
    override fun toString(): String = "$address:$port"
}
