# ch.hippmann.godot.replication

Peer to peer multiplayer for [Godot-JVM](https://github.com/utopia-rise/godot-jvm) (GDExtension binding, Kotlin).
No authoritative server: every member connects to every other member, the member with the lowest id is the
master, and a new master is elected automatically when the current one leaves. The library owns the transport,
the lobby, level coordination, node ownership and property replication; a game only provides its UI.

## What you get

- Hosting and joining with an optional password (salted challenge, never sent in clear), LAN discovery, kicks.
- A lobby every member sees: players, ready flags, profiles, a configuration map; any member can carry on as master.
- Level loading with `WaitForAll` (nobody starts before the slowest member loaded, with a straggler timeout) or
  `StartWhenLoaded`; late joiners load the current level and receive the world before they are connected.
- Networked nodes with an owner: `Network.spawn` replicates scene instances, `synced` delegates replicate
  properties (reliable or unreliable, on change or continuous, quantized, interpolated), ownership can be
  transferred or requested, and owner leave policies (`Despawn`, `TransferToMaster`, `TransferTo`) survive master migration.
- Godot's own `@Rpc` keeps working on top of the mesh, plus typed custom messages and RPC target helpers.
- Interest management by distance or custom filter, per second statistics, and a network simulation
  (latency, jitter, loss) for testing.
- One binary packet per peer per tick; Godot math types are pure Kotlin so replication costs no JNI per property.
  ENet's unreliable packet throttle is switched off on every link, so a stuttering frame never silently thins the state stream.

## Hello multiplayer

```kotlin
@Script
class Main : Node() {
    override fun _ready() {
        launch {
            if ("--host" in OS.getCmdlineUserArgs().toList()) {
                Network.host(LobbyConfiguration("Mara's arena", password = "wrench-42"), PlayerProfile("Mara"))
                Network.loadLevel("res://scenes/arena.tscn", LevelPolicy.WaitForAll())
            } else {
                Network.join("192.168.1.10", 7777, PlayerProfile("Tobias"), password = "wrench-42")
            }
        }
    }
}

@Script
class Arena : Node3D() {
    override fun _ready() {
        Network.spawn<Player>(GD.load("res://scenes/player.tscn")!!, parent = this, spawnData = Loadout("wrench", credits = 10))
    }
}

@Script
class Player : Node3D() {
    var health by synced(100)                                                    // reliable, on change
    var displayName by synced("")
    val positionSync = synced(::position) { unreliable(); continuous(rate = 30); interpolate() }
    val loadout by spawnData<Loadout>()                                          // any @Serializable type

    override fun _physicsProcess(delta: Double) {
        if (!Network.isOwner(this)) return
        position += Vector3(2.0 * delta, 0.0, 0.0)
    }
}
```

Every member runs `Arena._ready` after the level loads and spawns its own avatar; the library replicates the
avatar, its properties and its movement to everyone, including members that join later.

## Adding it to a project

```kotlin
repositories { mavenCentral() }
dependencies { implementation("ch.hippmann.godot:replication:0.1.0") }
```

The Godot-JVM registrar picks up the library's two script classes (`ReplicationManager`, `ReplicationMeshPeer`)
from the runtime classpath; nothing needs to be added to the Godot project by hand. The consumer applies the
kotlinx.serialization compiler plugin for its own `@Serializable` payloads.

Until a Godot-JVM release contains the binding fixes from `fix/multiplayer-binding-bugs`, the build runs against a
mavenLocal snapshot of that branch (see `gradle/libs.versions.toml`) and the sample copies the locally built addon
libraries from the binding checkout (`GODOT_JVM_ADDON_LIBRARIES`).

## API tour

| Area | Entry points |
|---|---|
| Session | `Network.host`, `join`, `leave`, `state`, `session`, `events`, `discoverLocalSessions`, `configure { port; tickRate; ... }` |
| Lobby | `Network.lobby`, `setReady`, `updateProfile`, `updateLobby` (master), `requestLobbyUpdate`, `kick` |
| Levels | `Network.loadLevel(path, policy)`, `level`, `levelNode`, `NetworkConfiguration.levelPreparation` |
| Nodes | `Network.spawn`, `despawn`, `ownerOf`, `isOwner`, `transferOwnership`, `requestOwnership`, `SpawnOptions`, `NetworkConfigured` |
| Properties | `synced(initial) { reliable(); onChange(); continuous(rate); quantize(...); interpolate(); interest(...) }`, `synced(::engineProperty)`, `spawnData<T>()` |
| Messages | `Network.send(payload, Target.All)`, `Network.messages<T>()`, `Node.rpcMaster`, `rpcOwner`, `rpcTo` |
| Diagnostics | `Network.statistics`, `Network.simulation = NetworkSimulation(latencyMilliseconds = 100, lossPercent = 5.0)`, `NetworkConfiguration.verboseTransportLogging` |

Godot authority mirrors ownership: a networked node's multiplayer authority is its owner, the level and the tree
root belong to the master, so `@Rpc(rpcMode = AUTHORITY)` keeps its meaning.

## Repository layout

- `replication-core`: pure Kotlin, no Godot dependency: binary codec, wire protocol, membership and election
  logic, delta packets, interpolation, simulation. Unit tested with JUnit.
- `replication`: the Godot-JVM library (transport over ENet, session flows, replication engine, public API).
- `sample`: a Godot 4.7 project you can play by hand (lobby, two levels, movement, shooting, crates and doors) that also hosts the scripted end-to-end scenarios and a self driving UI tour.
- `end-to-end-tests`: JUnit tests that launch several headless Godot processes on localhost per scenario.

## Running the tests

```
./gradlew build                     # unit tests plus every end-to-end scenario
./gradlew :end-to-end-tests:test --tests '*Level*' -Dverbose.transport=true
```

`GODOT_EDITOR` points at the Godot editor binary (default `godot` on the path). Each scenario's process output
lands in `end-to-end-tests/build/end-to-end-logs/<scenario>/<player>.log`. The sample is meant to be played by
hand: `godot --path sample` twice, host in one window and join in the other, press Ready, then the master starts a
level. WASD moves, click or space shoots, E pushes a crate or opens a door (taking it over first), Escape leaves.
`godot --path sample -- --tour=host --screenshot-dir=/tmp/shots` (and `--tour=join` in a second instance) drives
the same screens through real mouse and keyboard events and saves a screenshot of every step; `--tour=late` joins
the running level later and `--tour=discover` finds and joins the promoted master after the original host left.

## Measured on one machine (2026-09-08)

The `PerformanceScenariosTest` suite prints these on every run and fails when they degrade badly:

| What | Value |
|---|---|
| Reliable property change, owner to peer | 6 ms median, 7 ms p90 |
| Unreliable stream value, owner to peer | 6 ms median, 7 ms p90 |
| Interpolated position, shown behind the owner | 74 to 78 ms median at 30 ticks, 49 ms at 60 ticks (two ticks of delay plus sending; the delay grows on its own for gappy streams) |
| 150 nodes moving at 30 Hz, per receiving peer | 90 packets and 85 KB per second, about 50 ms of main thread time per second |
| Same, on the owner sending to two peers | 180 packets and 170 KB per second, about 28 ms per second |

## Protocol notes

Player ids are the join sequence allocated by the master, starting at 2, and double as Godot peer ids, so Godot
never sees peer 1 and never enters its client server semantics. Every member holds one listening ENet host and
one outbound ENet connection per other member; control messages travel reliably on channel 0, state deltas
unreliably sequenced on channel 1, Godot RPC packets on channel 2 and up. The wire format is documented in
`replication-core` (`MessageType`, `DeltaWriter`, `ReplicationBinary`).
