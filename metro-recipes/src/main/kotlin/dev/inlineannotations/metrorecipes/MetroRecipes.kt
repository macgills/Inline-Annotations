@file:Suppress("WRONG_MODIFIER_TARGET", "WRONG_ANNOTATION_TARGET")

package dev.inlineannotations.metrorecipes

import dev.inlineannotations.InlineAnnotations
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.SingleIn

public object AppScope

@InlineAnnotations // prototype-only binary recipe marker for downstream compilation
@DependencyGraph(AppScope::class)
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
public inline annotation class AppGraph

@InlineAnnotations // prototype-only binary recipe marker for downstream compilation
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
public inline annotation class AppSingletonBinding

@InlineAnnotations // prototype-only binary recipe marker for downstream compilation
@ContributesTo(AppScope::class)
@BindingContainer
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
public inline annotation class AppBindingContainer
