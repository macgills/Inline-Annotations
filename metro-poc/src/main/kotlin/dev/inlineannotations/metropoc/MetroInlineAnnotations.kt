package dev.inlineannotations.metropoc

import dev.inlineannotations.metrorecipes.AppScope
import dev.inlineannotations.metrorecipes.Authenticated
import dev.inlineannotations.metrorecipes.AuthenticatedAppSingleton
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraph

public interface ApiClient {
    public fun authorizationHeader(): String?
}

private class RealApiClient(
    private val authorizationHeader: String?,
) : ApiClient {
    override fun authorizationHeader(): String? = authorizationHeader
}

@DependencyGraph(AppScope::class)
public interface AppGraph {
    public val publicApiClient: ApiClient

    @Authenticated
    public val authenticatedApiClient: ApiClient

    @Provides
    public fun providePublicApiClient(): ApiClient = RealApiClient(authorizationHeader = null)

    @Provides
    @AuthenticatedAppSingleton
    public fun provideAuthenticatedApiClient(): ApiClient =
        RealApiClient(authorizationHeader = "Bearer demo-token")
}

public fun createAppGraph(): AppGraph = createGraph<AppGraph>()
