plugins {
    id("yokai.android.application")
    alias(kotlinx.plugins.serialization)
}

android {
    namespace = "eu.kanade.tachiyomi.extension.id.wibutime"

    defaultConfig {
        applicationId = "eu.kanade.tachiyomi.extension.id.wibutime"
        versionCode = 1
        versionName = "1.5.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // APK ekstensi harus ditandatangani supaya bisa di-trust aplikasi.
            // Keystore debug selalu ada di mesin build, jadi tak perlu secret.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

dependencies {
    // Antarmuka Source/SourceFactory + model (SManga/SChapter/Page)
    implementation(project(":source:api"))
    // Pembantu HTTP: GET(), dan okhttp yang di :source:api bersifat implementation
    implementation(project(":core:main"))
    implementation(libs.okhttp)
}
