package dev.inlineannotations.library

import dev.inlineannotations.InlineAnnotations

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
public annotation class CrossModuleFirst(val value: String)

@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
public annotation class CrossModuleSecond(val number: Int)

@Suppress("WRONG_MODIFIER_TARGET", "WRONG_ANNOTATION_TARGET")
@InlineAnnotations // prototype-only binary marker for downstream module discovery
@CrossModuleFirst("library")
@CrossModuleSecond(42)
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
public inline annotation class LibraryBundle
