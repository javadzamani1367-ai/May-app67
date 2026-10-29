import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Everything that does not need Android: the detection engine, the
// SensorSource contract, FFT, harmonic detection, Goertzel, direction finding
// and triangulation. Kept free of Android so the unit tests run on a plain JVM.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
}
