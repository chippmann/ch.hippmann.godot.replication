package ch.hippmann.godot.replication

sealed interface NetworkState {
    data object Offline : NetworkState

    data class Joining(val step: JoinStep) : NetworkState

    data object Connected : NetworkState

    data object Leaving : NetworkState
}

enum class JoinStep {
    CONNECTING,
    AUTHENTICATING,
    MESHING,
    LOADING_LEVEL,
    SYNCHRONIZING,
}
