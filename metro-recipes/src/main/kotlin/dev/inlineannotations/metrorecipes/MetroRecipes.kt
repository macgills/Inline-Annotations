@file:Suppress("WRONG_MODIFIER_TARGET", "WRONG_ANNOTATION_TARGET")

package dev.inlineannotations.metrorecipes

import dev.inlineannotations.InlineAnnotations
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn

/** Application lifetime used by the Metro proof-of-concept graph. */
public object AppScope

/**
 * The repetitive Metro pattern this proof is intended to remove.
 *
 * Without inline annotation classes every app-scoped implementation repeats both annotations and
 * the same scope argument:
 *
 * @ContributesBinding(AppScope::class)
 * @SingleIn(AppScope::class)
 * class RealThing : Thing
 */
@InlineAnnotations // prototype-only binary recipe marker for downstream compilation
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
public inline annotation class AppScopedBinding
