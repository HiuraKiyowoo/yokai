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
            // Keystore debug sudah ada di tiap mesin build, jadi tidak perlu secret.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

dependencies {
    implementation(project(":source:api"))
}
