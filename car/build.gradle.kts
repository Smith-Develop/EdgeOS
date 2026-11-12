import java.util.Properties

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
        minSdk = 23
        targetSdk = 36
        versionCode = 10
        versionName = "0.10"
        androidResources.localeFilters += arrayOf("es")
    }

    // Firma de las versiones publicadas: la clave y sus contraseñas están en keystore.properties, que nunca se sube.
    // Sin ese archivo, la versión release sale sin firmar.
    val keys = rootProject.file("keystore.properties").takeIf { it.exists() }?.let { f ->
        Properties().apply { f.inputStream().use { load(it) } }
    }
    signingConfigs {
        if (keys != null) create("release") {
            storeFile = file(keys.getProperty("storeFile"))
            storePassword = keys.getProperty("storePassword")
            keyAlias = keys.getProperty("keyAlias")
            keyPassword = keys.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            // Sin R8: osmdroid y Mapsforge usan reflexión, y la versión publicada debe ser la misma que se prueba.
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    implementation(libs.osmdroid)
    // Mapas vectoriales sin conexión (archivos .map de Mapsforge), dibujados en la radio.
    implementation(libs.osmdroid.mapsforge)
    testImplementation("junit:junit:4.13.2")
}
