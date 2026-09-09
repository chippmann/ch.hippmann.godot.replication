package ch.hippmann.godot.replication.processor

import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.Modifier

class SyncedProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor = SyncedProcessor(environment.codeGenerator, environment.logger)
}

/** One `<ClassName>SyncedProperties` object per node class with `@Synced` properties, in the class's package. */
class SyncedProcessor(private val codeGenerator: CodeGenerator, private val logger: KSPLogger) : SymbolProcessor {
    override fun process(resolver: Resolver): List<KSAnnotated> {
        val annotated = resolver.getSymbolsWithAnnotation(SYNCED_ANNOTATION).filterIsInstance<KSPropertyDeclaration>().toSet()
        val owners = annotated.mapNotNull { property -> property.parentDeclaration as? KSClassDeclaration }.distinct()
        for (owner in owners) {
            val properties = owner.getDeclaredProperties().filter { property -> property in annotated }.toList()
            if (validate(owner, properties)) generate(owner, properties)
        }
        return emptyList()
    }

    private fun validate(owner: KSClassDeclaration, properties: List<KSPropertyDeclaration>): Boolean {
        var valid = true
        if (owner.parentDeclaration != null) {
            logger.error("@Synced properties need a top level class, ${owner.simpleName.asString()} is nested", owner)
            valid = false
        }
        if (owner.getAllSuperTypes().none { type -> type.declaration.qualifiedName?.asString() == NODE_CLASS }) {
            logger.error("${owner.simpleName.asString()} declares @Synced properties but does not extend $NODE_CLASS", owner)
            valid = false
        }
        for (property in properties) {
            if (!property.isMutable) {
                logger.error("@Synced ${property.simpleName.asString()} must be a var", property)
                valid = false
            }
            if (Modifier.PRIVATE in property.modifiers) {
                logger.error("@Synced ${property.simpleName.asString()} must not be private, the generated binding reads and writes it", property)
                valid = false
            }
        }
        return valid
    }

    private fun generate(owner: KSClassDeclaration, properties: List<KSPropertyDeclaration>) {
        val packageName = owner.packageName.asString()
        val className = owner.simpleName.asString()
        val objectName = className + GENERATED_SUFFIX
        val bindings = properties.joinToString("\n") { property -> SyncedBinding.from(property).render() }
        val imports = listOf("ch.hippmann.godot.replication.sync.GeneratedSyncedProperties", "ch.hippmann.godot.replication.sync.synced") +
            listOf("ch.hippmann.godot.replication.core.codec.Precision", "ch.hippmann.godot.replication.core.codec.Quantization")
                .filter { import -> import.substringAfterLast('.') + "." in bindings }
        val source = buildString {
            appendLine("package $packageName")
            appendLine()
            imports.sorted().forEach { import -> appendLine("import $import") }
            appendLine()
            appendLine("public object $objectName : GeneratedSyncedProperties<$className> {")
            appendLine("    override fun bind(node: $className) {")
            appendLine(bindings)
            appendLine("    }")
            appendLine("}")
        }
        val dependencies = Dependencies(aggregating = false, *listOfNotNull(owner.containingFile).toTypedArray())
        codeGenerator.createNewFile(dependencies, packageName, objectName).use { stream -> stream.write(source.toByteArray()) }
    }

    private companion object {
        const val SYNCED_ANNOTATION = "ch.hippmann.godot.replication.sync.Synced"
        const val NODE_CLASS = "godot.api.Node"
        const val GENERATED_SUFFIX = "SyncedProperties"
    }
}
