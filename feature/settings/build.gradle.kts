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
    testImplementation(projects.core.testing)
}
