pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "inline-annotations"

include(":annotations")
include(":bundle-library")
include(":compiler-plugin")
include(":metro-poc")
include(":sample")
