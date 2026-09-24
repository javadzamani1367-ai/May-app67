plugins {
    alias(libs.plugins.roozban.android.library)
    alias(libs.plugins.roozban.android.compose)
}

android {
    namespace = "ir.roozban.core.ui"
}

dependencies {
    api(projects.core.designsystem)
    api(projects.core.calendar)
}
