plugins {
    alias(libs.plugins.roozban.android.feature)
}

android {
    namespace = "ir.roozban.feature.tools"
}

dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.ui)
    implementation(projects.core.calendar)
    implementation(projects.ai.runtime)
    implementation(projects.ai.tts)
    implementation(libs.androidx.activity.compose)
    implementation(projects.ai.models)
    implementation(projects.core.documents)
    implementation(libs.tesseract4android)
    implementation(libs.pdfbox.android)
    implementation(libs.androidx.exifinterface)
    testImplementation(projects.core.testing)
}
