package dev.inlineannotations.sample

import java.lang.reflect.AnnotatedElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

public class InlineAnnotationsTest {
    @Test
    public fun expandsBundleAcrossJvmDeclarationTargets() {
        Example::class.java.assertExpanded("class")
        Example::class.java.declaredConstructors.single().assertExpanded("constructor")
        Example::class.java.getDeclaredField("value").assertExpanded("field")
        Example::class.java.getDeclaredMethod("getValue").assertExpanded("getter")

        val function = Example::class.java.getDeclaredMethod("run", String::class.java)
        function.assertExpanded("function")
        function.parameters.single().assertExpanded("value parameter")

        Example.Generic::class.java.typeParameters.single().assertExpanded("type parameter")
    }

    private fun AnnotatedElement.assertExpanded(label: String) {
        val first = assertNotNull(getAnnotation(First::class.java), "$label did not receive @First")
        val second = assertNotNull(getAnnotation(Second::class.java), "$label did not receive @Second")

        assertEquals("expanded", first.value, "$label received the wrong @First argument")
        assertEquals(7, second.number, "$label received the wrong @Second argument")
        assertNull(getAnnotation(Bundle::class.java), "$label retained @Bundle")
        assertNull(getAnnotation(NestedBundle::class.java), "$label retained @NestedBundle")
    }
}
