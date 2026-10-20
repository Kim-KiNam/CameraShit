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
        // libadb-android, sun-security-android 는 JitPack 에서만 제공된다.
        maven {
            url = uri("https://jitpack.io")
            content { includeGroupByRegex("com\\.github\\.MuntashirAkon.*") }
        }
    }
}

rootProject.name = "CameraShit"
include(":app")
