package ch.hippmann.godot.replication.session

import ch.hippmann.godot.replication.JoinFailure
import ch.hippmann.godot.replication.JoinStep
import ch.hippmann.godot.replication.Network
import ch.hippmann.godot.replication.NetworkEvent
import ch.hippmann.godot.replication.NetworkState
import ch.hippmann.godot.replication.core.level.LevelState
import ch.hippmann.godot.replication.core.lobby.LobbyConfiguration
import ch.hippmann.godot.replication.core.lobby.LobbyState
import ch.hippmann.godot.replication.core.lobby.PlayerProfile
import ch.hippmann.godot.replication.core.session.Endpoint
import ch.hippmann.godot.replication.core.session.MemberRecord
import ch.hippmann.godot.replication.core.session.Membership
import ch.hippmann.godot.replication.core.session.PasswordChallenge
import ch.hippmann.godot.replication.core.session.PasswordVerifier
import ch.hippmann.godot.replication.core.session.PlayerId
import ch.hippmann.godot.replication.core.session.SessionId
import ch.hippmann.godot.replication.core.wire.Admitted
import ch.hippmann.godot.replication.core.wire.Challenge
import ch.hippmann.godot.replication.core.wire.DiscoveryAnswer
import ch.hippmann.godot.replication.core.wire.Hello
import ch.hippmann.godot.replication.core.wire.HelloAccepted
import ch.hippmann.godot.replication.core.wire.JoinProof
import ch.hippmann.godot.replication.core.wire.JoinRequest
import ch.hippmann.godot.replication.core.wire.Leave
import ch.hippmann.godot.replication.core.wire.LeaveReason
import ch.hippmann.godot.replication.core.wire.MessageType
import ch.hippmann.godot.replication.core.wire.PROTOCOL_VERSION
import ch.hippmann.godot.replication.core.wire.Redirect
import ch.hippmann.godot.replication.core.wire.RejectReason
import ch.hippmann.godot.replication.core.rendezvous.SessionRegistration
import ch.hippmann.godot.replication.rendezvous.OnlineSession
import ch.hippmann.godot.replication.rendezvous.RendezvousClient
import ch.hippmann.godot.replication.transport.ConnectionTarget
import ch.hippmann.godot.replication.transport.DirectStrategy
import ch.hippmann.godot.replication.transport.TransportLog
import ch.hippmann.godot.replication.transport.EnetLink
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import godot.global.GD
import java.security.SecureRandom

internal suspend fun SessionRuntime.host(lobby: LobbyConfiguration, profile: PlayerProfile, port: Int, online: Boolean) {
    check(membership == null) { "Already in a session" }
    val host = transport.bind(port, maximumPeers = 2 * lobby.maximumPlayers)
    localPlayerId = PlayerId.FIRST_HOST
    localProfile = profile
    lobbyConfiguration = lobby
    passwordVerifier = lobby.password?.takeIf { password -> password.isNotEmpty() }?.let(PasswordVerifier::forPassword)
    var endpoints = DirectStrategy.localEndpoints(host.port)
    if (online) {
        val session = connectOnline(code = null)
        endpoints = session.refreshListenerEndpoints()
        val registration = SessionRegistration(
            lobbyName = lobby.name, maximumPlayers = lobby.maximumPlayers, passwordRequired = lobby.passwordRequired,
            encrypted = false, masterId = localPlayerId.value, masterCertificate = certificatePem, masterEndpoints = endpoints,
        )
        val registered = session.client.register(registration)
        session.code = registered.code
        session.secret = registered.hostSecret
        this.online = session
    }
    val record = MemberRecord(localPlayerId, profile, endpoints)
    membership = Membership.hosting(SessionId(SecureRandom().nextLong()), record)
    masterRole = MasterRole(this, passwordVerifier)
    installMeshPeer()
    if (configuration.enableDiscovery) startDiscoveryResponder()
    this.online?.start()
    publishSession()
    transport.runDeferred()
    Network.setState(NetworkState.Connected)
    replication.start()
}

/** A client for the configured service plus the UDP endpoint it announces; nothing is registered yet. */
private suspend fun SessionRuntime.connectOnline(code: String?): OnlineSession {
    val url = configuration.rendezvousUrl ?: throw IllegalStateException("No rendezvous service is configured")
    val client = RendezvousClient(url)
    val information = client.information()
    return OnlineSession(this, client, code.orEmpty(), secret = null, information.udpAddress, information.udpPort)
}

internal suspend fun SessionRuntime.joinByCode(code: String, profile: PlayerProfile, password: String?) {
    check(membership == null) { "Already in a session" }
    Network.setState(NetworkState.Joining(JoinStep.CONNECTING))
    val session = try {
        connectOnline(code.uppercase())
    } catch (failure: Exception) {
        abort()
        throw failure
    }
    val published = session.client.find(code) ?: run { abort(); throw JoinFailure.Unreachable("code $code", 0) }
    online = session
    join(ConnectionTarget(PlayerId(published.masterId), published.masterEndpoints), profile, password)
}

internal fun SessionRuntime.startDiscoveryResponder() {
    discovery.startResponder {
        val membership = membership
        DiscoveryAnswer(
            protocolVersion = PROTOCOL_VERSION,
            sessionPort = transport.listeningHost?.port ?: 0,
            lobbyName = lobbyConfiguration.name,
            playerCount = membership?.members?.size ?: 0,
            maximumPlayers = lobbyConfiguration.maximumPlayers,
            passwordRequired = lobbyConfiguration.passwordRequired,
        )
    }
}

internal suspend fun SessionRuntime.join(address: String, port: Int, profile: PlayerProfile, password: String?) {
    check(membership == null) { "Already in a session" }
    join(ConnectionTarget(PlayerId.NONE, listOf(Endpoint(address, port))), profile, password)
}

private suspend fun SessionRuntime.join(master: ConnectionTarget, profile: PlayerProfile, password: String?) {
    Network.setState(NetworkState.Joining(JoinStep.CONNECTING))
    try {
        transport.bind(configuration.joinPort, maximumPeers = MAXIMUM_JOINER_PEERS)
        localProfile = profile
        online?.refreshListenerEndpoints()
        val entry = reach(master) ?: throw JoinFailure.Unreachable(master.endpoints.firstOrNull()?.address ?: "?", master.endpoints.firstOrNull()?.port ?: 0)
        val (link, challenge) = requestAdmission(entry, profile)

        Network.setState(NetworkState.Joining(JoinStep.AUTHENTICATING))
        val proof = if (challenge.passwordRequired) PasswordChallenge.proofFor(password.orEmpty(), challenge.salt, challenge.nonce) else ByteArray(0)
        link.sendMessage(JoinProof(proof))
        val admitted = mailbox.await<Admitted>(link, MessageType.ADMITTED, configuration.joinTimeoutMilliseconds)
        applyAdmission(link, admitted)

        online?.let { session -> session.secret = admitted.online?.secret ?: session.secret; session.start() }
        Network.setState(NetworkState.Joining(JoinStep.MESHING))
        meshWithMembers(admitted)
        replication.start()
        Network.setState(NetworkState.Joining(JoinStep.LOADING_LEVEL))
        levelService.followAdmittedLevel()
        Network.setState(NetworkState.Joining(JoinStep.SYNCHRONIZING))
        val missing = replication.snapshot.request(configuration.snapshotTimeoutMilliseconds)
        if (missing.isNotEmpty()) GD.printErr("Replication: no world snapshot from ${missing.map { it.value }}")
        publishSession()
        transport.runDeferred()
        Network.setState(NetworkState.Connected)
    } catch (failure: Exception) {
        abort()
        throw failure
    }
}

private suspend fun SessionRuntime.requestAdmission(entry: EnetLink, profile: PlayerProfile): Pair<EnetLink, Challenge> {
    var link = entry
    var candidates = listOf(Endpoint(entry.remoteAddress, entry.remotePort))
    var redirects = 0
    var retries = 0
    while (true) {
        val request = JoinRequest(PROTOCOL_VERSION, profile, transport.listeningHost?.port ?: 0, DirectStrategy.localAddresses(), online?.listenerEndpoints.orEmpty())
        link.sendMessage(request)
        val reply = try {
            mailbox.await(link, setOf(MessageType.CHALLENGE, MessageType.REDIRECT), configuration.joinTimeoutMilliseconds)
        } catch (rejected: JoinFailure.Rejected) {
            // The master closes the link with every rejection; a retry (admission frozen after a migration) re-dials.
            if (rejected.reason != RejectReason.RETRY || retries++ >= MAXIMUM_RETRIES) throw rejected
            transport.close(link, graceful = true)
            delay(RETRY_DELAY_MILLISECONDS)
            link = redial(candidates)
            continue
        }
        when (reply) {
            is Challenge -> return link to reply
            is Redirect -> {
                if (redirects++ >= 1) throw JoinFailure.Unreachable(link.remoteAddress, link.remotePort)
                transport.close(link, graceful = true)
                candidates = reply.master
                link = redial(candidates)
            }
            else -> error("Unexpected ${reply.type} while joining")
        }
    }
}

private suspend fun SessionRuntime.redial(candidates: List<Endpoint>): EnetLink =
    reach(ConnectionTarget(membership?.master ?: PlayerId.NONE, candidates))
        ?: throw JoinFailure.Unreachable(candidates.firstOrNull()?.address ?: "?", candidates.firstOrNull()?.port ?: 0)

/** Tries every configured strategy in order; the first link wins and remembers which strategy made it. */
internal suspend fun SessionRuntime.reach(target: ConnectionTarget): EnetLink? {
    for (strategy in strategies) {
        val link = strategy.connect(target, this, configuration.timeoutFor(strategy)) ?: continue
        link.strategy = strategy.name
        TransportLog.log { "reached ${target.member.value} through ${strategy.name}: $link" }
        return link
    }
    return null
}

private fun SessionRuntime.applyAdmission(link: EnetLink, admitted: Admitted) {
    localPlayerId = admitted.playerId
    membership = Membership(admitted.sessionId, admitted.epoch, admitted.members.associateBy { member -> member.id }, admitted.nextJoinSequence)
    lobbyConfiguration = admitted.lobby
    levelState = admitted.level
    passwordVerifier = admitted.password
    val master = checkNotNull(membership).master
    transport.identify(link, master)
    installMeshPeer()
    meshPeer.announcePeer(master)
}

private suspend fun SessionRuntime.meshWithMembers(admitted: Admitted) {
    val master = checkNotNull(membership).master
    val others = admitted.members.filter { member -> member.id != localPlayerId && member.id != master }
    val missing = coroutineScope {
        others.map { member -> async { if (connectMember(member)) null else member.id } }.awaitAll().filterNotNull()
    }
    if (missing.isNotEmpty()) throw JoinFailure.MeshIncomplete(missing.toSet())
}

private suspend fun SessionRuntime.connectMember(member: MemberRecord): Boolean {
    val membership = membership ?: return false
    val link = reach(ConnectionTarget(member.id, member.endpoints)) ?: return false
    link.sendMessage(Hello(membership.sessionId, localPlayerId, membership.epoch))
    mailbox.await<HelloAccepted>(link, MessageType.HELLO_ACCEPTED, configuration.meshTimeoutMilliseconds)
    transport.identify(link, member.id)
    meshPeer.announcePeer(member.id)
    publishSession()
    return true
}

internal suspend fun SessionRuntime.leave(reason: LeaveReason) {
    if (Network.state.value == NetworkState.Offline) return
    Network.setState(NetworkState.Leaving)
    discovery.stopResponder()
    online?.let { session ->
        session.stop()
        val secret = session.secret
        if (isMaster && secret != null && (membership?.members?.size ?: 0) <= 1) runCatching { session.client.remove(session.code, secret) }
    }
    if (membership != null) {
        transport.broadcastMessage(Leave(reason))
        transport.flush()
        for (link in transport.links.toList()) link.close(graceful = true)
        val deadline = nowMilliseconds() + GRACEFUL_LEAVE_MILLISECONDS
        while (transport.links.isNotEmpty() && nowMilliseconds() < deadline) {
            delay(LEAVE_POLL_MILLISECONDS)
            transport.pump()
        }

    }
    abort()
    emit(NetworkEvent.Disconnected(reason))
}

internal fun SessionRuntime.abort() {
    online?.stop()
    online = null
    mailbox.clear()
    levelService.unload()
    replication.stop()
    discovery.stopResponder()
    removeMeshPeer()
    transport.shutdown()
    transport.runDeferred()
    membership = null
    masterRole = null
    localPlayerId = PlayerId.NONE
    Network.updateSession(null)
    Network.updateLobby(LobbyState.EMPTY)
    Network.updateLevel(LevelState.NONE)
    Network.setState(NetworkState.Offline)
}

private const val MAXIMUM_JOINER_PEERS = 64
private const val MAXIMUM_RETRIES = 3
private const val RETRY_DELAY_MILLISECONDS = 1_000L
private const val GRACEFUL_LEAVE_MILLISECONDS = 1_000L
private const val LEAVE_POLL_MILLISECONDS = 16L
