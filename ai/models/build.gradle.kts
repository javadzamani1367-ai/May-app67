plugins {
    alias(libs.plugins.roozban.jvm.library)
}

dependencies {
    api(projects.ai.core)
    testImplementation(libs.kotlinx.coroutines.test)
}
