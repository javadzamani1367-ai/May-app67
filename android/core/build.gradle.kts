plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// What every TavanKav app shares: the design system and its components,
// Persian dates and numbers, precise location and the map picker, the camera
// and barcode capture, the server client, and file and crypto helpers.
// Moved here rather than copied, so a fix in one place reaches every app.
android {
    namespace = "ir.ilam.inspection.core"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    api(libs.androidx.core.ktx)
    api(libs.androidx.lifecycle.runtime.ktx)
    api(libs.androidx.lifecycle.viewmodel.compose)
    api(libs.androidx.lifecycle.runtime.compose)
    api(libs.androidx.activity.compose)

    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.ui.graphics)
    api(libs.androidx.compose.ui.tooling.preview)
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.material.icons)

    api(libs.androidx.camera.core)
    api(libs.androidx.camera.camera2)
    api(libs.androidx.camera.lifecycle)
    api(libs.androidx.camera.view)
    api(libs.androidx.camera.video)

    api(libs.androidx.exifinterface)
    api(libs.zxing.android.embedded)
    api(libs.play.services.location)
    api(libs.osmdroid)
    // Draws the offline Ilam map. Built on mapsforge 0.21.0, so the map file
    // must be written with that version (.github/workflows/map.yml).
    api(libs.osmdroid.mapsforge)
    api(libs.androidx.security.crypto)

    // Every app's database is SQLCipher behind Room (SecureDatabase).
    api(libs.androidx.room.runtime)
    api(libs.androidx.sqlite)
    api(libs.sqlcipher)

    testImplementation(libs.junit)
    testImplementation(libs.json)
}
