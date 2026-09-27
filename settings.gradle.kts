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

rootProject.name = "tuk-plus"

include(":app")
include(":core:model")
include(":core:domain")
include(":core:network")
include(":core:logging")
include(":core:data")
include(":core:pricing")
include(":tools:api-probe")
