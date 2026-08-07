package dev.inlineannotations.sample

import java.lang.reflect.AnnotatedElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

public class InlineAnnotationsTest {
    @Test
    public fun classTargetExpands() {
        Example::class.java.assertExpanded()
    }

    @Test
    public fun constructorTargetExpands() {
        Example::class.java.declaredConstructors.single().assertExpanded()
    }

    @Test
    public fun fieldTargetExpands() {
        Example::class.java.getDeclaredField("value").assertExpanded()
    }

    @Test
    public fun getterTargetExpands() {
        Example::class.java.getDeclaredMethod("getValue").assertExpanded()
    }

    @Test
    public fun functionTargetExpands() {
        Example::class.java.getDeclaredMethod("run", String::class.java).assertExpanded()
    }

    @Test
    public fun valueParameterTargetExpands() {
        Example::class.java
            .getDeclaredMethod("run", String::class.java)
            .parameters
            .single()
            .assertExpanded()
    }

    @Test
    public fun typeParameterTargetExpands() {
        Example.Generic::class.java.typeParameters.single().assertExpanded()
    }

    @Test
    public fun repeatableAnnotationsAccumulateAcrossDirectAndBundledUses() {
        val method = fixturesMethod("repeatableAnnotationsAccumulate")

        assertEquals(
            listOf("direct", "bundle"),
            method.getAnnotationsByType(Tag::class.java).map(Tag::value),
        )
        assertNull(method.getAnnotation(TagBundle::class.java))
    }

    private fun fixturesMethod(name: String) =
        Class.forName("dev.inlineannotations.sample.FixturesKt").getDeclaredMethod(name)

    private fun AnnotatedElement.assertExpanded() {
        val first = assertNotNull(getAnnotation(First::class.java), "did not receive @First")
        val second = assertNotNull(getAnnotation(Second::class.java), "did not receive @Second")

        assertEquals("expanded", first.value, "received the wrong @First argument")
        assertEquals(7, second.number, "received the wrong @Second argument")
        assertNull(getAnnotation(Bundle::class.java), "retained @Bundle")
        assertNull(getAnnotation(NestedBundle::class.java), "retained @NestedBundle")
    }
}
