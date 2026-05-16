package ch.hippmann.godot.replication.integrationtests

/**
 * Declares the Godot scene a [TestScenario] expects as its main scene. The
 * orchestrator reads this via reflection at launch time WITHOUT instantiating the
 * scenario class, which means scenario classes can hold `godot.core.*` types in
 * their fields without faulting the orchestrator's classpath (which doesn't have
 * the godot-api stubs — those are `compileOnly` deps that only the main source set
 * runtime needs because Godot itself provides them).
 *
 * Scenarios without this annotation fall back to `res://scenes/replication_basic.tscn`.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class TestScene(val path: String)
