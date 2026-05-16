# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A Kotlin library that adds basic multiplayer node replication and property synchronization on top of [godot-kotlin-jvm](https://github.com/utopia-rise/godot-kotlin-jvm). Consumers wire it into their own godot-kotlin-jvm Godot project; this repo only produces the published artifact (no Godot project of its own). Published to Maven Central as `ch.hippmann.godot:replication`.

## Build / publish commands

- `./gradlew build` — compile, lint, test the `replication` module.
- `./gradlew :replication:publishToMavenLocal` — install the artifact locally (useful for testing against a consumer project via `mavenLocal()`).
- `./gradlew publish` — publish to Maven Central. Requires `mavenCentralUsername`, `mavenCentralPassword`, `signingInMemoryKey`, `signingInMemoryKeyPassword` (as Gradle props or env vars). Without these the `ch.hippmann.publish` convention plugin silently skips remote setup and only `publishToMavenLocal` will work.
- `./gradlew generateChangelog` — produces `build/changelog.md` from git tags via grgit (used by CI release flow).

Library version lives in `gradle/libs.versions.toml` under `godot-kotlin-jvm-replication`. Bump it there, not in `build.gradle.kts`.

## Architecture

The library is built around three layered capability interfaces, each paired with a delegate class so consumers compose them onto their `Node` subclasses via `by` delegation. The pattern is intentional and load-bearing — see "Delegation pattern" below.

### Capability layers (bottom-up)

1. **`WithRemoteListeners` / `RemoteListenerManager`** — tracks which remote peers care about this node. Peers subscribe to the authority on `_ready`; the authority maintains `listeningPeers` and invokes `onPeerSubscribed` / `onPeerUnsubscribed` callbacks. All higher layers iterate `withRemoteListeners { peerId -> rpcId(...) }` instead of broadcasting, so late-joining peers get backfilled rather than missing updates.

2. **`Replicated` / `Replicator`** — spawning/despawning. Holds an editor-exposed `managedScenes: VariantArray<PackedScene>`. When a child whose `sceneFilePath` matches a managed scene is added under the authority, an RPC fires to all peers to instantiate the same scene, set the multiplayer authority, and (if the root implements `Synchronized`) apply initial spawn data. On peer-subscribe, the authority replays all current children to the new peer.

3. **`Synchronized` / `Synchronizer`** — per-property syncing. Consumer declares a `syncConfig { property(::foo) { tick = ...; syncMethod = ... } }` DSL. The synchronizer groups properties by tick interval, launches a coroutine per tick group on the authority that enqueues serialized updates, and exposes `performSynchronization()` for the consumer to call from `_process` to flush both send and receive queues (RPCs are issued / applied on Godot's main thread that way). `shouldSendUpdate` is consulted per property to skip no-op updates.

### The peer-ready handshake

Naive `_ready` RPCs race: a peer's `_ready` can fire before the authority has registered its RPC handlers (or vice versa). To work around this, every `WithRemoteListeners` node routes its readiness through the `RemoteListenerReadyRedirector` autoload — peers ask the authority "are you ready?", the authority broadcasts a ready signal, and only then does subscription happen. **This is why the autoload is mandatory** (see README §Autoload); without it, `WithRemoteListeners.initListening` throws at runtime when looking up `/root/RemoteListenerReadyRedirector`.

### Delegation pattern (and why interfaces look heavy)

Consumers write `class Player : CharacterBody3D(), Synchronized by Synchronizer()`. The interfaces (`Replicated`, `Synchronized`, `WithRemoteListeners`) deliberately expose every `@RegisterFunction` / `@Rpc` method, and the delegate classes implement them. This is required because godot-kotlin-jvm scans the **concrete class** for registered functions — methods only present on a hidden delegate would not be registered, so RPCs would not dispatch. That's also why `Synchronized` has 12 separate `replicateForSynchronized*` methods: each transfer mode / channel combination needs its own `@Rpc`-annotated entry point (Godot binds channels at annotation time, not call time). When adding sync channels or transfer modes, you must add a method on both the interface and the delegate; there is no way to parametrize this at runtime.

Delegate classes use `WithNodeAccessDelegate` (a `WeakReference<Node>` stash) so they can call back into the host node. `initReplication()` / `initSynchronization()` / `initListening()` are extension functions with a `where T : Node, T : <Interface>` constraint — the consumer must call them from `_enterTree` (not `_ready`) so the weak ref is populated before any signals fire.

### Lifecycle constraints (easy to violate)

- `initReplication()` / `initSynchronization()` **must** run in `_enterTree` — the delegates wire signals (`childEnteredTree`, `ready`, `treeExiting`) at that point, and `_ready` is too late.
- The multiplayer authority of a `Synchronized` node **must** be set before it enters the tree. The current implementation does not handle authority changes after init.
- `syncConfig` is frozen at `initSynchronization()` — assigning to it afterwards has no effect on the precomputed `tickToConfigs` grouping.
- `syncOnSpawn` only takes effect when the `Synchronized` node is a **direct** child of `Replicated`. Deeper nesting falls back to first-tick sync.
- `performSynchronization()` must be called more often than the smallest configured `tick`, otherwise the send queue accumulates and bursts.

### Serialization

`serializer/serializer.kt` wraps `kotlinx.serialization` JSON and special-cases Godot core math types (`Vector2/3/4`, `Transform2D/3D`, `Basis`, `Quaternion`, `Color`, `AABB`, `Plane`, `Projection`, `Rect2/2i`, `NodePath`, `StringName`) because they lack `@Serializable` annotations upstream. When adding support for a new Godot core type, add both a `*Serializer` in `serializer/math/` (or `serializer/bridge/`) and a branch in both the `serialize()` and `deserialize()` `when` blocks — missing either side will silently fall through to the default `@Serializable` path and fail at runtime.

## Build system layout

- Root `build.gradle.kts` configures version + `generateChangelog`; no production code.
- `replication/` is the only published module.
- `build-logic/` is an included build providing the `ch.hippmann.publish` convention plugin (`PublishToMavenCentralPlugin`). It wraps `com.vanniktech.maven.publish` and only registers signing/remote publishing if all four credentials resolve — otherwise publishing degrades to local-only without error. If a publish unexpectedly produces no signed artifacts, check that all four properties are set.
- `replication/build.gradle.kts` sets `isRegistrationFileGenerationEnabled = false` and disables `shadowJar` — both are needed to avoid jar conflicts when publishing a library (rather than a Godot project) through the godot-kotlin-jvm plugin.
