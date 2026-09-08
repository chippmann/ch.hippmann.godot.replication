package ch.hippmann.godot.replication.core.replication

import ch.hippmann.godot.replication.core.session.PlayerId
import ch.hippmann.godot.replication.core.wire.OwnerLeavePolicyId
import ch.hippmann.godot.replication.core.wire.OwnershipPolicyId
import ch.hippmann.godot.replication.core.wire.ParentReference
import ch.hippmann.godot.replication.core.session.NetworkId
import ch.hippmann.godot.replication.core.wire.Spawn
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class SchemaTest {
    private fun schema(vararg names: String) = ClassSchema("Player", names.mapIndexed { index, name -> PropertySchema(index, name, "int", reliable = index % 2 == 0, SyncMode.OnChange, interpolated = false) })

    @Test
    fun `the hash changes with any property detail`() {
        val base = schema("health", "name")
        assertEquals(base.hash, schema("health", "name").hash)
        assertNotEquals(base.hash, schema("name", "health").hash)
        assertNotEquals(base.hash, ClassSchema("Enemy", base.properties).hash)
        assertNotEquals(base.hash, ClassSchema("Player", listOf(base.properties[0].copy(reliable = false), base.properties[1])).hash)
    }

    @Test
    fun `masks follow the declaration order`() {
        val base = schema("health", "name", "score")
        assertEquals(0b111L, base.fullMask)
        assertEquals(0b101L, base.reliableMask)
        assertEquals(0b100L, base.properties[2].bit)
    }

    @Test
    fun `spawn messages round trip`() {
        val spawn = Spawn(
            networkId = NetworkId.spawned(PlayerId(3), 12),
            scenePath = "res://scenes/player.tscn",
            parent = ParentReference.Path("/root/Main/Arena"),
            name = "Player_3_12",
            owner = PlayerId(3),
            ownerLeavePolicy = OwnerLeavePolicyId.TRANSFER_TO_MASTER,
            ownershipPolicy = OwnershipPolicyId.REQUEST_REQUIRED,
            schemaHash = -12345,
            spawnData = byteArrayOf(1, 2, 3),
            initialState = byteArrayOf(9, 8),
        )
        val decoded = Spawn.decode(spawn.encode())
        assertEquals(spawn.networkId, decoded.networkId)
        assertEquals(spawn.parent, decoded.parent)
        assertEquals(spawn.name, decoded.name)
        assertEquals(spawn.ownerLeavePolicy, decoded.ownerLeavePolicy)
        assertEquals(spawn.schemaHash, decoded.schemaHash)
        assertContentEquals(spawn.spawnData, decoded.spawnData)
        assertContentEquals(spawn.initialState, decoded.initialState)
        val networkedParent = Spawn.decode(Spawn(spawn.networkId, "", ParentReference.Networked(NetworkId.scenePlaced(5)), "", PlayerId(2), OwnerLeavePolicyId.DESPAWN, OwnershipPolicyId.FIXED, 0, ByteArray(0), ByteArray(0)).encode())
        assertEquals(ParentReference.Networked(NetworkId.scenePlaced(5)), networkedParent.parent)
    }
}
