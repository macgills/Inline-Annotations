package dev.inlineannotations.metropoc

import dev.inlineannotations.metrorecipes.Authenticated
import dev.inlineannotations.metrorecipes.AuthenticatedAppSingleton
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame

public class MetroInlineAnnotationsTest {
    @Test
    public fun metroConsumesTheExpandedQualifierAndLifetimePolicy() {
        val graph = createAppGraph()

        val publicClient = graph.publicApiClient
        val authenticatedClient = graph.authenticatedApiClient

        assertNull(publicClient.authorizationHeader())
        assertEquals("Bearer demo-token", authenticatedClient.authorizationHeader())
        assertNotSame(publicClient, authenticatedClient)
        assertSame(authenticatedClient, graph.authenticatedApiClient)

        val provider = AppGraph::class.java.getDeclaredMethod("provideAuthenticatedApiClient")
        assertNull(provider.getAnnotation(AuthenticatedAppSingleton::class.java))
        assertNotNull(provider.getAnnotation(Authenticated::class.java))
    }
}
