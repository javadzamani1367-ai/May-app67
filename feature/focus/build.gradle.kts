plugins {
    alias(libs.plugins.roozban.android.feature)
}

android {
    namespace = "ir.roozban.feature.focus"
}

dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.ui)
    testImplementation(projects.core.testing)
}
