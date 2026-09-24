plugins {
    alias(libs.plugins.roozban.jvm.library)
}

dependencies {
    api(libs.javax.inject)
    api(libs.kotlinx.coroutines.core)
}
