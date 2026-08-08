package dev.inlineannotations.compiler

import kotlinx.collections.immutable.toPersistentList
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.DeclarationCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirTypeParameterChecker
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirValueParameterChecker
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension
import org.jetbrains.kotlin.fir.declarations.FirClassLikeDeclaration
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirDeclarationStatus
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.FirTypeParameter
import org.jetbrains.kotlin.fir.declarations.FirValueParameter
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.declarations.toAnnotationClass
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassId
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.FirExtensionApiInternals
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar
import org.jetbrains.kotlin.fir.extensions.FirExtensionSessionComponent
import org.jetbrains.kotlin.fir.extensions.FirStatusTransformerExtension
import org.jetbrains.kotlin.fir.extensions.FirSupertypeGenerationExtension
import org.jetbrains.kotlin.fir.extensions.predicate.DeclarationPredicate
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.extensions.transform
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

internal class InlineAnnotationsFirExtensionRegistrar : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        +FirExtensionSessionComponent.Factory(::InlineAnnotationsFirState)
        +::InlineAnnotationsFirSupertypeExpansionExtension
        +::InlineAnnotationsFirStatusTransformer
        +::InlineAnnotationsFirCheckersExtension
    }
}

private class InlineAnnotationsFirState(
    session: FirSession,
) : FirExtensionSessionComponent(session) {
    val inlineAnnotationClassIds: MutableSet<ClassId> = mutableSetOf()
    val earlyExpandedClassIds: MutableSet<ClassId> = mutableSetOf()
}

private val FirSession.inlineAnnotationsState: InlineAnnotationsFirState by FirSession.sessionComponentAccessor()

/**
 * Compatibility bridge for annotation-sensitive third-party FIR plugins.
 *
 * Kotlin builds its compiler-plugin annotation index before STATUS. Our original semantic prototype
 * expanded annotations during STATUS, which is sufficient for ordinary frontend/backend consumers but
 * too late for plugins such as Metro whose declaration generators query that index after SUPER_TYPES.
 *
 * A language implementation would make the effective annotation set part of the compiler's own early
 * annotation pipeline. The prototype emulates that by expanding class annotations at SUPER_TYPES and
 * re-registering the declaration in Kotlin's predicate provider. Nothing here is Metro-specific.
 */
private class InlineAnnotationsFirSupertypeExpansionExtension(
    session: FirSession,
) : FirSupertypeGenerationExtension(session) {
    private val expander = InlineAnnotationsFirExpander(session)

    override fun FirDeclarationPredicateRegistrar.registerPredicates() {
        register(INLINE_ANNOTATIONS_META_PREDICATE)
    }

    override fun needTransformSupertypes(declaration: FirClassLikeDeclaration): Boolean =
        declaration is FirRegularClass && declaration.annotations.isNotEmpty()

    @OptIn(FirExtensionApiInternals::class)
    override fun computeAdditionalSupertypes(
        classLikeDeclaration: FirClassLikeDeclaration,
        resolvedSupertypes: List<FirResolvedTypeRef>,
        typeResolver: TypeResolveService,
    ): List<ConeKotlinType> {
        val declaration = classLikeDeclaration as? FirRegularClass ?: return emptyList()
        val expanded = expander.expand(declaration.annotations)
        if (expanded == declaration.annotations) return emptyList()

        declaration.replaceAnnotations(expanded)
        session.inlineAnnotationsState.earlyExpandedClassIds += declaration.symbol.classId

        val owners = session.predicateBasedProvider
            .getOwnersOfDeclaration(declaration)
            ?.map { it.fir }
            ?.toPersistentList()
            ?: return emptyList()

        session.predicateBasedProvider.registerAnnotatedDeclaration(declaration, owners)
        return emptyList()
    }
}

private class InlineAnnotationsFirStatusTransformer(
    session: FirSession,
) : FirStatusTransformerExtension(session) {
    private val expander = InlineAnnotationsFirExpander(session)

    override fun needTransformStatus(declaration: FirDeclaration): Boolean {
        val isInlineAnnotationClass = expander.registerInlineAnnotationClass(declaration)
        if (
            declaration !is FirRegularClass ||
            declaration.symbol.classId !in session.inlineAnnotationsState.earlyExpandedClassIds
        ) {
            declaration.replaceAnnotations(expander.expand(declaration.annotations))
        }
        return isInlineAnnotationClass
    }

    override fun transformStatus(
        status: FirDeclarationStatus,
        declaration: FirDeclaration,
    ): FirDeclarationStatus = if (
        declaration is FirRegularClass &&
        declaration.symbol.classId in session.inlineAnnotationsState.inlineAnnotationClassIds
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
    fun registerInlineAnnotationClass(declaration: FirDeclaration): Boolean {
        if (!declaration.isPrototypeInlineAnnotationClass()) return false

        session.inlineAnnotationsState.inlineAnnotationClassIds += (declaration as FirRegularClass).symbol.classId
        return true
    }

    fun expand(annotations: List<FirAnnotation>): List<FirAnnotation> =
        expand(annotations, linkedSetOf())

    private fun expand(
        annotations: List<FirAnnotation>,
        expansionStack: MutableSet<ClassId>,
    ): List<FirAnnotation> = buildList {
        for (annotation in annotations) {
            val annotationClass = annotation.toAnnotationClass(session)
            val classId = annotation.toAnnotationClassId(session)

            if (annotationClass == null || classId == null) {
                add(annotation)
                continue
            }

            if (!annotationClass.isInlineAnnotationBundle()) {
                add(annotation)
                continue
            }

            check(expansionStack.add(classId)) {
                "Cyclic inline annotation bundle involving ${classId.asSingleFqName()}"
            }

            try {
                addAll(
                    expand(
                        annotations = annotationClass.annotations.filterNot(::isInfrastructureAnnotation),
                        expansionStack = expansionStack,
                    ),
                )
            } finally {
                expansionStack.remove(classId)
            }
        }
    }

    private fun FirRegularClass.isInlineAnnotationBundle(): Boolean =
        symbol.classId in session.inlineAnnotationsState.inlineAnnotationClassIds ||
            isPrototypeInlineAnnotationClass() ||
            hasAnnotation(INLINE_ANNOTATIONS_CLASS_ID, session)

    private fun FirDeclaration.isPrototypeInlineAnnotationClass(): Boolean =
        this is FirRegularClass &&
            classKind == ClassKind.ANNOTATION_CLASS &&
            status.isInline

    private fun isInfrastructureAnnotation(annotation: FirAnnotation): Boolean {
        val classId = annotation.toAnnotationClassId(session) ?: return false
        val fqName = classId.asSingleFqName()
        return classId == INLINE_ANNOTATIONS_CLASS_ID ||
            fqName == SUPPRESS_FQ_NAME ||
            fqName in KOTLIN_ANNOTATION_META_FQ_NAMES
    }
}

private val INLINE_ANNOTATIONS_CLASS_ID = ClassId.topLevel(FqName("dev.inlineannotations.InlineAnnotations"))
private val INLINE_ANNOTATIONS_FQ_NAME = INLINE_ANNOTATIONS_CLASS_ID.asSingleFqName()
private val INLINE_ANNOTATIONS_META_PREDICATE = DeclarationPredicate.create {
    metaAnnotated(INLINE_ANNOTATIONS_FQ_NAME, includeItself = false)
}
private val SUPPRESS_FQ_NAME = FqName("kotlin.Suppress")

private val KOTLIN_ANNOTATION_META_FQ_NAMES = setOf(
    FqName("kotlin.annotation.Target"),
    FqName("kotlin.annotation.Retention"),
    FqName("kotlin.annotation.MustBeDocumented"),
    FqName("kotlin.annotation.Repeatable"),
)
