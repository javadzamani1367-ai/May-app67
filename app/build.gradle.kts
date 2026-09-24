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
    implementation(projects.feature.today)
    implementation(projects.billing.api)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
}
