plugins {
    alias(libs.plugins.roozban.jvm.library)
}

dependencies {
    // Independent reference implementation used only to cross-check the conversion in tests.
    testImplementation(libs.icu4j)
}
