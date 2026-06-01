plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinSerialization)
}

android {
    namespace = "com.ryanjames.lunar.quest"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.ryanjames.lunar.quest"
        minSdk = 34
        // Horizon OS is Android 14 for current Meta Spatial SDK samples.
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1,LICENSE}"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.meta.spatial.sdk)
    implementation(libs.meta.spatial.sdk.toolkit)
    implementation(libs.meta.spatial.sdk.vr)
}
