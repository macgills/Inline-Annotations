@file:Suppress("WRONG_MODIFIER_TARGET", "WRONG_ANNOTATION_TARGET")

package dev.inlineannotations.metrorecipes

import dev.inlineannotations.InlineAnnotations
import dev.zacsweers.metro.Qualifier
import dev.zacsweers.metro.SingleIn

/** Application lifetime used by the Metro proof-of-concept graph. */
public object AppScope

/** Distinguishes the authenticated API client from the public client of the same type. */
@Qualifier
@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.VALUE_PARAMETER,
)
@Retention(AnnotationRetention.RUNTIME)
public annotation class Authenticated

/**
 * A project policy that otherwise has to be repeated on every authenticated app-lifetime binding:
 * the value is qualified as authenticated and cached for the lifetime of the application graph.
 */
@InlineAnnotations // prototype-only binary recipe marker for downstream compilation
@Authenticated
@SingleIn(AppScope::class)
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
public inline annotation class AuthenticatedAppSingleton
