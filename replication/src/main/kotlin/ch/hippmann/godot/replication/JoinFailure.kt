package ch.hippmann.godot.replication

import ch.hippmann.godot.replication.core.session.PlayerId
import ch.hippmann.godot.replication.core.wire.RejectReason

sealed class JoinFailure(message: String) : RuntimeException(message) {
    class Unreachable(address: String, port: Int) : JoinFailure("No member answered at $address:$port")

    class Rejected(val reason: RejectReason) : JoinFailure("The master rejected the join: $reason")

    class Timeout(detail: String) : JoinFailure(detail)

    class MeshIncomplete(val missing: Set<PlayerId>) : JoinFailure("Could not reach members ${missing.map { it.value }}")

    class LinkLost(detail: String) : JoinFailure(detail)
}
