package dev.inlineannotations.library

import dev.inlineannotations.InlineAnnotations

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
public annotation class CrossModuleFirst(val value: String)

@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
public annotation class CrossModuleSecond(val number: Int)

@Suppress("WRONG_ANNOTATION_TARGET")
@InlineAnnotations
@CrossModuleFirst("library")
@CrossModuleSecond(42)
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
public annotation class LibraryBundle
