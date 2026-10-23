plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.vidrio.launcher"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.vidrio.launcher"
        minSdk = 33 // Android 13+: el vidrio (RuntimeShader) necesita API 33.
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        androidResources.localeFilters += arrayOf("es")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    buildFeatures {
        compose = true
    }
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.kyant.backdrop)
    implementation(libs.kyant.shapes)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
}
