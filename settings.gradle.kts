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
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "ReelCounter"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")
include(":core:common")
include(":core:data")
include(":core:database")
include(":core:datastore")
include(":core:designsystem")
include(":core:domain")
include(":core:model")
include(":core:network")
include(":core:preview")
include(":core:testing")
include(":core:tracking")
include(":core:ui")
include(":core:work")
include(":feature:auth")
include(":feature:friends")
include(":feature:insights")
include(":feature:privacy")
include(":feature:profile")
include(":feature:settings")
include(":feature:today")
include(":feature:tracking")

check(JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_17)) {
    """
    ReelCounter requires JDK 17+ but Gradle is running on JDK ${JavaVersion.current()}.
    Java home: [${System.getProperty("java.home")}]
    In Android Studio: Settings > Build, Execution, Deployment > Build Tools > Gradle > Gradle JDK.
    """.trimIndent()
}
