package dev.inlineannotations.compiler

import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirDeclarationStatus
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.declarations.toAnnotationClass
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassId
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar
import org.jetbrains.kotlin.fir.extensions.FirStatusTransformerExtension
import org.jetbrains.kotlin.fir.extensions.transform
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

internal class InlineAnnotationsFirExtensionRegistrar : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        +::InlineAnnotationsFirStatusTransformer
    }
}

private class InlineAnnotationsFirStatusTransformer(
    session: FirSession,
) : FirStatusTransformerExtension(session) {
    override fun needTransformStatus(declaration: FirDeclaration): Boolean {
        declaration.replaceAnnotations(expand(declaration.annotations, linkedSetOf()))
        return declaration.isPrototypeInlineAnnotationClass()
    }

    override fun transformStatus(
        status: FirDeclarationStatus,
        declaration: FirDeclaration,
    ): FirDeclarationStatus = if (declaration.isPrototypeInlineAnnotationClass()) {
        status.transform {
            isInline = false
            isValue = false
        }
    } else {
        status
    }

    private fun expand(
        annotations: List<FirAnnotation>,
        expansionStack: MutableSet<ClassId>,
    ): List<FirAnnotation> = buildList {
        for (annotation in annotations) {
            val annotationClass = annotation.toAnnotationClass(session)
            val classId = annotation.toAnnotationClassId(session)

            if (annotationClass == null || classId == null || !annotationClass.isInlineAnnotationBundle()) {
                add(annotation)
                continue
            }

            check(expansionStack.add(classId)) {
                "Cyclic inline annotation bundle involving ${classId.asSingleFqName()}"
            }

            try {
                addAll(
                    expand(
                        annotationClass.annotations.filterNot(::isInfrastructureAnnotation),
                        expansionStack,
                    ),
                )
            } finally {
                expansionStack.remove(classId)
            }
        }
    }

    private fun FirRegularClass.isInlineAnnotationBundle(): Boolean =
        isPrototypeInlineAnnotationClass() || hasAnnotation(INLINE_ANNOTATIONS_CLASS_ID, session)

    private fun FirDeclaration.isPrototypeInlineAnnotationClass(): Boolean =
        this is FirRegularClass &&
            classKind == ClassKind.ANNOTATION_CLASS &&
            status.isInline

    private fun isInfrastructureAnnotation(annotation: FirAnnotation): Boolean {
        val classId = annotation.toAnnotationClassId(session) ?: return false
        return classId == INLINE_ANNOTATIONS_CLASS_ID || classId.asSingleFqName() in KOTLIN_ANNOTATION_META_FQ_NAMES
    }
}

private val INLINE_ANNOTATIONS_CLASS_ID = ClassId.topLevel(FqName("dev.inlineannotations.InlineAnnotations"))

private val KOTLIN_ANNOTATION_META_FQ_NAMES = setOf(
    FqName("kotlin.annotation.Target"),
    FqName("kotlin.annotation.Retention"),
    FqName("kotlin.annotation.MustBeDocumented"),
    FqName("kotlin.annotation.Repeatable"),
)
