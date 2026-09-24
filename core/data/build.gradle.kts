plugins {
    alias(libs.plugins.roozban.android.library)
    alias(libs.plugins.roozban.hilt)
}

android {
    namespace = "ir.roozban.core.data"
}

dependencies {
    api(projects.core.domain)
    implementation(projects.core.database)
    implementation(projects.core.datastore)
    implementation(projects.core.common)
    implementation(projects.core.backup)

    testImplementation(projects.core.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
}
