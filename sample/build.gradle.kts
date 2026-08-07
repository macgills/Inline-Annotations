import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":annotations"))
    implementation(project(":bundle-library"))
    testImplementation(kotlin("test-junit"))
    testImplementation("junit:junit:4.13.2")
}

val compilerPluginJar = project(":compiler-plugin").tasks.named<Jar>("jar")

tasks.withType<KotlinJvmCompile>().configureEach {
    dependsOn(compilerPluginJar)
    pluginClasspath.from(compilerPluginJar.flatMap { it.archiveFile })

    doFirst {
        val pluginFiles = pluginClasspath.files
        check(pluginFiles.any { it.name == "inline-annotations-compiler-plugin.jar" }) {
            "Inline annotations compiler plugin missing from $name pluginClasspath: $pluginFiles"
        }
    }
}
