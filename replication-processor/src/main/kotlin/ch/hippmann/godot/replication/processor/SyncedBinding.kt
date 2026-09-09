package ch.hippmann.godot.replication.processor

import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSPropertyDeclaration

/** The `@Synced` arguments of one property as the `synced(...)` call the generated object makes. */
class SyncedBinding(
    val name: String,
    val reliable: Boolean,
    val continuous: Boolean,
    val rate: Int,
    val idleAfterTicks: Int,
    val interpolate: Boolean,
    val half: Boolean,
    val step: Double,
    val doublePrecision: Boolean,
) {
    fun render(): String {
        val options = buildList {
            add(if (reliable) "reliable()" else "unreliable()")
            add(if (continuous) "continuous($rate, $idleAfterTicks)" else "onChange()")
            if (interpolate) add("interpolate()")
            if (half) add("quantize(Quantization.Half)")
            if (step > 0.0) add("quantize(Quantization.Fixed($step))")
            if (doublePrecision) add("precision(Precision.Double)")
        }
        return "        node.synced(\"$name\", { node.$name }, { value -> node.$name = value }) { ${options.joinToString("; ")} }"
    }

    companion object {
        fun from(property: KSPropertyDeclaration): SyncedBinding {
            val annotation = property.annotations.first { candidate -> candidate.shortName.asString() == "Synced" }
            return SyncedBinding(
                name = property.simpleName.asString(),
                reliable = annotation.argument("reliable", true),
                continuous = annotation.argument("continuous", false),
                rate = annotation.argument("rate", 0),
                idleAfterTicks = annotation.argument("idleAfterTicks", 10),
                interpolate = annotation.argument("interpolate", false),
                half = annotation.argument("half", false),
                step = annotation.argument("step", 0.0),
                doublePrecision = annotation.argument("doublePrecision", false),
            )
        }

        private inline fun <reified T> KSAnnotation.argument(name: String, default: T): T =
            arguments.firstOrNull { argument -> argument.name?.asString() == name }?.value as? T ?: default
    }
}
