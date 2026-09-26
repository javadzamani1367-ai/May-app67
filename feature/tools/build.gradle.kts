plugins {
    alias(libs.plugins.roozban.android.feature)
}

android {
    namespace = "ir.roozban.feature.tools"
}

dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.calendar)
    implementation(projects.ai.runtime)
    implementation(projects.ai.tts)
    implementation(libs.androidx.activity.compose)
    testImplementation(projects.core.testing)
}
