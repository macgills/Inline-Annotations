package dev.inlineannotations.metropoc

import dev.inlineannotations.metrorecipes.Authenticated
import dev.inlineannotations.metrorecipes.AuthenticatedAppSingleton
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

public class MetroInlineAnnotationsTest {
    @Test
    public fun metroConsumesTheExpandedQualifierAndLifetimePolicy() {
        val graph = createAppGraph()

        assertNull(graph.publicApiClient.authorizationHeader())
        assertEquals("Bearer demo-token", graph.authenticatedApiClient.authorizationHeader())
        assertSame(graph.authenticatedApiClient, graph.authenticatedApiClient)

        val provider = AppGraph::class.java.getDeclaredMethod("provideAuthenticatedApiClient")
        assertNull(provider.getAnnotation(AuthenticatedAppSingleton::class.java))
        assertEquals(
            Authenticated::class.java,
            provider.getAnnotation(Authenticated::class.java).annotationClass.java,
        )
    }
}
