pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "resolutor"

include(
    "resolutor-domain",
    "resolutor-application",
    "resolutor-adapter-persistence-jpa",
    "resolutor-adapter-web",
    "resolutor-adapter-resource-http",
    "resolutor-adapter-kafka",
    "resolutor-adapter-metrics",
    "resolutor-app",
)
