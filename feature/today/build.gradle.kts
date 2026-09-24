plugins {
    alias(libs.plugins.roozban.android.feature)
}

android {
    namespace = "ir.roozban.feature.today"
}

dependencies {
    implementation(projects.core.calendar)
    implementation(projects.core.timeparser)
}
