import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val lunarAppName = "Lunar"
val lunarBundleId = "com.ryanjames.lunar"
val lunarVendor = "Ryan James"
val lunarVersionName = providers.gradleProperty("lunar.versionName").getOrElse("0.1.0")
val lunarVersionCode = providers.gradleProperty("lunar.versionCode").map(String::toInt).getOrElse(1)
val lunarDesktopPackageVersion = providers.gradleProperty("lunar.desktopPackageVersion").getOrElse(lunarVersionName)
val lunarWindowsUpgradeUuid = providers.gradleProperty("lunar.windowsUpgradeUuid")
    .getOrElse("D1247941-B317-4D82-B11B-C7F06F513202")

group = lunarBundleId
version = lunarDesktopPackageVersion

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.kover)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    jvm()

    js {
        browser()
        binaries.executable()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.documentfile)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.okio)
            implementation(projects.shared)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.okio.fakefilesystem)
        }
        jvmTest.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(compose.desktop.uiTestJUnit4)
            implementation(libs.kotlin.testJunit)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
            implementation(libs.pdfbox)
        }
    }
}

android {
    namespace = "com.ryanjames.lunar"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = lunarBundleId
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = lunarVersionCode
        versionName = lunarVersionName
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    debugImplementation(libs.compose.uiTooling)
}

compose.desktop {
    application {
        mainClass = "com.ryanjames.lunar.MainKt"

        buildTypes.release.proguard {
            configurationFiles.from(project.file("compose-desktop.pro"))
        }

        nativeDistributions {
            // No TargetFormat.AppImage: it is the same jpackage app image that
            // createReleaseDistributable already writes to binaries/<build>/app, and the two
            // tasks sharing that output breaks packageReleaseDmg's task validation on macOS.
            targetFormats(
                TargetFormat.Deb,
                TargetFormat.Dmg,
                TargetFormat.Exe,
                TargetFormat.Msi,
            )
            packageName = lunarAppName
            packageVersion = lunarDesktopPackageVersion
            description = "Cross-platform sheet music library and PDF viewer."
            vendor = lunarVendor
            copyright = "Copyright 2026 $lunarVendor"

            windows {
                iconFile.set(project.file("desktop-icons/windows/lunar.ico"))
                menuGroup = lunarAppName
                dirChooser = true
                perUserInstall = true
                msiPackageVersion = lunarDesktopPackageVersion
                upgradeUuid = lunarWindowsUpgradeUuid
            }

            linux {
                iconFile.set(project.file("desktop-icons/linux/lunar.png"))
                packageName = "lunar"
                menuGroup = lunarAppName
                debPackageVersion = lunarDesktopPackageVersion
            }

            macOS {
                packageName = lunarAppName
                dockName = lunarAppName
                bundleID = lunarBundleId
                packageVersion = lunarDesktopPackageVersion
            }
        }
    }
}

// The AppImage task writes the same app-image directory (binaries/main[-release]/app) that the
// installer tasks (Dmg, Deb, Exe, Msi) read, so order them explicitly. Without this Gradle fails
// packageReleaseDistributionForCurrentOS with an implicit-dependency validation error.
tasks.withType<AbstractJPackageTask>().configureEach {
    val appImageTaskName = Regex("^package(.*?)(AppImage|Dmg|Pkg|Deb|Rpm|Exe|Msi)$")
        .matchEntire(name)
        ?.takeIf { it.groupValues[2] != "AppImage" }
        ?.let { "package${it.groupValues[1]}AppImage" }
        ?: return@configureEach
    mustRunAfter(appImageTaskName)
}
