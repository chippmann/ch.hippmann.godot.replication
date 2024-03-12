# Godot Kotlin Jvm replication
This is a small library providing basic godot multiplayer replication from kotlin for kotlin. Written for [godot kotlin jvm](https://github.com/utopia-rise/godot-kotlin-jvm) projects.

> **Note:** this library is pretty barebones, and inefficient but should be quite easy to use. There is no lag compensation or similar present atm.

## Usage
At the moment this library is not pushed to maven central. In order to use it, you'll either have to facilitate gradles composite build feature or publish it locally.

### Composite build
- Clone the project
- Add the following to the project in which you intend to use this library:
  ```kotlin
  // settings.gradle.kts
  includeBuild("path/to/this/library/cloned/to/your/machine") {
    dependencySubstitution {
        substitute(module("ch.hippmann.godot:replication")).using(project(":"))
    }
  }
  ```
  ```kotlin
  // build.gradle.kts
  dependencies {
    implementation("ch.hippmann.godot:replication:0.0.1")
    
    // if you plan on using the coroutine helpers; don't forget to add the kotlinx coroutines dependency:
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
  }
  ```

### Publish locally
- Clone the project
- `gradlew publishToMavenLocal`
- Add the following to the project in which you intend to use this library:
  ```kotlin
  // build.gradle.kts
  dependencies {
    implementation("ch.hippmann.godot:replication:0.0.1")
    
    // if you plan on using the coroutine helpers; don't forget to add the kotlinx coroutines dependency:
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
  }
  ```

## Usage
### Autoload
Its required that you add the following autoload singletons:

- From [ch.hippmann.godot:utilities](https://github.com/chippmann/ch.hippmann.godot.utilities) add the [DispatcherSingleton](https://github.com/chippmann/ch.hippmann.godot.utilities/blob/main/src/main/kotlin/ch/hippmann/godot/utilities/coroutines/DispatcherSingleton.kt)
- From this library, add the [RemoteListenerReadyRedirector](src/main/kotlin/ch/hippmann/godot/replication/autoload/RemoteListenerReadyRedirector.kt)

### Replication (spawning)
If you want to have nodes replicated automatically if you add them to a node, implement the `Replicated` interface with the `Replicator` delegate and add your class to a node: 
```kotlin
@RegisterClass
class ExampleReplicator: Node3D(), Replicated by Replicator() {
	@RegisterFunction
	override fun _enterTree() {
        // this line is needed to setup the replication! Needs to happen before _ready
		initReplication()
	}
}
```

Nodes of type `Replicated` expose an editor property `managedScenes`. This you need to fill with all the packed scenes you want to have replicated.  
Once such a scene is added as a direct child of `Replicated` on the network authority it gets also spawned on all other peers.  
So keep in mind to fill this property in the editor and properly define you network authorities for the `Replicated` nodes!

If the root node of a managed scene implements `Synchronized`, data you defined as `syncOnSpawn` in your `syncConfig` gets synced automatically on spawn of the child scene on a peer. More on that later.

### Synchronisation
If you want certain properties to be synchronized across all peers, implement `Synchronized` with the delegate `Synchronizer`.  
You can configure what is to be synchronized and when through overriding of the property `syncConfig`:
```kotlin
@RegisterClass
class Player: CharacterBody3D(), Synchronized by Synchronizer() {
    override val syncConfig: SyncConfigs = syncConfig {
        property(::team)
        property(::speed)
        property(::jumpVelocity)
        property(::velocity) {
            shouldSendUpdate = { current, last -> !last.isEqualApprox(current) }
        }
        // config example:
        property(::globalTransform) {
          // sync every tick milliseconds
          tick = 16 // default 16ms
          // send reliable or unreliable (tcp or udp)
          syncMethod = SyncConfig.SyncMethod.UNRELIABLE // default: SyncConfig.SyncMethod.RELIABLE
          // sync property on spawn
          syncOnSpawn = true // defaut: true
          // sync property on tick
          syncOnTick = true // default: true
          // whether to send an update
          shouldSendUpdate = { current, last -> !last.isEqualApprox(current) } // default: { current, last -> current != last }
        }
    }

    @Export
    @RegisterProperty
    var speed: Double = 500.0

    @Export
    @RegisterProperty
    var jumpVelocity: Double = 5.0

    var team: Int = 0

    @RegisterFunction
    override fun _enterTree() {
        // needed to init the synchronisation. Needs to happen before _ready
        // after this call, no changes to the `syncConfig` can be made!
        initSynchronization()
    }

    @RegisterFunction
    override fun _process(delta: Double) {
        // call periodically. If this gets called less than tick, the next time this gets called, multiple sync events might be sent at the same time
        // sync calls are queued on every tick, but are sent when this function is called. So it is advised that this function gets called more frequently than your lowest tick config
        // this calls godot's rpc functions internally. So you as the caller, need to make sure that this call happens at a time when godot can send rpc calls. _progress is a good candidate for this.
        performSynchronization()
    }
}
```

**Important:** For `Synchronized` you also need to make sure to properly set up the network authority before adding it to the tree! At the moment, the authority cannot be changed afterward!

Some notes regarding syncOnSpawn:
- `syncOnSpawn` only has an effect if the `Synchronized` node is a direct child of `Replicated`! Otherwise, it will only sync upon first tick.
- Sync on spawn takes the initial spawn data from the `syncConfig` after it has been added to the tree as a child of `Replicated`. So in order to make sure that the desired data is set, set all required properties before adding it to the tree. If this is not possible for a property (like the build in position for example), then it can only be synced upon first tick