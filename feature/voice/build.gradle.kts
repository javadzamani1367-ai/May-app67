plugins {
    alias(libs.plugins.roozban.android.feature)
}

android {
    namespace = "ir.roozban.feature.voice"
}

dependencies {
    implementation(projects.core.domain)
    implementation(projects.ai.runtime)
    implementation(libs.androidx.activity.compose)
}
