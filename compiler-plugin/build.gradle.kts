plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.4.10")
    compileOnly("org.jetbrains.kotlinx:kotlinx-collections-immutable-jvm:0.3.7")

    testImplementation(kotlin("test-junit"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.4.10")
}

tasks.jar {
    archiveFileName.set("inline-annotations-compiler-plugin.jar")
}
