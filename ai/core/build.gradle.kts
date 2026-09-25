plugins {
    alias(libs.plugins.roozban.jvm.library)
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlinx.coroutines.test)
}
