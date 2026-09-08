package ch.hippmann.godot.replication.core.session

import ch.hippmann.godot.replication.core.lobby.PlayerProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MembershipTest {
    private fun member(id: Int, name: String) = MemberRecord(PlayerId(id), PlayerProfile(name), listOf(Endpoint("10.0.0.$id", 7777)))

    private val hosting = Membership.hosting(SessionId(42), member(2, "Mara"))

    @Test
    fun `the host is the first master and allocates increasing ids`() {
        assertEquals(PlayerId.FIRST_HOST, hosting.master)
        assertEquals(3, hosting.nextJoinSequence)

        val (first, afterFirst) = hosting.allocateNext()
        val (second, afterSecond) = afterFirst.allocateNext()
        assertEquals(PlayerId(3), first)
        assertEquals(PlayerId(4), second)
        assertEquals(5, afterSecond.nextJoinSequence)
    }

    @Test
    fun `the master is always the smallest live id`() {
        val three = hosting.with(member(3, "Tobias")).with(member(4, "Lena"))
        assertEquals(PlayerId(2), three.master)
        assertEquals(PlayerId(3), three.without(PlayerId(2)).master)
        assertEquals(PlayerId(4), three.without(PlayerId(2)).without(PlayerId(3)).master)
        assertEquals(PlayerId.NONE, Membership(SessionId(1), Epoch.INITIAL, emptyMap(), 2).master)
    }

    @Test
    fun `every member computes the same master regardless of leave order`() {
        val members = (2..6).map { id -> member(id, "Player$id") }
        val full = members.fold(Membership(SessionId(7), Epoch.INITIAL, emptyMap(), 7)) { membership, record -> membership.with(record) }
        val leftInOneOrder = full.without(PlayerId(2)).without(PlayerId(4))
        val leftInAnotherOrder = full.without(PlayerId(4)).without(PlayerId(2))
        assertEquals(leftInOneOrder.master, leftInAnotherOrder.master)
        assertEquals(PlayerId(3), leftInOneOrder.master)
    }

    @Test
    fun `ids are never reused within a session`() {
        val (id, allocated) = hosting.allocateNext()
        val afterLeave = allocated.with(member(id.value, "Tobias")).without(id)
        val (next, _) = afterLeave.allocateNext()
        assertEquals(PlayerId(4), next)
        assertFalse(afterLeave.contains(id))
        assertTrue(afterLeave.contains(PlayerId(2)))
    }

    @Test
    fun `epochs only move forward`() {
        val migrated = hosting.without(PlayerId(2)).withEpoch(hosting.epoch.next())
        assertEquals(Epoch(2), migrated.epoch)
        assertEquals(Epoch(3), migrated.epoch.next())
    }
}
