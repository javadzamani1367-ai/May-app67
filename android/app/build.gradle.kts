plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// The signing key never lives in the repository. When these are absent the
// release variant simply builds unsigned, which is what a local build does.
val releaseKeystore: String? = System.getenv("RELEASE_KEYSTORE_PATH")
val releaseKeystorePassword: String? = System.getenv("RELEASE_KEYSTORE_PASSWORD")
val releaseKeyAlias: String? = System.getenv("RELEASE_KEY_ALIAS")
val releaseKeyPassword: String? = System.getenv("RELEASE_KEY_PASSWORD")

android {
    namespace = "ir.ilam.inspection"
    compileSdk = 34

    defaultConfig {
        applicationId = "ir.ilam.inspection"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "1.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        resourceConfigurations += listOf("fa")

        // SQLCipher ships a native library per ABI. Field phones are ARM, so the
        // x86 variants are dead weight in the package the experts install.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    signingConfigs {
        if (!releaseKeystore.isNullOrBlank()) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                // v1 as well as v2/v3: Android 8 verifies the APK signature
                // block, but the jar signature is the fallback older tooling
                // still checks when sideloading.
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    // Two apps from one codebase. The role is decided by which APK was
    // installed, not by a setting a user could change: an expert must not be
    // able to give themselves the manager's screens by tapping a dropdown.
    flavorDimensions += "role"
    productFlavors {
        create("expert") {
            dimension = "role"
            applicationIdSuffix = ".expert"
            versionNameSuffix = "-expert"
            resValue("string", "app_name", "توان‌کاو")
            buildConfigField("boolean", "MANAGER", "false")
        }
        create("manager") {
            dimension = "role"
            applicationIdSuffix = ".manager"
            versionNameSuffix = "-manager"
            resValue("string", "app_name", "توان‌کاو مدیر")
            buildConfigField("boolean", "MANAGER", "true")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
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
        buildConfig = true
    }
    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.sqlite)
    implementation(libs.sqlcipher)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.video)

    implementation(libs.androidx.exifinterface)
    implementation(libs.zxing.android.embedded)
    implementation(libs.play.services.location)
    implementation(libs.nanohttpd)
    implementation(libs.osmdroid)
    implementation(libs.androidx.security.crypto)

    // Roughly 400 KB, against a 20 MB ceiling the release build sits 7 MB
    // under. It buys the one thing no amount of care in the app can: a sync
    // that happens when the expert is back on Wi-Fi and has forgotten to ask
    // for one.
    implementation(libs.androidx.work)

    testImplementation(libs.junit)

    // The wire format shared with the Windows archive and the server is built
    // on org.json, which android.jar only stubs. Test scope only, so it costs
    // the APK nothing.
    testImplementation(libs.json)
}
