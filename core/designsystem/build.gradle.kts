plugins {
    alias(libs.plugins.roozban.android.library)
    alias(libs.plugins.roozban.android.compose)
}

android {
    namespace = "ir.roozban.core.designsystem"
}

dependencies {
    api(projects.core.model)
}
