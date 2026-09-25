plugins {
    alias(libs.plugins.roozban.jvm.library)
}

dependencies {
    api(projects.ai.core)
    api(projects.core.domain)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(projects.core.testing)
}
