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
    testImplementation(kotlin("test-junit"))
    testImplementation("junit:junit:4.13.2")
}

val compilerPluginJar = project(":compiler-plugin").tasks.named<Jar>("jar")

tasks.withType<KotlinJvmCompile>().configureEach {
    dependsOn(compilerPluginJar)
    compilerOptions.freeCompilerArgs.add(
        compilerPluginJar.flatMap { it.archiveFile }
            .map { "-Xplugin=${it.asFile.absolutePath}" },
    )
}
