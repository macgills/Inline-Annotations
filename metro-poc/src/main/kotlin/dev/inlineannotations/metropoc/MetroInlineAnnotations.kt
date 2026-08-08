@file:Suppress("WRONG_MODIFIER_TARGET", "WRONG_ANNOTATION_TARGET")

package dev.inlineannotations.metropoc

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.createGraph

public object AppScope

@DependencyGraph(AppScope::class)
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
public inline annotation class AppGraph

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
public inline annotation class AppSingletonBinding

@ContributesTo(AppScope::class)
@BindingContainer
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
public inline annotation class AppBindingContainer

public interface Analytics {
    public fun event(): String
}

public interface Clock {
    public fun now(): Long
}

@AppSingletonBinding
public class RealAnalytics : Analytics {
    override fun event(): String = "metro-inline"
}

@AppSingletonBinding
public class SystemClock : Clock {
    override fun now(): Long = 42L
}

public data class Endpoint(public val value: String)

@AppBindingContainer
public object NetworkBindings {
    @Provides
    public fun provideEndpoint(): Endpoint = Endpoint("https://api.example.test")
}

@AppGraph
public interface DemoGraph {
    public val analytics: Analytics
    public val clock: Clock
    public val endpoint: Endpoint
}

public fun createDemoGraph(): DemoGraph = createGraph<DemoGraph>()
