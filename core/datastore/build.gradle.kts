plugins {
    alias(libs.plugins.roozban.android.library)
}

android {
    namespace = "ir.roozban.core.datastore"
}

dependencies {
    api(projects.core.domain)
    api(libs.androidx.datastore.preferences)
    testImplementation(libs.kotlinx.coroutines.test)
}
