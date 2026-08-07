package dev.inlineannotations.compiler

import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.DeclarationCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirTypeParameterChecker
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirValueParameterChecker
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirDeclarationStatus
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.FirTypeParameter
import org.jetbrains.kotlin.fir.declarations.FirValueParameter
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.declarations.toAnnotationClass
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassId
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar
import org.jetbrains.kotlin.fir.extensions.FirExtensionSessionComponent
import org.jetbrains.kotlin.fir.extensions.FirStatusTransformerExtension
import org.jetbrains.kotlin.fir.extensions.transform
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

internal class InlineAnnotationsFirExtensionRegistrar : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        +FirExtensionSessionComponent.Factory(::InlineAnnotationsFirState)
        +::InlineAnnotationsFirStatusTransformer
        +::InlineAnnotationsFirCheckersExtension
    }
}

private class InlineAnnotationsFirState(
    session: FirSession,
) : FirExtensionSessionComponent(session) {
    val bundleClassIds: MutableSet<ClassId> = mutableSetOf()
    val recipes: MutableMap<ClassId, List<FirAnnotation>> = mutableMapOf()
}

private val FirSession.inlineAnnotationsState: InlineAnnotationsFirState by FirSession.sessionComponentAccessor()

private data class BundleDeclaration(
    val usesInlineModifier: Boolean,
)

private class InlineAnnotationsFirStatusTransformer(
    session: FirSession,
) : FirStatusTransformerExtension(session) {
    private val expander = InlineAnnotationsFirExpander(session)

    override fun needTransformStatus(declaration: FirDeclaration): Boolean {
        val bundle = expander.prepareBundleDeclaration(declaration)
        if (bundle == null) {
            declaration.replaceAnnotations(expander.expand(declaration.annotations))
        }
        return bundle?.usesInlineModifier == true
    }

    override fun transformStatus(
        status: FirDeclarationStatus,
        declaration: FirDeclaration,
    ): FirDeclarationStatus = if (
        declaration is FirRegularClass &&
        declaration.symbol.classId in session.inlineAnnotationsState.bundleClassIds &&
        status.isInline
    ) {
        status.transform {
            isInline = false
            isValue = false
        }
    } else {
        status
    }
}

private class InlineAnnotationsFirCheckersExtension(
    session: FirSession,
) : FirAdditionalCheckersExtension(session) {
    override val declarationCheckers: DeclarationCheckers = object : DeclarationCheckers() {
        override val typeParameterCheckers: Set<FirTypeParameterChecker> =
            setOf(InlineAnnotationsTypeParameterChecker(session))

        override val valueParameterCheckers: Set<FirValueParameterChecker> =
            setOf(InlineAnnotationsValueParameterChecker(session))
    }
}

private class InlineAnnotationsTypeParameterChecker(
    private val session: FirSession,
) : FirTypeParameterChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirTypeParameter) {
        declaration.replaceAnnotations(InlineAnnotationsFirExpander(session).expand(declaration.annotations))
    }
}

private class InlineAnnotationsValueParameterChecker(
    private val session: FirSession,
) : FirValueParameterChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirValueParameter) {
        declaration.replaceAnnotations(InlineAnnotationsFirExpander(session).expand(declaration.annotations))
    }
}

private class InlineAnnotationsFirExpander(
    private val session: FirSession,
) {
    fun prepareBundleDeclaration(declaration: FirDeclaration): BundleDeclaration? {
        if (declaration !is FirRegularClass || declaration.classKind != ClassKind.ANNOTATION_CLASS) {
            return null
        }

        val usesInlineModifier = declaration.status.isInline
        val usesPrototypeMarker = declaration.hasAnnotation(INLINE_ANNOTATIONS_CLASS_ID, session)
        if (!usesInlineModifier && !usesPrototypeMarker) {
            return null
        }

        val classId = declaration.symbol.classId
        val state = session.inlineAnnotationsState
        state.bundleClassIds += classId
        state.recipes[classId] = declaration.annotations.filterNot(::isInfrastructureAnnotation)

        // Constituents are a recipe, not annotations applied to the annotation class itself.
        // Removing them here is what lets a FUNCTION-only annotation participate in a
        // FUNCTION-only bundle without requiring ANNOTATION_CLASS in its own @Target.
        declaration.replaceAnnotations(declaration.annotations.filter(::isInfrastructureAnnotation))

        return BundleDeclaration(usesInlineModifier)
    }

    fun expand(annotations: List<FirAnnotation>): List<FirAnnotation> =
        expand(annotations, linkedSetOf())

    private fun expand(
        annotations: List<FirAnnotation>,
        expansionStack: MutableSet<ClassId>,
    ): List<FirAnnotation> = buildList {
        for (annotation in annotations) {
            val classId = annotation.toAnnotationClassId(session)
            val annotationClass = annotation.toAnnotationClass(session)
            val recipe = if (classId != null && annotationClass != null) {
                recipeFor(classId, annotationClass)
            } else {
                null
            }

            if (classId == null || recipe == null) {
                add(annotation)
                continue
            }

            check(expansionStack.add(classId)) {
                "Cyclic inline annotation bundle involving ${classId.asSingleFqName()}"
            }

            try {
                addAll(expand(recipe, expansionStack))
            } finally {
                expansionStack.remove(classId)
            }
        }
    }

    private fun recipeFor(
        classId: ClassId,
        annotationClass: FirRegularClass,
    ): List<FirAnnotation>? {
        session.inlineAnnotationsState.recipes[classId]?.let { return it }

        // Prototype metadata fallback: a bundle compiled without this plugin retains
        // @InlineAnnotations and its recipe annotations in Kotlin/JVM metadata.
        if (annotationClass.hasAnnotation(INLINE_ANNOTATIONS_CLASS_ID, session)) {
            return annotationClass.annotations.filterNot(::isInfrastructureAnnotation)
        }

        return null
    }

    private fun isInfrastructureAnnotation(annotation: FirAnnotation): Boolean {
        val classId = annotation.toAnnotationClassId(session) ?: return false
        return classId == INLINE_ANNOTATIONS_CLASS_ID ||
            classId.asSingleFqName() in KOTLIN_ANNOTATION_META_FQ_NAMES
    }
}

private val INLINE_ANNOTATIONS_CLASS_ID = ClassId.topLevel(FqName("dev.inlineannotations.InlineAnnotations"))

private val KOTLIN_ANNOTATION_META_FQ_NAMES = setOf(
    FqName("kotlin.annotation.Target"),
    FqName("kotlin.annotation.Retention"),
    FqName("kotlin.annotation.MustBeDocumented"),
    FqName("kotlin.annotation.Repeatable"),
)
