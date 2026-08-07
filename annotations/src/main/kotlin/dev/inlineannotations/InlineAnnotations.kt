package dev.inlineannotations

/**
 * Prototype-only marker for an annotation bundle.
 *
 * The language proposal replaces this marker with `inline annotation class`.
 */
@Target(AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.BINARY)
public annotation class InlineAnnotations
