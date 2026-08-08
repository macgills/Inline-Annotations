package dev.inlineannotations

/**
 * Prototype-only binary marker for cross-module recipe discovery.
 *
 * Source declarations use the proposed `inline annotation class` syntax directly.
 * A production language implementation would replace this marker with dedicated Kotlin recipe metadata.
 */
@Target(AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.BINARY)
public annotation class InlineAnnotations
