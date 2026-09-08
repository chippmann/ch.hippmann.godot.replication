package ch.hippmann.godot.replication.sync

import ch.hippmann.godot.replication.OwnerLeavePolicy
import ch.hippmann.godot.replication.OwnershipPolicy
import ch.hippmann.godot.replication.core.codec.ByteReader
import ch.hippmann.godot.replication.core.codec.ByteWriter
import ch.hippmann.godot.replication.core.replication.ClassSchema
import ch.hippmann.godot.replication.core.replication.PropertySchema
import ch.hippmann.godot.replication.core.session.NetworkId
import ch.hippmann.godot.replication.core.session.PlayerId
import ch.hippmann.godot.replication.core.wire.ParentReference
import godot.api.Node

internal class SpawnRecord(
    val scenePath: String,
    val parent: ParentReference,
    val spawnData: ByteArray,
)

/** Everything the engine knows about one networked node; created by the first delegate, activated on tree entry. */
class Replica internal constructor(val node: Node) {
    internal val properties = ArrayList<ReplicatedProperty<*>>()
    internal val spawnDataProperties = ArrayList<SpawnDataProperty<*>>()
    internal var schema: ClassSchema? = null
    internal var networkId: NetworkId? = null
    internal var spawnRecord: SpawnRecord? = null
    internal var ownerLeavePolicy: OwnerLeavePolicy = OwnerLeavePolicy.Despawn
    internal var ownershipPolicy: OwnershipPolicy = OwnershipPolicy.Transferable
    internal var onOwnershipRequest: (PlayerId) -> Boolean = { true }
    internal var interestFilter: InterestFilter = InterestFilter.Always
    internal var dirtyMask: Long = 0
    internal var active: Boolean = false
    internal var despawning: Boolean = false
    internal var interpolationDelayMilliseconds: Long = 100
    internal var maximumExtrapolationMilliseconds: Long = 100

    var owner: PlayerId = PlayerId.NONE
        internal set

    val isOwnedLocally: Boolean
        get() = active && owner == NodeRegistry.localPlayerId

    @PublishedApi internal fun register(property: ReplicatedProperty<*>) {
        check(schema == null) { "Synced properties must be declared as members, not added after the node entered the tree" }
        property.replica = this
        property.index = properties.size
        properties += property
    }

    internal fun freezeSchema(): ClassSchema = schema ?: ClassSchema(
        className = node::class.qualifiedName ?: node::class.java.name,
        properties = properties.map { property ->
            PropertySchema(property.index, property.name, property.codec.codecId, property.options.reliable, property.options.mode, property.options.interpolated)
        },
    ).also { schema = it }

    internal fun markDirty(property: ReplicatedProperty<*>) {
        dirtyMask = dirtyMask or (1L shl property.index)
        property.lastChangedTick = NodeRegistry.currentTick
    }

    internal fun writeValues(writer: ByteWriter, mask: Long) {
        for (property in properties) {
            if (mask and (1L shl property.index) != 0L) property.write(writer)
        }
    }

    internal fun readValues(reader: ByteReader, mask: Long, receiveTimeMilliseconds: Long, applyEngineBindings: Boolean = true) {
        for (property in properties) {
            if (mask and (1L shl property.index) == 0L) continue
            applyRead(property, reader, receiveTimeMilliseconds, applyEngineBindings)
        }
    }

    private fun <T> applyRead(property: ReplicatedProperty<T>, reader: ByteReader, receiveTimeMilliseconds: Long, applyEngineBindings: Boolean) {
        val value = property.read(reader)
        if (property is EnginePropertyBinding && !applyEngineBindings) {
            deferredEngineValues += Pair(property, value)
        } else {
            property.applyRemote(value, receiveTimeMilliseconds)
        }
    }

    internal val deferredEngineValues = ArrayList<Pair<ReplicatedProperty<*>, Any?>>()

    internal fun applyDeferredEngineValues(receiveTimeMilliseconds: Long) {
        for ((property, value) in deferredEngineValues) {
            @Suppress("UNCHECKED_CAST")
            (property as ReplicatedProperty<Any?>).applyRemote(value, receiveTimeMilliseconds)
        }
        deferredEngineValues.clear()
    }

    override fun toString(): String = "Replica(${node::class.simpleName}, networkId=${networkId?.value}, owner=${owner.value})"
}
