import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    kotlin("jvm")
    id("dev.zacsweers.metro")
}

kotlin {
    jvmToolchain(21)
}

metro {
    // Deliberate project policy for the proof: construction is always explicit with @Inject.
    // AppScopedBinding is responsible only for contribution + lifetime semantics.
    contributesAsInject.set(false)
}

dependencies {
    implementation(project(":metro-recipes"))

    testImplementation(kotlin("test-junit"))
    testImplementation("junit:junit:4.13.2")
}

evaluationDependsOn(":compiler-plugin")
val inlineAnnotationsCompilerPluginJar = project(":compiler-plugin").tasks.named<Jar>("jar")

tasks.withType<KotlinJvmCompile>().configureEach {
    dependsOn(inlineAnnotationsCompilerPluginJar)
    pluginClasspath.from(inlineAnnotationsCompilerPluginJar.flatMap { it.archiveFile })
    compilerOptions.freeCompilerArgs.add(
        // A language implementation would expand before third-party plugins by definition.
        "-Xcompiler-plugin-order=dev.inlineannotations>dev.zacsweers.metro",
    )

    doFirst {
        val pluginFiles = pluginClasspath.files
        check(pluginFiles.any { it.name == "inline-annotations-compiler-plugin.jar" }) {
            "Inline annotations compiler plugin missing from $name pluginClasspath: $pluginFiles"
        }
    }
}
