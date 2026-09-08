package ch.hippmann.godot.replication.core.wire

/** First byte of every library packet. Values 0xF0 to 0xFF are reserved for Godot RPC packets (0xF0 + channel). */
public enum class MessageType(public val id: Int) {
    JOIN_REQUEST(0x01),
    REDIRECT(0x02),
    CHALLENGE(0x03),
    JOIN_PROOF(0x04),
    ADMITTED(0x05),
    REJECTED(0x06),
    HELLO(0x07),
    HELLO_ACCEPTED(0x08),

    MEMBER_JOINED(0x10),
    LEAVE(0x11),
    MEMBERSHIP_UPDATE(0x12),
    KICK(0x13),

    LOBBY_UPDATE(0x20),
    PLAYER_UPDATE(0x21),
    LOBBY_COMMAND(0x22),

    LEVEL_LOAD(0x30),
    LEVEL_LOADED(0x31),
    LEVEL_START(0x32),

    SPAWN(0x40),
    DESPAWN(0x41),

    OWNERSHIP_CHANGED(0x50),
    OWNERSHIP_REQUEST(0x51),
    OWNERSHIP_REPLY(0x52),

    STATE_DELTA(0x60),
    STATE_RELIABLE(0x61),
    STATE_FULL(0x62),

    SNAPSHOT_REQUEST(0x70),
    SNAPSHOT_CHUNK(0x71),
    SNAPSHOT_DONE(0x72),

    TIME_SYNC(0x80),
    CUSTOM(0x81),
    ;

    public companion object {
        public const val RPC_PACKET_BASE: Int = 0xF0

        private val byId = entries.associateBy { type -> type.id }

        public fun fromId(id: Int): MessageType? = byId[id]

        public fun isRpcPacket(firstByte: Int): Boolean = firstByte >= RPC_PACKET_BASE
    }
}
