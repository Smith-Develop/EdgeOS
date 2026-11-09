plugins {
    alias(libs.plugins.android.application)
}

// EdgeOS Car: launcher para radios Android de coche (pensado para la k80_bsp: MT6580, Android 8.1, 1024×600).
// Vistas clásicas, sin Compose: en un Mali-400 cada milisegundo cuenta.
android {
    namespace = "io.edgeos.car"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.edgeos.car"
        minSdk = 26
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
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    implementation(libs.osmdroid)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
}
