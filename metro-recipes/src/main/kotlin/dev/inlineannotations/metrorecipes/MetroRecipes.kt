@file:Suppress("WRONG_MODIFIER_TARGET", "WRONG_ANNOTATION_TARGET")

package dev.inlineannotations.metrorecipes

import dev.inlineannotations.InlineAnnotations
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/** Application lifetime used by the Metro proof-of-concept graph. */
public object AppScope

/**
 * A small but real Metro preset: every application-scoped provider otherwise repeats both
 * annotations and the same scope argument.
 *
 * Without inline annotation classes:
 *
 * @Provides
 * @SingleIn(AppScope::class)
 * fun repository(): Repository = RealRepository()
 */
@InlineAnnotations // prototype-only binary recipe marker for downstream compilation
@Provides
@SingleIn(AppScope::class)
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
public inline annotation class AppScopedProvider
