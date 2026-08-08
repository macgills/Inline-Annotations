package dev.inlineannotations.metropoc

import dev.inlineannotations.metrorecipes.AppScopedProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

public class MetroInlineAnnotationsTest {
    @Test
    public fun metroBuildsARealGraphFromInlineScopedProviders() {
        val graph = createAppGraph()

        assertEquals(
            User(id = "42", displayName = "Ada"),
            graph.userRepository.currentUser(),
        )
        assertEquals("signed-in:Ada", graph.analytics.currentUserLabel())

        assertSame(graph.userRepository, graph.userRepository)
        assertSame(graph.analytics, graph.analytics)

        assertNull(
            AppGraph::class.java
                .getDeclaredMethod("provideUserRepository")
                .getAnnotation(AppScopedProvider::class.java),
        )
        assertNull(
            AppGraph::class.java
                .getDeclaredMethod("provideAnalytics", UserRepository::class.java)
                .getAnnotation(AppScopedProvider::class.java),
        )
    }
}
