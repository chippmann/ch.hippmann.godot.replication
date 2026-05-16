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

### #7 Authority changes are silently broken — **godot-API limitation**

`RemoteListenerManager.listeningPeers` is populated during the WithRemoteListeners
handshake. If a consumer calls `setMultiplayerAuthority(newId)` after the handshake
completes, the authority/peer roles flip but the existing subscriptions don't
migrate — the new authority's `listeningPeers` is empty; old peers think they're
still subscribed to the old authority.

**Root cause is Godot-side:** there's no `multiplayerAuthorityChanged` signal on
`Node` to react to. The library would have to either (a) poll
`getMultiplayerAuthority()` every frame (cost without clear gain), or
(b) require consumers to call an explicit `refreshAuthorityState()` after every
`setMultiplayerAuthority`, which is just shifting the silent-break to "forgot
the post-call" rather than "didn't know about the constraint".

**Recommendation for now:** README continues to state authority must be fixed
before tree entry. If a real use case for mutable authority shows up, add the
explicit `refreshAuthorityState()` API surface.

---

### #8 Ticker drift in `Synchronizer`

`Synchronizer.kt:84-121` is `while (isActive) { enqueue; delay(tick) }`. Effective
period is `tick + enqueue time + scheduling jitter`. At small ticks (16ms) this drifts.
The commented-out `ticker(...)` block on lines 122-140 was closer to the right design.

**Fix:** either commit to `delay` and accept drift (document it), or use a
timestamp-corrected loop.

---

### #9 `peerSpawnAllForReplicated` doesn't reconcile — **FIXED**

`Replicator.peerSpawnAllForReplicated` now diffs the incoming snapshot against
locally-spawned managed children: any local managed child whose name isn't in
the snapshot is `queueFree`d before the snapshot is applied. Combined with the
duplicate-name guard (#10), re-subscription is idempotent.

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

### #19 Synchronizer per-property `shouldSendUpdate` dedup is global, not per-peer — **FIXED**

Synchronizer now passes an `onPeerSubscribed` callback through `initListening`.
When a new peer's WithRemoteListeners handshake completes, the authority
immediately `rpcId`s the current value of every synced property to that peer
specifically (`sendFullStateTo`). Mirrors what `Replicator.onPeerSubscribe`
already did for managed children's spawn-all snapshot.

`shouldSendUpdate`'s global dedup is preserved — it still cuts redundant
periodic ticks — but late subscribers now always get a one-shot catch-up,
so a stable value is no longer permanently missed.

**Test status:** `MultipleSyncedPropertiesScenario` had a workaround wait
`pollUntil { listeningPeers.size == expectedClientCount }`; that wait has
been removed and the test still passes, proving the catch-up fires.

---

### #20 Godot/JVM aborts (SIGABRT) on process exit when Synchronizer tickers are active

Observed during test teardown: SIGABRT inside a `jni_CallVoidMethodA` →
`SafepointSynchronize::block`. The JVM requests a safepoint (typically for
GC or stop-the-world during shutdown) but a thread on `Dispatchers.Default`
is still mid-JNI call (the ticker coroutine touching `thisNode.get()` and
godot bindings) and can't reach safepoint, so the wait never resolves and
the JVM aborts. On macOS the OS surfaces the resulting crash via a
"Godot quit unexpectedly" dialog from `ReportCrash`.

The crash is asynchronous to test outcomes — tests can pass cleanly and
still leave a crash report behind. It does not affect test correctness but
is noisy in development.

**Fix idea:** move the Synchronizer ticker off `Dispatchers.Default` onto a
Godot-main-thread scheduler (e.g., a `_process` frame counter rather than
`delay(tick)`), eliminating the cross-thread JNI hazard at shutdown.

**Workaround for development:**
```sh
defaults write com.apple.CrashReporter DialogType none
killall ReportCrash
```
suppresses the dialog system-wide. Crashes still go to
`~/Library/Logs/DiagnosticReports/` for forensic inspection.

---

## Small / cosmetic

### #12 `StringNameSerializer` descriptor element is named `"nodePath"` — **FIXED**

Renamed to `"stringName"`.

---

### #13 Dead commented-out code — **FIXED**

`Synchronizer.kt:122-140` and `RemoteListenerManager.kt:59-67` deleted.

---

### #14 `Synchronizer.cancel()` is over-broad on missing node — **commented**

Added a comment explaining `this.cancel()` resolves to the Synchronizer's whole
CoroutineScope (cancelling every tick group), and that this is intentional once
the host node is gone — there's nothing left to sync.

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

### #17 `SyncMethod.UNRELIABLE` ignores channels — **wont-fix-by-design**

Only `UNRELIABLE_ORDERED` exposes a channel in `Synchronizer.kt`; `UNRELIABLE` and
`RELIABLE` always go on channel 0. Godot *does* support channels on all transfer
modes, but exposing it through this library would mean another 20 channel-specific
`@Rpc`-annotated methods on `Synchronized` (10 each for RELIABLE/UNRELIABLE)
because godot-kotlin-jvm requires the channel as an annotation constant. Godot's
default ENet channel count is 1; non-ordered channeled transfers are an unusual
use case. Marked as design choice; revisit if a real user pattern needs it.

---

### #18 `peerDespawnForReplicated` allocates unnecessarily — **partial**

Changed `getNodeAs<Node>(name.toString())` to `getNodeOrNull(name.toString())` —
drops the unchecked-cast wrapper but the `name.toString()` is still required
because `NodePath` has no `StringName`-direct constructor in this godot-kotlin-jvm
version. Worth revisiting if a constructor is added upstream.
