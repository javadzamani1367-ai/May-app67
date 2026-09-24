plugins {
    alias(libs.plugins.roozban.android.library)
    alias(libs.plugins.roozban.android.compose)
    alias(libs.plugins.roozban.hilt)
}

android {
    namespace = "ir.roozban.core.alarm"
}

dependencies {
    api(projects.core.domain)
    implementation(projects.core.calendar)
    implementation(projects.core.designsystem)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
}
