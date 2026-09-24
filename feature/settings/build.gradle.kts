plugins {
    alias(libs.plugins.roozban.android.feature)
}

android {
    namespace = "ir.roozban.feature.settings"
}

dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.ui)
    implementation(projects.core.alarm)
    implementation(libs.androidx.activity.compose)
    testImplementation(projects.core.testing)
}
