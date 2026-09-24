pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
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

rootProject.name = "Roozban"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")
include(":core:common")
include(":core:calendar")
include(":core:recurrence")
include(":core:timeparser")
include(":core:model")
include(":core:domain")
include(":core:database")
include(":core:datastore")
include(":core:data")
include(":core:alarm")
include(":core:testing")
include(":core:designsystem")
include(":feature:tasks")
include(":feature:settings")
include(":core:ui")
include(":billing:api")
