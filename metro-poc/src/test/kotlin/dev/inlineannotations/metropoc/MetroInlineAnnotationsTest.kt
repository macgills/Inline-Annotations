package dev.inlineannotations.metropoc

import dev.inlineannotations.metrorecipes.AppScopedBinding
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

public class MetroInlineAnnotationsTest {
    @Test
    public fun metroBuildsARealGraphFromInlineScopedBindings() {
        val graph = createAppGraph()

        assertEquals(
            User(id = "42", displayName = "Ada"),
            graph.userRepository.currentUser(),
        )
        assertEquals("signed-in:Ada", graph.analytics.currentUserLabel())

        assertSame(graph.userRepository, graph.userRepository)
        assertSame(graph.analytics, graph.analytics)

        assertNull(RealUserRepository::class.java.getAnnotation(AppScopedBinding::class.java))
        assertNull(DefaultAnalytics::class.java.getAnnotation(AppScopedBinding::class.java))
    }
}
