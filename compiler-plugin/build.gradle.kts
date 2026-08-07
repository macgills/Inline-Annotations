plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.4.10")
}

tasks.jar {
    archiveFileName.set("inline-annotations-compiler-plugin.jar")
}
