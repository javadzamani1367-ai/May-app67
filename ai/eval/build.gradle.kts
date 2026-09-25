plugins {
    alias(libs.plugins.roozban.jvm.library)
    application
}

application {
    mainClass.set("ir.roozban.ai.eval.EvalKt")
}

dependencies {
    implementation(projects.ai.tools)
    implementation(projects.core.testing)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}
