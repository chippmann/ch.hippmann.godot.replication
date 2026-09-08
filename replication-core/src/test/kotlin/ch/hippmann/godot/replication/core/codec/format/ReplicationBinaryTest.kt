package ch.hippmann.godot.replication.core.codec.format

import ch.hippmann.godot.replication.core.codec.ByteWriter
import kotlinx.serialization.Contextual
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReplicationBinaryTest {

    @Serializable
    enum class Weapon { WRENCH, CROWBAR, LANTERN }

    @Serializable
    @JvmInline
    value class Credits(val amount: Int)

    @Serializable
    data class Position(val x: Double, val y: Double)

    @Serializable
    data class Loadout(
        val weapon: Weapon,
        val credits: Credits,
        val nickname: String?,
        val tags: List<String>,
        val scores: Map<String, Int>,
        val spawn: Position,
        val secondary: Weapon? = null,
        val flags: Boolean = true,
    )

    @Serializable
    sealed interface Command {
        @Serializable
        data class Move(val target: Position) : Command

        @Serializable
        data object Halt : Command
    }

    data class Fraction(val numerator: Int, val denominator: Int)

    private object FractionSerializer : KSerializer<Fraction> {
        override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Fraction", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: Fraction) = encoder.encodeString("${value.numerator}/${value.denominator}")

        override fun deserialize(decoder: Decoder): Fraction {
            val (numerator, denominator) = decoder.decodeString().split("/")
            return Fraction(numerator.toInt(), denominator.toInt())
        }
    }

    @Serializable
    data class WithContextual(@Contextual val ratio: Fraction, val label: String)

    @Test
    fun `nested data classes with collections and nullables round trip`() {
        val loadout = Loadout(
            weapon = Weapon.CROWBAR,
            credits = Credits(250),
            nickname = null,
            tags = listOf("engineer", "veteran"),
            scores = mapOf("arena" to 12, "hangar" to -3),
            spawn = Position(1.5, -2.25),
            secondary = Weapon.LANTERN,
        )
        val bytes = ReplicationBinary.Default.encodeToByteArray(serializer(), loadout)
        val restored = ReplicationBinary.Default.decodeFromByteArray<Loadout>(serializer(), bytes)

        assertEquals(loadout, restored)
        assertTrue(bytes.size < 64, "compact encoding, was ${bytes.size} bytes")
    }

    @Test
    fun `sealed hierarchies carry the serial name`() {
        val commands: List<Command> = listOf(Command.Move(Position(3.0, 4.0)), Command.Halt)
        val bytes = ReplicationBinary.Default.encodeToByteArray(serializer<List<Command>>(), commands)
        assertEquals(commands, ReplicationBinary.Default.decodeFromByteArray(serializer<List<Command>>(), bytes))
    }

    @Test
    fun `contextual serializers come from the module`() {
        val format = ReplicationBinary(SerializersModule { contextual(Fraction::class, FractionSerializer) })
        val value = WithContextual(Fraction(3, 4), "ratio")
        val bytes = format.encodeToByteArray(serializer(), value)
        assertEquals(value, format.decodeFromByteArray<WithContextual>(serializer(), bytes))
    }

    @Test
    fun `values written into a shared writer decode in sequence`() {
        val writer = ByteWriter()
        ReplicationBinary.Default.encodeTo(writer, serializer(), Position(1.0, 2.0))
        ReplicationBinary.Default.encodeTo(writer, serializer(), "tail")
        val bytes = writer.toByteArray()

        val expected = ByteWriter().apply { writeFloat64(1.0); writeFloat64(2.0); writeString("tail") }.toByteArray()
        assertContentEquals(expected, bytes)
    }
}
