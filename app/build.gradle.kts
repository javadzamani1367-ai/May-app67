plugins {
    alias(libs.plugins.roozban.android.application)
    alias(libs.plugins.roozban.android.compose)
    alias(libs.plugins.roozban.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "ir.roozban.app"

    defaultConfig {
        applicationId = "ir.roozban.app"
        versionCode = 1
        versionName = "0.1.0"
    }

    // One flavor per store; each provides its own BillingGateway (see src/<flavor>/).
    flavorDimensions += "store"
    productFlavors {
        create("bazaar") { dimension = "store" }
        create("myket") { dimension = "store" }
        create("dev") {
            dimension = "store"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
        }
    }

    // A committed debug key keeps test builds from CI installable over each other.
    // It is public and only for debug builds; release signing is configured in phase 8.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    // ggml loads its CPU backend variants from the native library directory at runtime,
    // so the libraries must be extracted on install.
    packaging {
        jniLibs.useLegacyPackaging = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.calendar)
    implementation(projects.core.timeparser)
    implementation(projects.core.designsystem)
    implementation(projects.core.domain)
    implementation(projects.core.data)
    implementation(projects.core.alarm)
    implementation(projects.feature.tasks)
    implementation(projects.feature.settings)
    implementation(projects.feature.focus)
    implementation(projects.feature.habits)
    implementation(projects.feature.reports)
    implementation(projects.feature.assistant)
    implementation(projects.ai.runtime)
    implementation(projects.billing.api)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
}
