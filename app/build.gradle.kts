plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Versión de la app: cambiar SOLO aquí. El APK se publica como LeeTuAgenda-v<versión>.apk
val appVersion = "1.2.0"

android {
    namespace = "com.leetuagenda"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.leetuagenda"
        minSdk = 26
        targetSdk = 35
        versionName = appVersion
        // 1.2.0 -> 10200 (siempre crece al subir la versión)
        versionCode = appVersion.split(".").map { it.toInt() }.let { (a, b, c) -> a * 10000 + b * 100 + c }
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
