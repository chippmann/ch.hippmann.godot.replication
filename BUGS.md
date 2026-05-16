# Bugs & TODOs

Known issues in `replication/` and the integration-test harness. Severity is impact
× likelihood-in-real-use. Status tracks whether an `replication-integration-tests` scenario covers it.

---

## High severity

### #1 `syncChannel` is unreachable from the `SyncConfigs` DSL — **FIXED**

`PropertyConfig` now has `var syncChannel = SyncConfig.SyncChannel.CHANNEL_0` and the
DSL construction at SyncConfig.kt:40 passes it through to the produced `SyncConfig`.

**Test status:** `SyncConfigDslTest` — single-peer scenario constructs four DSL
configs (RELIABLE/CHANNEL_0, UNRELIABLE/CHANNEL_0, UNRELIABLE_ORDERED/CHANNEL_3,
UNRELIABLE_ORDERED/CHANNEL_9) via a probe property on TestRunner and asserts the
produced `SyncConfig.syncMethod` and `SyncConfig.syncChannel` match the input. Green.

---

### #2 `sendQueue` / `receiveQueue` in `Synchronizer` are not thread-safe — **FIXED**

`Synchronizer.kt:45-46` switched to `ConcurrentLinkedQueue<() -> Unit>` for both
queues. `FrameSyncTest` exercises this path under per-tick pressure but does not
specifically stress for races; deterministic reproduction is hard without a
dedicated JVM-side stress test.

---

### #3 No authority verification on `Synchronized` sync RPCs — **FIXED**

All `replicateForSynchronized*` are still `@Rpc(rpcMode = RpcMode.ANY)` because
godot-kotlin-jvm requires this for cross-peer RPC, but `Synchronizer.replicate(...)`
now verifies `multiplayer.getRemoteSenderId()` matches `node.getMultiplayerAuthority()`
synchronously inside the @Rpc handler (before the receive queue is enqueued — the
sender ID is only valid while the RPC is being dispatched). Forged calls from
non-authority peers log a warning and are dropped.

**Test status:** `ForgedSyncTest` — 1 server + 2 clients; each client legitimately
receives `x=42` from the server, then sends an unsolicited reliable RPC to the
other client with `x=999`. Recipient's local `customPosition.x` must remain 42.
Green.

---

### #4 `WithRemoteListeners` ready-handshake is fire-once and timing-fragile — **FIXED**

`RemoteListenerManager.notificationOnReadyForWithRemoteListeners` used to call
`notifyReady` exactly once on tree-entry. If multiplayer wasn't connected at that
moment (the common case for scene-loaded Replicators), the broadcast went nowhere
and any peer joining later silently never learned the node existed.

**Fix:** connect to `multiplayer.peerConnected` in `initListening` and re-emit
`remoteReady` to each newly-connected peer specifically (via `rpcId`). Also added a
dedup check in `authorityOnPeerSubscribeForWithRemoteListeners` since the now-
symmetric handshake can fire twice for the same peer pair (each side independently
re-emits on its peer_connected).

**Test status:** covered by `SpawnTest` (Replicator already in the loaded scene
before any multiplayer setup) and `LateJoinTest` (server spawns a managed child
before any client connects; late client must observe via snapshot). Both green.

---

### #5 `RemoteListenerReadyRedirector.listeners` map leaks — **FIXED**

`notificationOnExitingTreeForWithRemoteListeners` now unconditionally calls
`deregister()` so the autoload's static `listeners` map drops its entry when the
node leaves the tree. Added a public `listenerCount` accessor on the autoload's
companion for diagnostic/test access.

**Test status:** `ListenerLeakTest` — single peer, no multiplayer; adds 5
Replicators dynamically, observes `listenerCount` reach baseline+5, queueFrees
them, observes return to baseline. Green.

---

### #6 `Replicator.managedScenes` setter accumulates — **FIXED**

The setter now calls `_managedScenes.clear()` before re-populating from the new
value, so reassigning to a smaller/different list drops the previous entries.

**Test status:** `ManagedScenesReassignmentTest` — scene declares both `cube` and
`cube_alt` (so the client can instantiate either); server reassigns to
`[cube_alt]` only, then addChilds both a stale cube instance and a fresh
cube_alt instance. The client must observe ONLY the cube_alt — confirms the
old-scene path was removed from the server's filter.

Documentation gotcha exposed by the test: client-side `managedScenes` is what
the spawn RPC handler uses to instantiate; reassigning the server's
`managedScenes` is purely a server-side filter on outgoing spawn RPCs.

---

## Medium severity

### #7 Authority changes are silently broken

The README documents it but nothing enforces it. `RemoteListenerManager.listeningPeers`
is captured at init; if `setMultiplayerAuthority(newId)` is called after init, the
authority/peer roles flip but subscriptions don't migrate.

**Fix:** either listen for `Node.multiplayerAuthorityChanged` and re-handshake, or
fail-fast in `initListening` if anything tries to change authority later.

---

### #8 Ticker drift in `Synchronizer`

`Synchronizer.kt:84-121` is `while (isActive) { enqueue; delay(tick) }`. Effective
period is `tick + enqueue time + scheduling jitter`. At small ticks (16ms) this drifts.
The commented-out `ticker(...)` block on lines 122-140 was closer to the right design.

**Fix:** either commit to `delay` and accept drift (document it), or use a
timestamp-corrected loop.

---

### #9 `peerSpawnAllForReplicated` doesn't reconcile

`Replicator.kt:82-90` instantiates everything from the authority's snapshot but never
removes locally-spawned children that no longer exist on the authority. If a peer ever
holds stale state (edge cases on re-subscribe), they'll have ghosts plus the new
snapshot side-by-side.

**Fix:** clear managed children before applying the snapshot, or diff and reconcile.

---

### #10 `Replicator.spawnNode` doesn't guard duplicate names — **FIXED**

`spawnNode` now early-returns if `getNodeOrNull(spawnNodeData.nodeName) != null` —
so if the spawn-on-add and spawn-all-on-subscribe RPCs both fire for the same
managed child (race on a fast late-joiner), the second handler is a no-op rather
than silently letting Godot rename the duplicate.

---

### #11 RpcMode defaults are inconsistent / implicit — **FIXED**

`Replicated.kt` now uses `@Rpc(rpcMode = RpcMode.AUTHORITY)` explicitly on all
three spawn/despawn entry points. Other interfaces were already explicit.

---

## Small / cosmetic

### #12 `StringNameSerializer` descriptor element is named `"nodePath"` — **FIXED**

Renamed to `"stringName"`.

---

### #13 Dead commented-out code — **FIXED**

`Synchronizer.kt:122-140` and `RemoteListenerManager.kt:59-67` deleted.

---

### #14 `Synchronizer.cancel()` is over-broad on missing node

`Synchronizer.kt:87-90` calls `this.cancel()` (resolves to the `Synchronizer`
`CoroutineScope`) when `thisNode.get()` returns null. This kills **all** tick groups,
not just the one whose lambda observed the missing node. Likely intentional but worth
a one-line comment so a reader doesn't read it the other way.

---

### #15 Serializer instantiated per call — **FIXED**

Hoisted all 18 KSerializer instances to `@PublishedApi internal val`s at the top
of `serializer/serializer.kt`. The inline `serialize`/`deserialize` bodies now
reference shared instances instead of allocating a fresh one per tick.

---

### #16 `SimpleReplicator` / `SimpleSynchronizer` are non-functional stubs

`replication/src/main/kotlin/.../impl/SimpleReplicator.kt` and `SimpleSynchronizer.kt`
declare the delegation but never override `_enterTree` to call
`initReplication()` / `initSynchronization()`. The delegate never wires up its signal
connections. Users following the type system (instantiate `SimpleReplicator`, add to
tree) silently get a non-functional node.

**Fix:** either add the `_enterTree` override inside the `Simple*` classes, or remove
them entirely so consumers are forced to follow the README pattern. If kept, document
clearly that they're for `Node` only and a custom subclass is needed for
`Node2D`/`Node3D`/`CharacterBody3D`/etc.

**Test status:** discovered while writing `replication-integration-tests/SpawnScenario`; the scenario
now uses a hand-rolled `IntegrationTestReplicator`.

---

### #17 `SyncMethod.UNRELIABLE` ignores channels

Only `UNRELIABLE_ORDERED` exposes a channel in `Synchronizer.kt:101-112`. `UNRELIABLE`
and `RELIABLE` always go on channel 0. Godot supports channels on all transfer modes.
Either parametrize, or document the limitation.

---

### #18 `peerDespawnForReplicated` allocates unnecessarily — **partial**

Changed `getNodeAs<Node>(name.toString())` to `getNodeOrNull(name.toString())` —
drops the unchecked-cast wrapper but the `name.toString()` is still required
because `NodePath` has no `StringName`-direct constructor in this godot-kotlin-jvm
version. Worth revisiting if a constructor is added upstream.
