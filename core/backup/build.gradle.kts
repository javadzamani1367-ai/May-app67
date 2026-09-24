plugins {
    alias(libs.plugins.roozban.jvm.library)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.bouncycastle)
}
