plugins {
    alias(libs.plugins.roozban.android.library)
    alias(libs.plugins.roozban.hilt)
}

android {
    namespace = "ir.roozban.ai.runtime"

    buildFeatures {
        aidl = true
    }

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
        ndk {
            // armeabi-v7a carries only a stub so 32-bit phones can still install the app.
            abiFilters += listOf("arm64-v8a", "x86_64", "armeabi-v7a")
        }
    }

    externalNativeBuild {
        cmake {
            path("src/main/cpp/CMakeLists.txt")
        }
    }
}

dependencies {
    api(projects.ai.core)
    api(projects.ai.models)
    implementation(projects.core.common)
    implementation(projects.core.domain)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
}
