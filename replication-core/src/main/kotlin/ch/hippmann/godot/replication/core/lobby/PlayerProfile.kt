package ch.hippmann.godot.replication.core.lobby

import kotlinx.serialization.Serializable

@Serializable
public class PlayerProfile(
    public val name: String,
    public val customData: ByteArray? = null,
) {
    public fun copy(name: String = this.name, customData: ByteArray? = this.customData): PlayerProfile =
        PlayerProfile(name, customData)

    override fun equals(other: Any?): Boolean =
        other is PlayerProfile && other.name == name && other.customData.contentEqualsNullable(customData)

    override fun hashCode(): Int = 31 * name.hashCode() + (customData?.contentHashCode() ?: 0)

    override fun toString(): String = "PlayerProfile(name=$name, customData=${customData?.size ?: 0} bytes)"
}

internal fun ByteArray?.contentEqualsNullable(other: ByteArray?): Boolean =
    if (this == null || other == null) this === other else contentEquals(other)
