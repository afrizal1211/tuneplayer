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
        exclusiveContent {
            forRepository {
                maven("https://jitpack.io")
            }
            filter {
                includeGroup("com.github.MetrolistGroup.innertubex")
                includeGroup("com.github.MetrolistGroup.quackjs")
            }
        }
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

rootProject.name = "TunePlayer"
include(":app")
