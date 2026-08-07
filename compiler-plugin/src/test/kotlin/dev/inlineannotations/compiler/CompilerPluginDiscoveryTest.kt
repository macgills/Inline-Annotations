package dev.inlineannotations.compiler

import java.util.ServiceLoader
import kotlin.test.Test
import kotlin.test.assertEquals
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

@OptIn(ExperimentalCompilerApi::class)
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
