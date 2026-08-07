package dev.inlineannotations.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter

@OptIn(ExperimentalCompilerApi::class)
public class InlineAnnotationsCompilerPluginRegistrar : CompilerPluginRegistrar() {
    override val pluginId: String = "dev.inlineannotations"

    override val supportsK2: Boolean = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        FirExtensionRegistrarAdapter.registerExtension(InlineAnnotationsFirExtensionRegistrar())
        IrGenerationExtension.registerExtension(InlineAnnotationsIrGenerationExtension())
    }
}
