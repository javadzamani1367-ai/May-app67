plugins {
    alias(libs.plugins.roozban.android.feature)
}

android {
    namespace = "ir.roozban.feature.tasks"
}

dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.ui)
    implementation(projects.core.alarm)
    implementation(projects.feature.voice)
    implementation(libs.androidx.activity.compose)
    testImplementation(projects.core.testing)
}
