package dev.inlineannotations.metropoc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

public class MetroInlineAnnotationsTest {
    @Test
    public fun metroConsumesExpandedInlineAnnotations() {
        val graph = createDemoGraph()

        assertEquals("metro-inline", graph.analytics.event())
        assertEquals(42L, graph.clock.now())
        assertEquals("https://api.example.test", graph.endpoint.value)

        assertSame(graph.analytics, graph.analytics)
        assertSame(graph.clock, graph.clock)

        assertNull(RealAnalytics::class.java.getAnnotation(AppSingletonBinding::class.java))
        assertNull(SystemClock::class.java.getAnnotation(AppSingletonBinding::class.java))
        assertNull(DemoGraph::class.java.getAnnotation(AppGraph::class.java))
        assertNull(NetworkBindings::class.java.getAnnotation(AppBindingContainer::class.java))
    }
}
