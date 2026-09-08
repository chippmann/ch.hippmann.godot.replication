package ch.hippmann.godot.replication.core.wire

import ch.hippmann.godot.replication.core.codec.CodecException
import ch.hippmann.godot.replication.core.level.LevelPolicy
import ch.hippmann.godot.replication.core.level.LevelState
import ch.hippmann.godot.replication.core.lobby.LobbyConfiguration
import ch.hippmann.godot.replication.core.lobby.PlayerProfile
import ch.hippmann.godot.replication.core.session.Endpoint
import ch.hippmann.godot.replication.core.session.Epoch
import ch.hippmann.godot.replication.core.session.MemberRecord
import ch.hippmann.godot.replication.core.session.NetworkId
import ch.hippmann.godot.replication.core.session.PasswordVerifier
import ch.hippmann.godot.replication.core.session.PlayerId
import ch.hippmann.godot.replication.core.session.SessionId
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class MessageCodecTest {

    @Test
    fun `the first byte is the message type`() {
        val bytes = MessageCodec.encode(Leave(LeaveReason.GRACEFUL))
        assertEquals(MessageType.LEAVE.id, bytes[0].toInt())
        assertEquals(2, bytes.size)
    }

    @Test
    fun `admitted carries the whole session view`() {
        val admitted = Admitted(
            playerId = PlayerId(4),
            sessionId = SessionId(0x1234_5678_9ABC_DEF0L),
            epoch = Epoch(2),
            nextJoinSequence = 5,
            members = listOf(
                MemberRecord(PlayerId(2), PlayerProfile("Mara"), listOf(Endpoint("192.168.1.10", 7777)), ready = true),
                MemberRecord(PlayerId(3), PlayerProfile("Tobias", byteArrayOf(9)), listOf(Endpoint("192.168.1.11", 7777), Endpoint("10.0.0.5", 51000))),
            ),
            lobby = LobbyConfiguration("Mara's arena", maximumPlayers = 4, locked = false, settings = mapOf("mode" to "deathmatch")),
            level = LevelState(3, "res://scenes/arena.tscn", LevelPolicy.WaitForAll(15_000), started = true, loaded = setOf(PlayerId(2), PlayerId(3))),
            password = PasswordVerifier.forPassword("wrench-42"),
        )

        val decoded = MessageCodec.decode(MessageCodec.encode(admitted))
        assertEquals(admitted, decoded)
    }

    @Test
    fun `binary payloads survive`() {
        val challenge = Challenge(SessionId(7), ByteArray(16) { it.toByte() }, ByteArray(16) { (15 - it).toByte() }, passwordRequired = true)
        val decoded = assertIs<Challenge>(MessageCodec.decode(MessageCodec.encode(challenge)))
        assertContentEquals(challenge.salt, decoded.salt)
        assertContentEquals(challenge.nonce, decoded.nonce)
        assertEquals(true, decoded.passwordRequired)
    }

    @Test
    fun `lobby commands are polymorphic`() {
        val command = LobbyCommand(LobbyCommandPayload.LoadLevel("res://scenes/hangar.tscn", LevelPolicy.StartWhenLoaded))
        assertEquals(command, MessageCodec.decode(MessageCodec.encode(command)))
    }

    @Test
    fun `ownership entries keep their network ids`() {
        val message = OwnershipChanged(Epoch(3), listOf(OwnershipEntry(NetworkId.spawned(PlayerId(3), 17), PlayerId(2)), OwnershipEntry(NetworkId.scenePlaced(-5), PlayerId(4))))
        val decoded = assertIs<OwnershipChanged>(MessageCodec.decode(MessageCodec.encode(message)))
        assertEquals(message, decoded)
        assertEquals(PlayerId(3), decoded.entries[0].networkId.ownerPart)
        assertEquals(17, decoded.entries[0].networkId.counter)
        assertEquals(true, decoded.entries[1].networkId.isScenePlaced)
    }

    @Test
    fun `unknown and non control types are rejected`() {
        assertFailsWith<CodecException> { MessageCodec.decode(byteArrayOf(0x7F)) }
        assertFailsWith<CodecException> { MessageCodec.decode(byteArrayOf(MessageType.STATE_DELTA.id.toByte())) }
    }
}
