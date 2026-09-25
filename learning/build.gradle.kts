plugins {
    alias(libs.plugins.roozban.jvm.library)
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.calendar)
}
