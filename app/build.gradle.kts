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
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "COMMIT", "\"" + (System.getenv("GITHUB_SHA")?.take(7) ?: "local") + "\"")
    }
    buildFeatures {
        buildConfig = true
    }

    // Firma fija: así cada APK nuevo se instala encima del anterior sin desinstalar.
    signingConfigs {
        getByName("debug") {
            storeFile = file("leetuagenda.keystore")
            storePassword = "leetuagenda"
            keyAlias = "leetuagenda"
            keyPassword = "leetuagenda"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
