plugins {
    alias(libs.plugins.roozban.android.feature)
}

android {
    namespace = "ir.roozban.feature.assistant"
}

dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.calendar)
    implementation(projects.ai.tools)
    implementation(projects.ai.runtime)
    implementation(projects.ai.tts)
    implementation(projects.feature.voice)
    implementation(libs.androidx.activity.compose)
    testImplementation(projects.core.testing)
}
