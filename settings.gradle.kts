pluginManagement {
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
rootProject.name = "SubjectToolbox"

include(":app")
include(":core:model")
include(":core:registry")
include(":core:designsystem")
include(":core:data")
include(":feature:home")
include(":feature:settings")
include(":feature:chinese-poetry")
include(":feature:math-function")
include(":feature:english-dictionary")
include(":feature:chemistry-equation")
include(":feature:biology-diagram")
include(":feature:geography-diagram")
include(":feature:electronic-textbook")
include(":feature:formula")
