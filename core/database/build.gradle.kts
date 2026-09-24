plugins {
    alias(libs.plugins.roozban.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

android {
    namespace = "ir.roozban.core.database"
}

room {
    // Exported schemas are committed and used for migration tests.
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    api(projects.core.model)
    api(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
}
