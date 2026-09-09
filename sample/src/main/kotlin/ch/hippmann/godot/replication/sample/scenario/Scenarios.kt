package ch.hippmann.godot.replication.sample.scenario

import ch.hippmann.godot.replication.core.level.LevelPolicy

object Scenarios {
    fun create(name: String): Scenario = when (name) {
        "host_join_password_ok" -> HostJoinPasswordScenario()
        "join_wrong_password" -> JoinWrongPasswordScenario()
        "rpc_over_mesh" -> RpcOverMeshScenario()
        "client_leave" -> ClientLeaveScenario()
        "master_leave_reelection" -> MasterLeaveReelectionScenario()
        "lan_discovery" -> LanDiscoveryScenario()
        "lobby_flow" -> LobbyFlowScenario()
        "three_peers_sync" -> ThreePeersSyncScenario()
        "spawn_despawn" -> SpawnDespawnScenario()
        "ownership_transfer" -> OwnershipTransferScenario()
        "owner_leave_policies" -> OwnerLeavePoliciesScenario()
        "level_wait_for_all" -> LevelPolicyScenario(LevelPolicy.WaitForAll(stragglerTimeoutMilliseconds = 20_000))
        "level_start_when_loaded" -> LevelPolicyScenario(LevelPolicy.StartWhenLoaded)
        "late_join_snapshot" -> LateJoinSnapshotScenario()
        "reconnect_after_drop" -> ReconnectAfterDropScenario()
        "interest_distance" -> InterestDistanceScenario()
        "custom_messages" -> CustomMessagesScenario()
        "simulated_latency" -> SimulatedLatencyScenario()
        "state_latency" -> StateLatencyScenario()
        "replication_load" -> ReplicationLoadScenario()
        "join_by_code" -> JoinByCodeScenario()
        else -> throw ScenarioFailure("Unknown scenario $name")
    }
}
