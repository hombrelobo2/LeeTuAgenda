plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.leetuagenda"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.leetuagenda"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "1.2"
        buildConfigField("String", "COMMIT", "\"" + (System.getenv("GITHUB_SHA")?.take(7) ?: "local") + "\"")
    }
    buildFeatures {
        buildConfig = true
    }

    // Firma de publicación: la clave NO está en el repositorio.
    // Se lee de variables de entorno (en GitHub vienen de los "secrets").
    // Sin ellas (compilación local o F-Droid) el APK release sale sin firmar.
    val keystorePath = System.getenv("SIGNING_KEYSTORE_FILE")
    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystorePath != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

// Sin dependencias externas: solo APIs del sistema Android.
dependencies {
}
