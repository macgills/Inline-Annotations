package dev.inlineannotations.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrDeclarationBase
import org.jetbrains.kotlin.ir.declarations.IrMutableAnnotationContainer
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.expressions.IrAnnotation
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.deepCopyWithoutPatchingParents
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

internal class InlineAnnotationsIrGenerationExtension : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        moduleFragment.transformChildrenVoid(InlineAnnotationsTransformer(pluginContext))
    }
}

private class InlineAnnotationsTransformer(
    private val pluginContext: IrPluginContext,
) : IrElementTransformerVoid() {
    override fun visitDeclaration(declaration: IrDeclarationBase) =
        super.visitDeclaration(declaration).also {
            if (declaration is IrMutableAnnotationContainer) {
                declaration.annotations = declaration.annotations.flatMap(::expand)
            }
        }

    private fun expand(annotation: IrAnnotation): List<IrAnnotation> =
        expand(annotation, linkedSetOf())

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    @Suppress("DEPRECATION")
    private fun expand(
        annotation: IrAnnotation,
        expansionStack: MutableSet<ClassId>,
    ): List<IrAnnotation> {
        val classId = annotation.classId ?: return listOf(annotation)
        val annotationClass = pluginContext.referenceClass(classId)?.owner ?: return listOf(annotation)

        if (!annotationClass.hasAnnotation(INLINE_ANNOTATIONS_FQ_NAME)) {
            return listOf(annotation)
        }

        check(expansionStack.add(classId)) {
            "Cyclic inline annotation bundle involving ${classId.asSingleFqName()}"
        }

        return try {
            annotationClass.annotations
                .asSequence()
                .filterNot(IrAnnotation::isInfrastructureAnnotation)
                .flatMap { expand(it, expansionStack).asSequence() }
                .map { it.deepCopyWithoutPatchingParents() }
                .toList()
        } finally {
            expansionStack.remove(classId)
        }
    }
}

private fun IrAnnotation.isInfrastructureAnnotation(): Boolean {
    val fqName = classId?.asSingleFqName() ?: return false
    return fqName == INLINE_ANNOTATIONS_FQ_NAME || fqName in KOTLIN_ANNOTATION_META_FQ_NAMES
}

private val INLINE_ANNOTATIONS_FQ_NAME = FqName("dev.inlineannotations.InlineAnnotations")

private val KOTLIN_ANNOTATION_META_FQ_NAMES = setOf(
    FqName("kotlin.annotation.Target"),
    FqName("kotlin.annotation.Retention"),
    FqName("kotlin.annotation.MustBeDocumented"),
    FqName("kotlin.annotation.Repeatable"),
)
