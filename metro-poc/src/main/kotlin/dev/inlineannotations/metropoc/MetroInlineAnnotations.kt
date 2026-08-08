package dev.inlineannotations.metropoc

import dev.inlineannotations.metrorecipes.AppBindingContainer
import dev.inlineannotations.metrorecipes.AppGraph
import dev.inlineannotations.metrorecipes.AppSingletonBinding
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraph

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
