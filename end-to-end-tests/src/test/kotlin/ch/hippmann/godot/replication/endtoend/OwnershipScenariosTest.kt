package ch.hippmann.godot.replication.endtoend

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import kotlin.test.assertEquals

@Timeout(240)
class OwnershipScenariosTest {

    @Test
    fun `ownership moves by request, by refusal and by explicit transfer`() {
        GodotCluster("ownership_transfer", "ownership_transfer").use { cluster ->
            val host = cluster.startHost("Mara")
            val tobias = cluster.startClient("Tobias", "--start-delay-milliseconds=500")
            val lena = cluster.startClient("Lena", "--start-delay-milliseconds=2500")
            cluster.awaitAllPassed()

            assertEquals(3, cluster.assertEvent(host, "crate_taken").int("owner"))
            assertEquals("true", cluster.assertEvent(tobias, "door_request").string("granted"))
            assertEquals("false", cluster.assertEvent(lena, "door_request").string("granted"))
            assertEquals(4, cluster.assertEvent(host, "crate_pushed_after_transfer").int("owner"))
            assertEquals(10, cluster.assertEvent(tobias, "saw_final_pushes").int("pushes"))
        }
    }

    @Test
    fun `owner leave policies despawn or hand nodes to the master, also across a master change`() {
        GodotCluster("owner_leave_policies", "owner_leave_policies").use { cluster ->
            val host = cluster.startHost("Mara")
            cluster.startClient("Tobias", "--start-delay-milliseconds=500")
            val lena = cluster.startClient("Lena", "--start-delay-milliseconds=2500")
            cluster.awaitAllPassed()

            assertEquals(2, cluster.assertEvent(host, "inherited_crate").int("owner"))
            assertEquals(7, cluster.assertEvent(lena, "saw_master_inherit").int("pushes"))
            val inherited = cluster.assertEvent(lena, "inherited_all")
            assertEquals(1, inherited.int("projectiles"))
            assertEquals(3, inherited.fields["crates"]!!.toString().split(",").size)
        }
    }
}
