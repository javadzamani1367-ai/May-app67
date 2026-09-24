plugins {
    alias(libs.plugins.roozban.jvm.library)
}

dependencies {
    api(projects.core.calendar)
    api(projects.core.recurrence)
}
