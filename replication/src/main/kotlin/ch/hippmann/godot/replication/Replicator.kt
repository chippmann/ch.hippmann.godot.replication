package ch.hippmann.godot.replication

import ch.hippmann.godot.replication.serializer.deserialize
import ch.hippmann.godot.replication.serializer.serialize
import ch.hippmann.godot.utilities.logging.Log
import godot.api.Node
import godot.api.PackedScene
import godot.core.StringName
import godot.core.VariantArray
import godot.extension.getNodeAs
import kotlinx.serialization.Serializable

class Replicator : Replicated, WithRemoteListeners by RemoteListenerManager(),
    WithNodeAccess by WithNodeAccessDelegate() {
    private val _managedScenes: MutableMap<String, PackedScene> = mutableMapOf()
    override var managedScenes: VariantArray<PackedScene> = VariantArray()
        set(value) {
            field = value
            // Replace the resolved-by-resource-path lookup wholesale; previously this
            // setter only added entries, so reassigning the property to a different
            // (or smaller) list left stale entries behind and any addChild whose
            // sceneFilePath matched the old list would still replicate.
            _managedScenes.clear()
            value.forEach { packedScene ->
                _managedScenes[packedScene.resourcePath] = packedScene
            }
        }

    override fun <T> T.initReplication() where T : Node, T : Replicated {
        initNodeAccess()
        initListening(onPeerSubscribed = ::onPeerSubscribe)

        childEnteredTree.connect(this, Replicated::notificationOnChildEnteredTreeForReplicated)
        childExitingTree.connect(this, Replicated::notificationOnChildExitingTreeForReplicated)
        Log.debug { "Replicator[${this.name}]: initialised" }
    }

    override fun notificationOnChildEnteredTreeForReplicated(child: Node) {
        ifAuthority {
            Log.debug { "Replicator[${this.name}]: child added: ${child.name}" }
            provideManagedSceneFromNode(child)?.let { managedScene ->
                Log.debug { "Replicator[${this.name}]: child has associated managed scene: ${managedScene.resourcePath}" }

                withRemoteListeners { peerId ->
                    val spawnData = SpawnNodeData(
                        nodeName = child.name.toString(),
                        authority = child.getMultiplayerAuthority().toLong(),
                        packedScenePath = managedScene.resourcePath,
                        spawnData = (child as? Synchronized)?.syncConfig?.serializeSpawnData()
                    )

                    Log.debug { "Replicator[${this.name}]: sending spawnData: $spawnData to peer with id: $peerId" }
                    rpcId(
                        peerId,
                        thisNodeAsType<Replicated>()::peerSpawnForReplicated,
                        spawnData.serialize()
                    )
                }
            }
        }
    }

    override fun notificationOnChildExitingTreeForReplicated(child: Node) {
        ifAuthority {
            Log.debug { "Replicator[${this.name}]: child left tree: ${child.name}" }
            provideManagedSceneFromNode(child)?.let { managedScene ->
                Log.debug { "Replicator[${this.name}]: child has associated managed scene: ${managedScene.resourcePath}" }
                withRemoteListeners { peerId ->
                    Log.debug { "Replicator[${this.name}]: sending despawn request for child: ${child.name} to peer with id: $peerId" }
                    rpcId(
                        peerId,
                        thisNodeAsType<Replicated>()::peerDespawnForReplicated,
                        child.name
                    )
                }
            }
        }
    }

    override fun peerSpawnForReplicated(spawnNodeData: SerializedData) {
        ifPeer {
            val deserializedSpawnNodeData = spawnNodeData.deserialize<SpawnNodeData>()
            spawnNode(deserializedSpawnNodeData)
        }
    }

    override fun peerSpawnAllForReplicated(spawnNodesData: SerializedData) {
        ifPeer {
            spawnNodesData
                .deserialize<List<SpawnNodeData>>()
                .forEach { spawnNodeData ->
                    spawnNode(spawnNodeData)
                }
        }
    }

    override fun peerDespawnForReplicated(name: StringName) {
        ifPeer {
            Log.debug { "Replicator[${this.name}]: received despawn request for node with name: $name" }
            getNodeOrNull(name.toString())?.queueFree()
        }
    }

    private fun Node.spawnNode(spawnNodeData: SpawnNodeData) {
        Log.debug { "Replicator[${this.name}]: received spawn request with spawnData: $spawnNodeData" }

        // Skip if a child with this name already exists. Without this, a race between
        // the spawn-on-add RPC and the spawn-all-on-subscribe snapshot — both legitimate
        // — would cause the same managed scene to be instantiated twice, and Godot would
        // silently rename the duplicate, breaking later despawn-by-name lookups.
        if (getNodeOrNull(spawnNodeData.nodeName) != null) {
            Log.debug { "Replicator[${this.name}]: already have a child named '${spawnNodeData.nodeName}', skipping duplicate spawn" }
            return
        }

        _managedScenes[spawnNodeData.packedScenePath]
            ?.instantiate()
            ?.let { node ->
                node.setName(spawnNodeData.nodeName)
                node.setMultiplayerAuthority(spawnNodeData.authority.toInt())
                addChild(node)
                spawnNodeData.spawnData?.let { (node as? Synchronized)?.syncConfig?.applySpawnData(it) }
            }
    }

    private fun onPeerSubscribe(peerId: Long) {
        ifAuthority {
            val spawnNodesData = getChildren()
                .mapNotNull { node ->
                    provideManagedSceneFromNode(node)?.let { packedScene ->
                        SpawnNodeData(
                            nodeName = node.name.toString(),
                            authority = node.getMultiplayerAuthority().toLong(),
                            packedScenePath = packedScene.resourcePath,
                            spawnData = (node as? Synchronized)?.syncConfig?.serializeSpawnData()
                        )
                    }
                }

            Log.debug { "Replicator[${this.name}]: peer with id: $peerId subscribed. Sending initial spawnData: $spawnNodesData" }

            rpcId(
                peerId,
                thisNodeAsType<Replicated>()::peerSpawnAllForReplicated,
                spawnNodesData.serialize()
            )
        }
    }

    private fun provideManagedSceneFromNode(node: Node): PackedScene? {
        return node
            .sceneFilePath
            .let { scenePath ->
                if (scenePath.isNotEmpty()) {
                    _managedScenes[scenePath]
                } else null
            }
    }
}

@Serializable
private class SpawnNodeData(
    val nodeName: String,
    val authority: Long,
    val packedScenePath: String,
    val spawnData: SerializedData?
) {
    override fun toString(): String {
        return """
            SpawnNodeData(
                nodeName: $nodeName,
                authority: $authority,
                packedScenePath: $packedScenePath,
                spawnData: $spawnData
            )
        """.trimIndent()
    }
}
