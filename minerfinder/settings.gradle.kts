pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "minerfinder"
// :core is plain Kotlin (no Android): the engine, the SensorSource contract and
// all signal processing, so its tests run on any JVM. :app is the Android shell.
include(":core")
include(":app")
