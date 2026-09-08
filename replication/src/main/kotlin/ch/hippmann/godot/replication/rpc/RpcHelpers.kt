package ch.hippmann.godot.replication.rpc

import ch.hippmann.godot.replication.Network
import godot.api.Node
import godot.common.extensions.convertToSnakeCase
import godot.core.Error
import godot.core.asStringName
import kotlin.reflect.KFunction

/** Untyped helpers over Godot's `rpc_id`; the method name is the Kotlin name, converted like the registrar does. */
fun Node.rpcTo(target: Target, method: String, vararg args: Any?): Error {
    var result = Error.OK
    for (player in target.resolve()) {
        if (player == Network.localPlayerId) continue
        val error = rpcId(player.value.toLong(), method.convertToSnakeCase().asStringName(), *args)
        if (error != Error.OK) result = error
    }
    return result
}

fun Node.rpcMaster(method: String, vararg args: Any?): Error = rpcTo(Target.Master, method, *args)

fun Node.rpcOwner(method: String, vararg args: Any?): Error = rpcTo(Target.Owner(this), method, *args)

fun Node.rpcTo(target: Target, function: KFunction<*>, vararg args: Any?): Error = rpcTo(target, function.name, *args)

fun Node.rpcMaster(function: KFunction<*>, vararg args: Any?): Error = rpcTo(Target.Master, function.name, *args)

fun Node.rpcOwner(function: KFunction<*>, vararg args: Any?): Error = rpcTo(Target.Owner(this), function.name, *args)
