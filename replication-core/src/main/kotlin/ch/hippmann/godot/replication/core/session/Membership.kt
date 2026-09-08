package ch.hippmann.godot.replication.core.session

/** The membership view every member keeps; the master is derived, never stored. */
public data class Membership(
    val sessionId: SessionId,
    val epoch: Epoch,
    val members: Map<PlayerId, MemberRecord>,
    val nextJoinSequence: Int,
) {
    val master: PlayerId
        get() = members.keys.minByOrNull { id -> id.value } ?: PlayerId.NONE

    val ids: Set<PlayerId>
        get() = members.keys

    public fun contains(id: PlayerId): Boolean = id in members

    /** Keeps the allocation counter ahead of every id seen, so a member promoted to master never reuses one. */
    public fun with(member: MemberRecord): Membership = copy(
        members = members + (member.id to member),
        nextJoinSequence = maxOf(nextJoinSequence, member.id.value + 1),
    )

    public fun without(id: PlayerId): Membership = copy(members = members - id)

    public fun allocateNext(): Pair<PlayerId, Membership> =
        PlayerId(nextJoinSequence) to copy(nextJoinSequence = nextJoinSequence + 1)

    public fun withEpoch(epoch: Epoch): Membership = copy(epoch = epoch)

    public companion object {
        public fun hosting(sessionId: SessionId, host: MemberRecord): Membership = Membership(
            sessionId = sessionId,
            epoch = Epoch.INITIAL,
            members = mapOf(host.id to host),
            nextJoinSequence = host.id.value + 1,
        )
    }
}
