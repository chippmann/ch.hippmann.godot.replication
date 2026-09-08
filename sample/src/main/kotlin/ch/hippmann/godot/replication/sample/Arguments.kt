package ch.hippmann.godot.replication.sample

/** `--key=value` user arguments after Godot's `--` separator; flags without a value map to "true". */
class Arguments(private val values: Map<String, String>) {
    operator fun get(key: String): String? = values[key]

    fun int(key: String, default: Int): Int = values[key]?.toIntOrNull() ?: default

    fun long(key: String, default: Long): Long = values[key]?.toLongOrNull() ?: default

    companion object {
        fun parse(rawArguments: List<String>): Arguments = Arguments(
            rawArguments
                .filter { argument -> argument.startsWith("--") }
                .associate { argument ->
                    val body = argument.removePrefix("--")
                    val key = body.substringBefore("=")
                    val value = if ("=" in body) body.substringAfter("=") else "true"
                    key to value
                },
        )
    }
}
