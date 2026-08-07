package dev.inlineannotations.compiler

import java.util.ServiceLoader
import kotlin.test.Test
import kotlin.test.assertEquals
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar

public class CompilerPluginDiscoveryTest {
    @Test
    public fun serviceLoaderDiscoversRegistrar() {
        val registrars = ServiceLoader
            .load(CompilerPluginRegistrar::class.java)
            .filterIsInstance<InlineAnnotationsCompilerPluginRegistrar>()
            .toList()

        assertEquals(1, registrars.size)
        assertEquals("dev.inlineannotations", registrars.single().pluginId)
        assertEquals(true, registrars.single().supportsK2)
    }
}
