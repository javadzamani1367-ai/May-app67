plugins {
    alias(libs.plugins.roozban.jvm.library)
}

dependencies {
    api(projects.core.model)
    api(projects.core.timeparser)
    api(projects.core.recurrence)
    api(libs.kotlinx.coroutines.core)
    api(libs.javax.inject)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(projects.core.testing)
}
