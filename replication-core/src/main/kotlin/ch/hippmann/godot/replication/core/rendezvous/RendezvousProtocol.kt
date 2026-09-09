package ch.hippmann.godot.replication.core.rendezvous

import ch.hippmann.godot.replication.core.session.Endpoint
import kotlinx.serialization.Serializable

/**
 * JSON bodies of the rendezvous service, shared by the service and the library. The service keeps sessions by code, lets
 * members leave each other "knocks" carrying the endpoints to punch toward, tells a socket its public mapping and hands out
 * relay port pairs when punching fails.
 */
public object RendezvousProtocol {
    public const val VERSION: Int = 1
    public const val CODE_LENGTH: Int = 6

    /** No 0, O, 1 or I: a code is read aloud and typed by hand. */
    public const val CODE_ALPHABET: String = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    /** Player id of a knock from someone who is not admitted yet. */
    public const val JOINER: Int = 0
}

@Serializable
public data class SessionRegistration(
    val protocolVersion: Int = RendezvousProtocol.VERSION,
    val lobbyName: String,
    val maximumPlayers: Int,
    val passwordRequired: Boolean,
    val encrypted: Boolean,
    val masterId: Int,
    val masterCertificate: String,
    val masterEndpoints: List<Endpoint>,
    val playerCount: Int = 1,
)

@Serializable
public data class SessionRegistered(val code: String, val hostSecret: String)

@Serializable
public data class SessionHeartbeat(
    val hostSecret: String,
    val playerCount: Int,
    val masterId: Int,
    val masterEndpoints: List<Endpoint>,
    val masterCertificate: String,
)

@Serializable
public data class PublishedSession(
    val code: String,
    val lobbyName: String,
    val playerCount: Int,
    val maximumPlayers: Int,
    val passwordRequired: Boolean,
    val encrypted: Boolean,
    val masterId: Int,
    val masterCertificate: String,
    val masterEndpoints: List<Endpoint>,
)

/** What a member needs to keep a registered session alive and to be found by knocks. */
@Serializable
public data class OnlineSessionInfo(val serviceUrl: String, val code: String, val secret: String)

/** "Member [toMember], I am [fromMember] and my socket for you is reachable at [endpoints]; open your side and expect my dial." */
@Serializable
public data class Knock(
    val fromMember: Int,
    val toMember: Int,
    val endpoints: List<Endpoint>,
    val token: Long,
    /** Set when the caller gave up on punching: the callee must send toward the relay's callee port first. */
    val relay: RelayAllocation? = null,
)

@Serializable
public data class Knocks(val knocks: List<Knock>)

/**
 * The callee's reply to the knock with [token]: it punched toward the caller from a fresh socket and now listens on it.
 * [endpoints] is where the caller dials; empty when the caller asked for a relay, then it dials the relay's caller port.
 */
@Serializable
public data class KnockAnswer(val token: Long, val endpoints: List<Endpoint>)

@Serializable
public data class ObservedEndpoint(val address: String, val port: Int)

@Serializable
public data class RelayRequest(val fromMember: Int, val toMember: Int)

/** The caller talks to [callerPort], the callee to [calleePort]; the relay swaps what arrives on one onto the other. */
@Serializable
public data class RelayAllocation(val address: String, val callerPort: Int, val calleePort: Int)

@Serializable
public data class ServiceInformation(val protocolVersion: Int, val udpAddress: String, val udpPort: Int)
