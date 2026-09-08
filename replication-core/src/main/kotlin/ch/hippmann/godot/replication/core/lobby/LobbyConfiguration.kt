package ch.hippmann.godot.replication.core.lobby

import kotlinx.serialization.Serializable

@Serializable
public class LobbyConfiguration(
    public val name: String,
    public val maximumPlayers: Int = 8,
    public val password: String? = null,
    public val locked: Boolean = false,
    public val settings: Map<String, String> = emptyMap(),
    public val customData: ByteArray? = null,
) {
    public val passwordRequired: Boolean
        get() = !password.isNullOrEmpty()

    public fun copy(
        name: String = this.name,
        maximumPlayers: Int = this.maximumPlayers,
        password: String? = this.password,
        locked: Boolean = this.locked,
        settings: Map<String, String> = this.settings,
        customData: ByteArray? = this.customData,
    ): LobbyConfiguration = LobbyConfiguration(name, maximumPlayers, password, locked, settings, customData)

    /** The configuration as every member sees it: the password never leaves the host. */
    public fun withoutPassword(): LobbyConfiguration = copy(password = null)

    override fun equals(other: Any?): Boolean =
        other is LobbyConfiguration &&
            other.name == name &&
            other.maximumPlayers == maximumPlayers &&
            other.password == password &&
            other.locked == locked &&
            other.settings == settings &&
            other.customData.contentEqualsNullable(customData)

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + maximumPlayers
        result = 31 * result + (password?.hashCode() ?: 0)
        result = 31 * result + locked.hashCode()
        result = 31 * result + settings.hashCode()
        result = 31 * result + (customData?.contentHashCode() ?: 0)
        return result
    }

    override fun toString(): String =
        "LobbyConfiguration(name=$name, maximumPlayers=$maximumPlayers, passwordRequired=$passwordRequired, locked=$locked, settings=$settings)"
}
