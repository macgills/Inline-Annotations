package dev.inlineannotations.sample

import dev.inlineannotations.library.CrossModuleFirst
import dev.inlineannotations.library.CrossModuleSecond
import dev.inlineannotations.library.LibraryBundle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

public class CrossModuleInlineAnnotationsTest {
    @Test
    public fun compiledLibraryBundleExpandsInConsumerCompilation() {
        val method = Class.forName("dev.inlineannotations.sample.CrossModuleFixtureKt")
            .getDeclaredMethod("crossModuleTarget")

        assertEquals(
            "library",
            assertNotNull(method.getAnnotation(CrossModuleFirst::class.java)).value,
        )
        assertEquals(
            42,
            assertNotNull(method.getAnnotation(CrossModuleSecond::class.java)).number,
        )
        assertNull(method.getAnnotation(LibraryBundle::class.java))
    }
}
