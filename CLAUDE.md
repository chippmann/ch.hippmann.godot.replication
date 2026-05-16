# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A Kotlin library that adds basic multiplayer node replication and property synchronization on top of [godot-kotlin-jvm](https://github.com/utopia-rise/godot-kotlin-jvm). Consumers wire it into their own godot-kotlin-jvm Godot project; this repo only produces the published artifact (no Godot project of its own). Published to Maven Central as `ch.hippmann.godot:replication`.

## Build / publish commands

- `./gradlew build` — compile, lint, test the `replication` module.
- `./gradlew :replication:publishToMavenLocal` — install the artifact locally (useful for testing against a consumer project via `mavenLocal()`).
- `./gradlew :replication-it:test` — run the multi-peer integration tests (see "Integration tests" below). Requires the custom godot-kotlin-jvm Godot binary at `/Applications/Godot.app/Contents/MacOS/Godot`; override with `GODOT_BIN` env var.
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
- `replication-it/` is an integration-test-only Godot project; never published. Has a sibling-repo composite-build dependency on `../ch.hippmann.godot.utilities` to dodge a version skew in the published utilities-0.0.9 jar (see "Integration tests" below).
- `build-logic/` is an included build providing the `ch.hippmann.publish` convention plugin (`PublishToMavenCentralPlugin`). It wraps `com.vanniktech.maven.publish` and only registers signing/remote publishing if all four credentials resolve — otherwise publishing degrades to local-only without error. If a publish unexpectedly produces no signed artifacts, check that all four properties are set.
- `replication/build.gradle.kts` sets `isRegistrationFileGenerationEnabled = false` and disables `shadowJar` — both are needed to avoid jar conflicts when publishing a library (rather than a Godot project) through the godot-kotlin-jvm plugin.

## Integration tests (`replication-it/`)

Multi-peer end-to-end tests. Architecture: a Kotest orchestrator runs in plain JVM; each test method spawns N headless Godot subprocesses via `ProcessBuilder` and joins on their exit codes + JSON result files written under a temp `resultDir`.

- **`TestRunner`** is a `@RegisterClass Node` attached to `test_runner.tscn`. Its `_ready` reads `OS.getCmdlineUserArgs()`, parses the scenario FQCN + role + port + peer id + result dir, instantiates the scenario by reflection, launches it on a coroutine, and writes a JSON result + `quit(0|1)` on completion.
- **`TestScenario`** has two `suspend` functions, `runAsServer` / `runAsClient`. Each scenario is one Kotlin file.
- **`TestContext`** wraps the runner and provides `startServer` / `connectToServer` / `awaitClientsConnected` / `awaitServerConnected` / `pollUntil` / `put` / `report`. All signal awaits use `CompletableDeferred` set from callables wrapped via `.asCallable {}` — see notes below on why this is needed.
- **`GodotMainDispatcher`** is the coroutine dispatcher scenarios run on. It queues continuations into a `ConcurrentLinkedQueue` drained from `TestRunner._process` every frame. This is mandatory: godot-kotlin-jvm enforces "Multiplayer / Node operations must come from the main thread", and `kotlinx.coroutines.delay` resumes on its own `DefaultExecutor` thread otherwise. The `DispatcherSingleton` from utilities is **not sufficient** — it captures `runBlocking`'s event-loop dispatcher, not Godot's main thread.

### Pitfalls and conventions baked into the harness

- **Replicator must be added AFTER multiplayer is connected on the client side.** The library's `_ready` handshake immediately RPCs through the `RemoteListenerReadyRedirector` autoload; if the peer isn't connected yet, that RPC is silently dropped and the spawn never propagates.
- **`SimpleReplicator` / `SimpleSynchronizer` in the library are stubs** — they don't override `_enterTree` to call `initReplication` / `initSynchronization`, so they never actually initialize at runtime. Test scenarios use `ITReplicator` (in `replication-it/src/main/kotlin/.../fixtures/`) which does the override. Anything new added in this style needs the same pattern.
- **Signal connection** must go through `asCallable {}` (from `godot.core`) rather than the typed `connect(target, fn)` overload. The latter is shadowed by the base `Signal.connect(callable, flags)` overload in 2-arg form, and the compiler picks the wrong one.
- **The Gradle plugin writes dep-supplied `.gdj` files under `gdj/dependencies/<libProjectName>/...`** but Godot-JVM resolves `@RegisterClass` instances at canonical FQ paths `gdj/<package>/...`. A `flattenDependencyGdj` task in `replication-it/build.gradle.kts` mirrors them up, hooked off `copyJars`.
- **The KSP processor that generates `godot.Entry`** runs on every Kotlin source set by default. We disable `kspTestKotlin` because the Kotest orchestrator runs OUTSIDE Godot and shouldn't have a registered Entry class. Without this, the test source fails to compile against runtime types it can't see.
- **`utilities-0.0.9` published jar references `kotlinx-datetime` 0.7.x types** (and indirectly `kotlin.time.Clock`, which the godot-kotlin-jvm 0.13.1 bootstrap stdlib strips). The fix is a composite build pointing at the sibling `../ch.hippmann.godot.utilities` repo — utilities's source already pins `kotlinx-datetime = 0.6.2` in its libs catalog, so a fresh build of it produces compatible bytecode. Plus a `force("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")` in `replication-it/build.gradle.kts` for transitive callers.

### Adding a new scenario

1. Implement `class FooScenario : TestScenario` under `replication-it/src/main/kotlin/.../scenarios/`.
2. Register any new fixture classes with `@RegisterClass` so they get `.gdj` generated.
3. Write a Kotest spec under `src/test/kotlin/...` that uses `ProcessOrchestrator` to launch the scenario (look at `SpawnTest` for the pattern).
4. Build first: `./gradlew :replication-it:assemble` ensures the `.gdj` files for new fixtures land in the canonical location. Then `./gradlew :replication-it:test` runs the scenarios.
