package dev.inlineannotations.sample

import java.lang.reflect.AnnotatedElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

public class InlineAnnotationsTest {
    @Test
    public fun expandsBundleAcrossJvmDeclarationTargets() {
        Example::class.java.assertExpanded()
        Example::class.java.declaredConstructors.single().assertExpanded()
        Example::class.java.getDeclaredField("value").assertExpanded()
        Example::class.java.getDeclaredMethod("getValue").assertExpanded()

        val function = Example::class.java.getDeclaredMethod("run", String::class.java)
        function.assertExpanded()
        function.parameters.single().assertExpanded()

        Example.Generic::class.java.typeParameters.single().assertExpanded()
    }

    private fun AnnotatedElement.assertExpanded() {
        assertEquals("expanded", assertNotNull(getAnnotation(First::class.java)).value)
        assertEquals(7, assertNotNull(getAnnotation(Second::class.java)).number)
        assertNull(getAnnotation(Bundle::class.java))
        assertNull(getAnnotation(NestedBundle::class.java))
    }
}
