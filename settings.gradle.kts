pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // No BOOX repo on purpose. The only Onyx API this app uses is EpdController, to force a
        // full e-ink refresh, and it is reached by reflection (ui/eink/EpdRefresher.kt). Their
        // Maven is plain HTTP with no HTTPS mirror, and a CI build should not depend on it for
        // one optional call that is absent on every non-BOOX device anyway.
    }
}
rootProject.name = "things-android"
include(":app")
