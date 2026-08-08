@file:Suppress("WRONG_MODIFIER_TARGET", "WRONG_ANNOTATION_TARGET")

package dev.inlineannotations.metrorecipes

import dev.inlineannotations.InlineAnnotations
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn

/** Application lifetime used by the Metro proof-of-concept graph. */
public object AppScope

/**
 * The repeated Metro binding pattern this proof-of-concept is intended to remove.
 *
 * In a codebase that requires explicit injection, every app-scoped interface binding otherwise
 * repeats the contribution annotation and the same scope argument twice:
 *
 * @Inject
 * @ContributesBinding(AppScope::class)
 * @SingleIn(AppScope::class)
 * class RealRepository : Repository
 */
@InlineAnnotations // prototype-only binary recipe marker for downstream compilation
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
public inline annotation class AppScopedBinding
