# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A Kotlin library that adds basic multiplayer node replication and property synchronization on top of [godot-kotlin-jvm](https://github.com/utopia-rise/godot-kotlin-jvm). Consumers wire it into their own godot-kotlin-jvm Godot project; this repo only produces the published artifact (no Godot project of its own). Published to Maven Central as `ch.hippmann.godot:replication`.

## Build / publish commands

- `./gradlew build` — compile, lint, test the `replication` module.
- `./gradlew :replication:publishToMavenLocal` — install the artifact locally (useful for testing against a consumer project via `mavenLocal()`).
- `./gradlew :replication-integration-tests:test` — run the multi-peer integration tests (see "Integration tests" below). Requires the custom godot-kotlin-jvm Godot binary at `/Applications/Godot.app/Contents/MacOS/Godot`; override with `GODOT_BIN` env var.
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
- `replication-integration-tests/` is an integration-test-only Godot project; never published. Has a sibling-repo composite-build dependency on `../ch.hippmann.godot.utilities` to dodge a version skew in the published utilities-0.0.9 jar (see "Integration tests" below).
- `build-logic/` is an included build providing the `ch.hippmann.publish` convention plugin (`PublishToMavenCentralPlugin`). It wraps `com.vanniktech.maven.publish` and only registers signing/remote publishing if all four credentials resolve — otherwise publishing degrades to local-only without error. If a publish unexpectedly produces no signed artifacts, check that all four properties are set.
- `replication/build.gradle.kts` sets `isRegistrationFileGenerationEnabled = false` and disables `shadowJar` — both are needed to avoid jar conflicts when publishing a library (rather than a Godot project) through the godot-kotlin-jvm plugin.

## Integration tests (`replication-integration-tests/`)

Multi-peer end-to-end tests. Architecture: a Kotest orchestrator runs in plain JVM; each test method spawns N headless Godot subprocesses via `ProcessBuilder` and joins on their exit codes + JSON result files written under a temp `resultDir`.

### Scene-based setup, code-driven actions

Every scenario's test fixture (Replicator, managed scenes, exported properties) lives in a `.tscn` file under `replication-integration-tests/scenes/`. Scenarios don't construct node graphs in code — they assume the scene is already in tree and only drive runtime behavior (multiplayer start/connect, scenario-specific spawns, assertions). `scenes/replication_basic.tscn` is the default for scenarios that just need TestRunner + a configured Replicator; other scenarios can supply their own `.tscn` via `TestScenario.scenePath`.

- **`TestRunner`** is a `@RegisterClass Node` set as the script of every scene's root. `_ready` reads `OS.getCmdlineUserArgs()`, parses the test args, instantiates the scenario by FQCN, launches it on a coroutine, and writes a JSON result + `quit(0|1)` on completion.
- **`TestScenario`** has two `suspend` functions, `runAsServer` / `runAsClient`, plus an optional `scenePath` override that the orchestrator reads by reflection at launch time.
- **`TestContext`** wraps the runner and provides `startServer` / `connectToServer` / `awaitClientsConnected` / `awaitServerConnected` / `awaitServerDisconnected` / `awaitAllClientsDisconnected` / `pollUntil` / `put` / `replicator` (looks up the Replicator at `Replicator` relative to runner — convention from the default scene). All signal awaits use `CompletableDeferred` set from `.asCallable {}` wrappers — see notes below on why.
- **`ProcessOrchestrator`** launches/kills Godot subprocesses, owns a random port + temp result dir, collects each peer's JSON result. `launch(scenarioClass, role, peerName, expectedClientCount)` returns a `PeerHandle`; `killPeer(handle)` simulates crash/network loss; `awaitAll(timeoutSeconds)` joins everyone with force-kill on overrun.
- **`GodotMainDispatcher`** is the coroutine dispatcher scenarios run on. It queues continuations into a `ConcurrentLinkedQueue` drained from `TestRunner._process` every frame. This is mandatory: godot-kotlin-jvm enforces "Multiplayer / Node operations must come from the main thread", and `kotlinx.coroutines.delay` resumes on its own `DefaultExecutor` thread otherwise. The `DispatcherSingleton` from utilities is **not sufficient** — it captures `runBlocking`'s event-loop dispatcher, not Godot's main thread.

### Pitfalls and conventions baked into the harness

- **Property names in `.tscn` files use snake_case** (Godot convention) even though the Kotlin source declares them in camelCase. Use `managed_scenes = [...]` in scene files, not `managedScenes = [...]`.
- **`SimpleReplicator` / `SimpleSynchronizer` in the library are stubs** — they don't override `_enterTree` to call `initReplication` / `initSynchronization`, so they never actually initialize at runtime. Test scenarios use `IntegrationTestReplicator` (in `replication-integration-tests/src/main/kotlin/.../fixtures/`) which does the override. Anything new added in this style needs the same pattern.
- **Signal connection** must go through `asCallable {}` (from `godot.core`) rather than the typed `connect(target, fn)` overload. The latter is shadowed by the base `Signal.connect(callable, flags)` overload in 2-arg form, and the compiler picks the wrong one.
- **The Gradle plugin writes dep-supplied `.gdj` files under `gdj/dependencies/<libProjectName>/...`** but Godot-JVM resolves `@RegisterClass` instances at canonical FQ paths `gdj/<package>/...`. A `flattenDependencyGdj` task in `replication-integration-tests/build.gradle.kts` mirrors them up, hooked off `copyJars`.
- **The KSP processor that generates `godot.Entry`** runs on every Kotlin source set by default. We disable `kspTestKotlin` because the Kotest orchestrator runs OUTSIDE Godot and shouldn't have a registered Entry class. Without this, the test source fails to compile against runtime types it can't see.
- **`utilities-0.0.9` published jar references `kotlinx-datetime` 0.7.x types** (and indirectly `kotlin.time.Clock`, which the godot-kotlin-jvm 0.13.1 bootstrap stdlib strips). The fix is a composite build pointing at the sibling `../ch.hippmann.godot.utilities` repo — utilities's source already pins `kotlinx-datetime = 0.6.2` in its libs catalog, so a fresh build of it produces compatible bytecode. Plus a `force("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")` in `replication-integration-tests/build.gradle.kts` for transitive callers.

### Adding a new scenario

1. Decide if the default scene (`scenes/replication_basic.tscn`) fits. If not, copy and adapt it under `scenes/<your_scenario>.tscn` and reference it from `TestScenario.scenePath`.
2. Implement `class YourScenario : TestScenario` under `src/main/kotlin/.../scenarios/`. Use `context.replicator`, `context.startServer`, etc. — don't construct nodes.
3. Register any new fixture classes with `@RegisterClass` so they get `.gdj` generated, then reference them in your `.tscn` by their REPL/UTIL-prefixed registered names.
4. Write a Kotest spec under `src/test/kotlin/...` that uses `ProcessOrchestrator` (look at `SpawnTest` / `LateJoinTest` for the pattern).
5. Build first: `./gradlew :replication-integration-tests:assemble` so `.gdj` files exist before the test launches Godot. Then `./gradlew :replication-integration-tests:test`.
