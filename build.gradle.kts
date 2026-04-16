plugins {
    alias(libs.plugins.kover)
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.composeHotReload) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.ktor) apply false
}

dependencies {
    kover(projects.composeApp)
    kover(projects.server)
    kover(projects.shared)
}

kover {
    reports {
        total {
            verify {
                rule {
                    minBound(35)
                }
            }
        }
    }
}
